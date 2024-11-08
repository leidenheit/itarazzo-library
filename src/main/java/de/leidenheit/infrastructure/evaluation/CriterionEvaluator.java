package de.leidenheit.infrastructure.evaluation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import de.leidenheit.core.exception.ItarazzoIllegalArgumentException;
import de.leidenheit.core.exception.ItarazzoIllegalStateException;
import de.leidenheit.core.exception.ItarazzoUnsupportedException;
import de.leidenheit.core.model.Criterion;
import de.leidenheit.infrastructure.resolving.SpecExpressionResolver;
import de.leidenheit.infrastructure.resolving.ResolverContext;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CriterionEvaluator {

    private final SpecExpressionResolver resolver;
    private final ObjectMapper mapper;

    public CriterionEvaluator(final SpecExpressionResolver resolver, final ObjectMapper mapper) {
        this.resolver = resolver;
        this.mapper = mapper;
    }

    public boolean evalCriterion(final Criterion criterion, final ResolverContext resolverContext) {
        if (criterion.getType() != null) {
            return switch (criterion.getType()) {
                case REGEX -> evaluateRegex(criterion, resolverContext);
                case JSONPATH -> evaluateJsonPath(criterion, resolverContext);
                case XPATH -> evaluateXPath(criterion, resolverContext);
                case SIMPLE -> evaluateSimpleCondition(criterion, resolverContext);
            };
        } else {
            return evaluateSimpleCondition(criterion, resolverContext);
        }
    }

    private String resolveCriterionContext(final String criterionContext, final ResolverContext resolverContext) {
        var resolved = resolver.resolveExpression(criterionContext, resolverContext);
        if (resolved instanceof String resolvedAsString) {
            return resolvedAsString;
        }
        throw new ItarazzoIllegalStateException(
                "Expected resolved context '%s' to be a string but was not".formatted(criterionContext));
    }

    private boolean evaluateSimpleCondition(final Criterion criterion, final ResolverContext resolverContext) {
        // e.g. $statusCode == 200
        return evaluateLogicalExpression(criterion.getCondition(), resolverContext);
    }

    private boolean evaluateRegex(final Criterion criterion, final ResolverContext resolverContext) {
        String contextValue = resolveCriterionContext(criterion.getContext(), resolverContext);
        if (Objects.isNull(contextValue)) return false;
        // e.g. $response.body.fieldHugo -> ^FieldHugoValue$
        return contextValue.matches(criterion.getCondition());
    }

    private boolean evaluateJsonPath(final Criterion criterion, final ResolverContext resolverContext) {
        // resolve the context value (e.g., response body)
        String contextValue = resolveCriterionContext(criterion.getContext(), resolverContext);
        if (Objects.isNull(contextValue)) return false;

        // parse the contextValue into a JSON Node
        JsonNode jsonNode;
        try {
            // TODO refactor: redundant code in CriterionValidator.validateJsonPath()
            jsonNode = mapper.readTree(contextValue);

            // check if the criterion uses a JSON Pointer (starts with #/)
            if (criterion.getCondition().startsWith("#/")) {
                // extract JSON pointer, operator and expected value
                Pattern pattern = Pattern.compile("#(?<ptr>/[^ ]+)\\s*(?<operator>==|!=|<=|>=|<|>)\\s*(?<expected>.+)");
                Matcher matcher = pattern.matcher(criterion.getCondition());

                if (!matcher.find()) throw new ItarazzoIllegalStateException(
                        "Pattern matching failed: input='%s' pattern='%s'".formatted(criterion.getCondition(), pattern));

                String ptr = matcher.group("ptr");
                String operator = matcher.group("operator");
                String expected = matcher.group("expected");

                // use JSON Pointer to resolve the node
                JsonNode nodeAtPointer = jsonNode.at(ptr);
                if (Objects.isNull(nodeAtPointer)) throw new ItarazzoIllegalStateException("Must not be null");

                // resolve expected if it is an expression
                expected = resolver.resolveString(expected);

                // evaluate condition based on the extracted node (simple condition)
                var resolvedCriterion = Criterion.builder()
                        .type(Criterion.CriterionType.SIMPLE)
                        .condition(String.format("%s %s %s", nodeAtPointer.asText(), operator, expected))
                        .context(criterion.getContext())
                        .build();
                return evaluateSimpleCondition(resolvedCriterion, resolverContext);
            } else {
                // extract query, operator and expected value
                Pattern pattern = Pattern.compile("(?<query>[$][^ ]+)\\s*(?<operator>==|!=|<=|>=|<|>)\\s*(?<expected>.+)");
                Matcher matcher = pattern.matcher(criterion.getCondition());

                if (!matcher.find()) throw new ItarazzoIllegalStateException(
                        "Pattern matching failed: input='%s' pattern='%s'".formatted(criterion.getCondition(), pattern));
                String query = matcher.group("query");
                String operator = matcher.group("operator");
                String expected = matcher.group("expected");

                // resolve expected if it is an expression
                expected = resolver.resolveString(expected);

                var jsonNodeAsString = mapper.writeValueAsString(jsonNode);
                var jsonNodeValue = JsonPath.parse(jsonNodeAsString).read(query);
                if (Objects.isNull(jsonNodeValue)) throw new ItarazzoIllegalStateException("Must not be null");
                // Evaluate condition based on the extracted node (simple condition)
                var resolvedCriterion = Criterion.builder()
                        .type(Criterion.CriterionType.SIMPLE)
                        .condition(String.format("%s %s %s", jsonNodeValue, operator, expected))
                        .context(criterion.getContext())
                        .build();
                return evaluateSimpleCondition(resolvedCriterion, resolverContext);
            }
        } catch (JsonProcessingException e) {
            throw new ItarazzoIllegalStateException(e);
        }
    }

    private boolean evaluateXPath(final Criterion criterion, final ResolverContext resolverContext) {
        String contextValue = resolveCriterionContext(criterion.getContext(), resolverContext);
        if (Objects.isNull(contextValue)) return false;
        return evaluateXPathExpression(contextValue, criterion.getCondition());
    }

    private boolean evaluateLogicalExpression(final String condition, final ResolverContext resolverContext) {
        // split into left, right and operator
        String[] parts = condition.split("==|!=|<=|>=|<|>");
        if (parts.length != 2) {
            throw new ItarazzoIllegalArgumentException("Invalid simple condition format: " + condition);
        }

        String leftPart = parts[0].trim();
        String rightPart = parts[1].trim();

        // resolved left and right, e.g. $statusCode
        Object leftValue = resolver.resolveExpression(leftPart, resolverContext);
        Object rightValue = resolver.resolveExpression(rightPart, resolverContext);

        // determine operator
        if (condition.contains("==")) {
            return compareValues(leftValue, rightValue) == 0;
        } else if (condition.contains("!=")) {
            return compareValues(leftValue, rightValue) != 0;
        } else if (condition.contains("<=")) {
            return compareValues(leftValue, rightValue) <= 0;
        } else if (condition.contains(">=")) {
            return compareValues(leftValue, rightValue) >= 0;
        } else if (condition.contains("<")) {
            return compareValues(leftValue, rightValue) < 0;
        } else if (condition.contains(">")) {
            return compareValues(leftValue, rightValue) > 0;
        } else {
            throw new ItarazzoUnsupportedException("Unsupported operator in condition: " + condition);
        }
    }

    private int compareValues(final Object leftValue, final Object rightValue) {
        if (leftValue instanceof Number leftNumberValue && rightValue instanceof Number rightNumberValue) {
            return Double.compare(leftNumberValue.doubleValue(), rightNumberValue.doubleValue());
        } else if (leftValue instanceof String leftStringValue && rightValue instanceof String rightStringValue) {
            return (leftStringValue).compareToIgnoreCase(rightStringValue);
        } else if (Objects.isNull(leftValue) && "null".equals(rightValue)) {
            return 0;
        } else {
            throw new ItarazzoIllegalArgumentException("Incomparable types: " + leftValue + " and " + rightValue);
        }
    }

    private boolean evaluateXPathExpression(final String contextValue, final String condition) {
        try {
            Document document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new InputSource(new StringReader(contextValue)));
            XPath xpath = XPathFactory.newInstance().newXPath();
            return (boolean) xpath.evaluate(condition, document, XPathConstants.BOOLEAN);
        } catch (Exception e) {
            throw new ItarazzoIllegalStateException("Error while evaluating XPath expression: " + e.getMessage(), e);
        }
    }
}
