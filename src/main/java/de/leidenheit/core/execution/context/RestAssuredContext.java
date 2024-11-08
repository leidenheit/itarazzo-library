package de.leidenheit.core.execution.context;

import de.leidenheit.infrastructure.resolving.HttpResolverContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import lombok.Builder;
import lombok.Data;

import java.util.Objects;

@Data
@Builder
public class RestAssuredContext implements HttpResolverContext {
    private String latestUrl;
    private String latestHttpMethod;
    private int latestStatusCode;
    private FilterableRequestSpecification latestRequest;
    private Response lastestResponse;
    private Objects latestMessage;
    private String latestContentType;
}