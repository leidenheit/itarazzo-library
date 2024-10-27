package de.leidenheit.infrastructure.evaluation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.leidenheit.core.model.Criterion;
import de.leidenheit.core.model.CriterionExpressionTypeObject;
import de.leidenheit.infrastructure.resolving.ResolverContext;
import de.leidenheit.infrastructure.resolving.SpecExpressionResolver;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class CriterionEvaluatorTest {

    @Mock
    private SpecExpressionResolver resolverMock;
    @Mock
    private ResolverContext resolverContextMock;
    @Mock
    private ObjectMapper mapperMock;

    @InjectMocks
    private CriterionEvaluator underTest;

    @Test
    void shouldSuccessfullyEvalCriterionOfTypeRegEx() {
        // given
        var regExCriterion = Criterion.builder()
                .type(Criterion.CriterionType.REGEX)
                .condition("^200$")
                .context("$statusCode")
                .build();

        given(resolverMock.resolveExpression("$statusCode", resolverContextMock))
                .willReturn("200");

        // when
        var result = underTest.evalCriterion(regExCriterion, resolverContextMock);

        // then
        Assertions.assertTrue(result);
    }

    @Test
    void shouldSuccessfullyEvalCriterionOfTypeJsonPath() throws JsonProcessingException {
        // given
        var jsonPathCriterion = Criterion.builder()
                .type(Criterion.CriterionType.JSONPATH)
                .expressionTypeObject(CriterionExpressionTypeObject.builder()
                        .type(CriterionExpressionTypeObject.CriterionExpressionType.JSONPATH)
                        .version("draft-goessner-dispatch-jsonpath-00")
                        .build())
                .condition("#/name == Chocolate")
                .context("$response.body")
                .build();

        // mock for context value
        var jsonNodeMock = mock(JsonNode.class);
        given(jsonNodeMock.at(anyString()))
                .willReturn(jsonNodeMock);
        given(jsonNodeMock.asText())
                .willReturn("Chocolate");
        given(mapperMock.readTree(anyString()))
                .willReturn(jsonNodeMock);
        given(resolverMock.resolveExpression("$response.body", resolverContextMock))
                .willReturn("{\"name\": \"Chocolate\"}");
        // mock for expected value
        given(resolverMock.resolveString("Chocolate"))
                .willReturn("Chocolate");
        // mock for condition comparison
        given(resolverMock.resolveExpression("Chocolate", resolverContextMock))
                .willReturn("Chocolate");

        // when
        var result = underTest.evalCriterion(jsonPathCriterion, resolverContextMock);

        // then
        Assertions.assertTrue(result);
    }

    @Test
    void shouldSuccessfullyEvalCriterionOfTypeJsonPathAltCondition() throws JsonProcessingException {
        // given
        var jsonPathCriterion = Criterion.builder()
                .type(Criterion.CriterionType.JSONPATH)
                .expressionTypeObject(CriterionExpressionTypeObject.builder()
                        .type(CriterionExpressionTypeObject.CriterionExpressionType.JSONPATH)
                        .version("draft-goessner-dispatch-jsonpath-00")
                        .build())
                .condition("$.id == $inputs.cookieId")
                .context("$response.body")
                .build();

        // mock for context value
        given(resolverMock.resolveExpression("$response.body", resolverContextMock))
                .willReturn("{\"id\": 4711}");
        // mock for expected values
        given(resolverMock.resolveString("$inputs.cookieId"))
                .willReturn("4711");
        // mock for condition comparison
        given(mapperMock.writeValueAsString(any()))
                .willReturn("{\"id\": 4711}");
        given(resolverMock.resolveExpression("4711", resolverContextMock))
                .willReturn("4711");

        // when
        var result = underTest.evalCriterion(jsonPathCriterion, resolverContextMock);

        // then
        Assertions.assertTrue(result);
    }

    @Test
    void shouldSuccessfullyEvalCriterionOfTypeXPath() {
        // given
        var xpathCriterion = Criterion.builder()
                .type(Criterion.CriterionType.XPATH)
                .condition("/root/id = 4711")
                .context("<root><id>4711</id><name>Chocolate</name></root>")
                .build();

        given(resolverMock.resolveExpression("<root><id>4711</id><name>Chocolate</name></root>", resolverContextMock))
                .willReturn("<root><id>4711</id><name>Chocolate</name></root>");

        // when
        var result = underTest.evalCriterion(xpathCriterion, resolverContextMock);

        // then
        Assertions.assertTrue(result);
    }

    @Test
    void shouldSuccessfullyEvalCriterionOfTypeSimple() {
        // given
        var simpleCriterion = Criterion.builder()
                .condition("$response.header.location != null")
                .build();
        // untyped will be handled as SIMPLE

        given(resolverMock.resolveExpression("$response.header.location", resolverContextMock))
                .willReturn("a location");
        given(resolverMock.resolveExpression("null", resolverContextMock))
                .willReturn("null");

        // when
        var result = underTest.evalCriterion(simpleCriterion, resolverContextMock);

        // then
        Assertions.assertTrue(result);
    }
}