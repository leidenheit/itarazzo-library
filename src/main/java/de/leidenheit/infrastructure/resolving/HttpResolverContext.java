package de.leidenheit.infrastructure.resolving;

import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;

import java.util.Objects;

public interface HttpResolverContext extends ResolverContext {

    String getLatestUrl();
    String getLatestHttpMethod();
    int getLatestStatusCode();
    FilterableRequestSpecification getLatestRequest();
    Response getLastestResponse();
    Objects getLatestMessage();
}
