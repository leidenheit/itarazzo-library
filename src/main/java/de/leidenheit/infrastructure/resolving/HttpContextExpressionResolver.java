package de.leidenheit.infrastructure.resolving;

import com.jayway.jsonpath.JsonPath;
import de.leidenheit.core.exception.ItarazzoIllegalStateException;
import de.leidenheit.core.exception.ItarazzoUnsupportedException;
import io.restassured.http.ContentType;
import io.restassured.http.Headers;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.RequestSpecification;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

// TODO refactor
public class HttpContextExpressionResolver implements HttpExpressionResolver {

    @Override
    public Object resolveExpression(final String expression, final ResolverContext context) {
        if (Objects.isNull(context)) return expression;
        if (!(HttpResolverContext.class.isAssignableFrom(context.getClass())))
            throw new ItarazzoIllegalStateException("Expected context to be a descendant of HttpResolverContext but was not");

        var httpContext = (HttpResolverContext) context;
        if (expression.equals("$statusCode")) {
            return String.valueOf(httpContext.getLatestStatusCode());
        } else if (expression.startsWith("$response.")) {
            if (expression.startsWith("$response.header")) {
                var header = expression.substring("$response.header.".length());
                return resolveHeader(header, httpContext.getLastestResponse().getHeaders());
            } else if (expression.startsWith("$response.body")) {
                var responseBody = resolveResponseBodyPayload(httpContext.getLastestResponse());
                if (responseBody.isBlank()) {
                    return null;
                }
                String subPath = null;
                if (expression.contains("$response.body#/")) {
                    subPath = expression.substring("$response.body#/".length());
                    subPath = convertJsonPointerToJsonPath(subPath);
                } else if (expression.contains("$response.body.")) {
                    subPath = expression.substring("$response.body.".length());
                } else if (expression.contains("$response.body[")) {
                    subPath = expression.substring("$response.body".length());
                }

                if (Objects.isNull(subPath)) return responseBody;

                try {
                    if (ContentType.JSON.matches(httpContext.getLatestContentType())) {
                        return resolveJsonPath(responseBody, subPath);
                    } else if (ContentType.XML.matches(httpContext.getLatestContentType())) {
                        Document document = DocumentBuilderFactory.newInstance()
                                .newDocumentBuilder()
                                .parse(new ByteArrayInputStream(responseBody.getBytes(StandardCharsets.UTF_8)));

                        XPath xpath = XPathFactory.newInstance().newXPath();
                        var xmlSubPath = subPath.replace(".", "/");
                        var res = xpath
                                .compile("/%s".formatted(xmlSubPath))
                                .evaluate(document, XPathConstants.NODE);

                        if (Objects.nonNull(res) && res instanceof Node resNode) {
                            return resNode.getTextContent();
                        }
                        throw new ItarazzoIllegalStateException("Tried to resolved xpath %s but got null".formatted(xmlSubPath));
                    }
                    throw new ItarazzoUnsupportedException("Reading nested properties of response body requires a content type of JSON|XML");

                } catch (Exception e) {
                    throw new ItarazzoIllegalStateException("Invalid JSON path or XPath: '%s'".formatted(expression), e);
                }
            }
            throw new ItarazzoUnsupportedException("Not supported: expression=%s".formatted(expression));
        } else if (expression.startsWith("$request.")) {
            if (expression.startsWith("$request.header")) {
                var header = expression.substring("$request.header.".length());
                return resolveHeader(header, httpContext.getLatestRequest().getHeaders());
            } else if (expression.startsWith("$request.body")) {
                var requestBody = resolveRequestBodyPayload(httpContext.getLatestRequest());
                if (requestBody.isBlank()) {
                    return null;
                }
                // TODO consider handle request bodies in a deeply manner
                return requestBody;
            } else if (expression.startsWith("$request.path")) {
                var pathParam = expression.substring("$request.path.".length());
                return resolvePathParam(pathParam, httpContext.getLatestRequest().getPathParams());
            }
            throw new ItarazzoUnsupportedException("Not supported: expression=%s".formatted(expression));
        } else if (expression.startsWith("$url")) {
            return httpContext.getLatestUrl();
        } else if (expression.startsWith("$method")) {
            return httpContext.getLatestHttpMethod();
        } else if (expression.startsWith("$message")) {
            return httpContext.getLatestMessage();
        }

        return expression; // Return unchanged if no resolution is found
    }

    @Override
    public String resolveHeader(final String headerName, final Headers headers) {
        return headers.getValue(headerName);
    }

    @Override
    public String resolvePathParam(final String paramName, final Map<String, String> pathParams) {
        return pathParams.get(paramName);
    }

    @Override
    public String resolveRequestBodyPayload(final RequestSpecification requestSpecification) {
        return ((FilterableRequestSpecification) requestSpecification).getBody();
    }

    @Override
    public String resolveResponseBodyPayload(final Response response) {
        return (Objects.nonNull(response.body())) ? response.body().asString() : null;
    }

    private Object resolveJsonPath(final String responseBody, final String subPath) {
        try {
            // JSONPath requires array indices to be enclosed in brackets, e.g., body[0].items[10].id
            String correctedPath = correctJsonPathSyntax(subPath);
            return JsonPath.read(responseBody, "$." + correctedPath);
        } catch (Exception e) {
            throw new ItarazzoIllegalStateException("Invalid JSON Path: '$.%s'".formatted(subPath), e);
        }
    }

    private String convertJsonPointerToJsonPath(final String pointerPath) {
        // Convert JSON Pointer to JSONPath by replacing "/" with "." except the leading "/"
        // and handling array indexes appropriately.
        String[] segments = pointerPath.split("/");
        StringBuilder jsonPathBuilder = new StringBuilder();

        for (String segment : segments) {
            if (segment.isEmpty()) {
                // Ignore leading segment if it is empty (happens when splitting at leading '/')
                continue;
            }

            if (!jsonPathBuilder.isEmpty()) {
                jsonPathBuilder.append(".");
            }

            // If the segment is numeric, it represents an array index
            if (segment.matches("\\d+")) {
                jsonPathBuilder.append("[").append(segment).append("]");
            } else {
                jsonPathBuilder.append(segment);
            }
        }

        return jsonPathBuilder.toString();
    }

    private String correctJsonPathSyntax(final String subPath) {
        // Regular expression to replace all numeric parts with the correct array syntax
        // Matches any dot followed by one or more digits and replaces it with brackets
        return subPath.replaceAll("\\.(\\d+)", "[$1]");
    }
}
