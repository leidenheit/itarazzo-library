package de.leidenheit.infrastructure.parsing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;
import de.leidenheit.core.exception.ItarazzoIllegalStateException;
import de.leidenheit.core.model.*;
import de.leidenheit.infrastructure.validation.ValidationResult;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;

@SuppressWarnings("java:S1192") // magic strings
public class ArazzoDeserializer {

    protected static final Set<String> RESERVED_KEYWORDS = new LinkedHashSet<>(List.of(
            "x-oai-", "x-oas-"
    ));
    protected static final Set<String> ROOT_KEYS = new LinkedHashSet<>(List.of(
            "arazzo", "info", "sourceDescriptions", "workflows", "components", "extensions"
    ));
    protected static final Set<String> INFO_KEYS = new LinkedHashSet<>(List.of(
            "title", "summary", "description", "version", "extensions"
    ));
    protected static final Set<String> SOURCE_DESCRIPTION_KEYS = new LinkedHashSet<>(List.of(
            "name", "url", "type", "extensions"
    ));
    protected static final Set<String> WORKFLOW_KEYS = new LinkedHashSet<>(List.of(
            "workflowId", "summary", "description", "inputs",
            "dependsOn", "steps", "successActions", "failureActions",
            "outputs", "parameters", "extensions"
    ));
    protected static final Set<String> STEP_KEYS = new LinkedHashSet<>(List.of(
            "description", "stepId", "operationId", "operationPath",
            "workflowId", "parameters", "requestBody", "successCriteria",
            "onSuccess", "onFailure", "outputs", "extensions"
    ));
    protected static final Set<String> COMPONENTS_KEYS = new LinkedHashSet<>(List.of(
            "inputs", "parameters", "successActions", "failureActions", "extensions"
    ));
    protected static final Set<String> PARAMETER_KEYS = new LinkedHashSet<>(List.of(
            "name", "in", "value", "extensions"
    ));
    protected static final Set<String> SUCCESS_ACTION_KEYS = new LinkedHashSet<>(List.of(
            "name", "type", "workflowId", "stepId", "criteria", "extensions"
    ));
    protected static final Set<String> FAILURE_ACTION_KEYS = new LinkedHashSet<>(List.of(
            "name", "type", "workflowId", "stepId", "criteria", "retryAfter", "retryLimit", "extensions"
    ));
    protected static final Set<String> CRITERION_KEYS = new LinkedHashSet<>(List.of(
            "context", "condition", "type", "version", "extensions"
    ));
    protected static final Set<String> REQUEST_BODY_KEYS = new LinkedHashSet<>(List.of(
            "contentType", "payload", "replacements", "extensions"
    ));
    protected static final Set<String> PAYLOAD_REPLACEMENT_OBJECT_KEYS = new LinkedHashSet<>(List.of(
            "target", "value", "extensions"
    ));
    protected static final Set<String> REUSABLE_OBJECT_KEYS = new LinkedHashSet<>(List.of(
            "reference", "value"
    ));
    protected static Map<String, Map<String, Set<String>>> KEYS = new LinkedHashMap<>();

    static {
        Map<String, Set<String>> keys10 = new LinkedHashMap<>();

        keys10.put("RESERVED_KEYWORDS", RESERVED_KEYWORDS);
        keys10.put("ROOT_KEYS", ROOT_KEYS);
        keys10.put("INFO_KEYS", INFO_KEYS);
        keys10.put("SOURCE_DESCRIPTION_KEYS", SOURCE_DESCRIPTION_KEYS);
        keys10.put("WORKFLOW_KEYS", WORKFLOW_KEYS);
        keys10.put("STEP_KEYS", STEP_KEYS);
        keys10.put("COMPONENTS_KEYS", COMPONENTS_KEYS);
        keys10.put("PARAMETER_KEYS", PARAMETER_KEYS);
        keys10.put("SUCCESS_ACTION_KEYS", SUCCESS_ACTION_KEYS);
        keys10.put("FAILURE_ACTION_KEYS", FAILURE_ACTION_KEYS);
        keys10.put("CRITERION_KEYS", CRITERION_KEYS);
        keys10.put("REQUEST_BODY_KEYS", REQUEST_BODY_KEYS);
        keys10.put("PAYLOAD_REPLACEMENT_OBJECT_KEYS", PAYLOAD_REPLACEMENT_OBJECT_KEYS);
        keys10.put("REUSABLE_OBJECT_KEYS", REUSABLE_OBJECT_KEYS);

        KEYS.put("arazzo10", keys10);
    }

    private JsonNode rootNode;
    private final Set<String> workflowIds = new HashSet<>();

    public de.leidenheit.infrastructure.parsing.ParseResult deserialize(final JsonNode node,
                                                                        final String path,
                                                                        final ParseOptions options) {
        rootNode = node;
        de.leidenheit.infrastructure.parsing.ParseResult result = new de.leidenheit.infrastructure.parsing.ParseResult();
        try {
            ParseResult rootParseResult = ArazzoDeserializer.ParseResult.builder().build();
            rootParseResult.setOaiAuthor(options.isOaiAuthor());
            rootParseResult.setAllowEmptyStrings(options.isAllowEmptyStrings());
            rootParseResult.setMustValidate(options.isMustValidate());

            ArazzoSpecification arazzo = parseRoot(rootNode, rootParseResult, path);
            result.setArazzo(arazzo);
            result.setMessages(rootParseResult.getMessages());
        } catch (Exception e) {
            if (StringUtils.isNotBlank(e.getMessage())) {
                result.setMessages(List.of(e.getMessage()));
            } else {
                result.setMessages(List.of("Unexpected error while de-serialising arazzo specification"));
            }
            throw new ItarazzoIllegalStateException(e);
        }
        return result;
    }

    public ArazzoSpecification parseRoot(final JsonNode rootNode, final ParseResult parseResult, final String path) {
        String location = "arazzo";
        ArazzoSpecification arazzo = new ArazzoSpecification();
        if (JsonNodeType.OBJECT.equals(rootNode.getNodeType())) {
            ObjectNode node = (ObjectNode) rootNode;

            // arazzo version
            String value = getString("arazzo", node, true, location, parseResult);
            if (Objects.isNull(value) || !value.startsWith("1.0")) {
                return null;
            }
            arazzo.setArazzo(value);

            // info object
            ObjectNode infoObjNode = getObject("info", node, true, location, parseResult);
            if (Objects.nonNull(infoObjNode)) {
                Info info = getInfo(infoObjNode, "info", parseResult);
                arazzo.setInfo(info);
            }

            // list of source descriptions
            ArrayNode sourceDescriptionsArray = getArray("sourceDescriptions", node, true, location, parseResult);
            if (Objects.nonNull(sourceDescriptionsArray) && !sourceDescriptionsArray.isEmpty()) {
                arazzo.setSourceDescriptions(getSourceDescriptionList(sourceDescriptionsArray, parseResult, path));
            }

            // workflows
            ArrayNode workflowsArray = getArray("workflows", node, true, location, parseResult);
            if (Objects.nonNull(workflowsArray) && !workflowsArray.isEmpty()) {
                arazzo.setWorkflows(getWorkflowList(workflowsArray, parseResult, path));
            }

            // components
            ObjectNode componentsObj = getObject("components", node, false, "components", parseResult);
            if (Objects.nonNull(componentsObj)) {
                Components components = getComponents(componentsObj, "components", parseResult);
                arazzo.setComponents(components);
            }

            // extensions
            Map<String, Object> extensions = getExtensions(node);
            if (!extensions.isEmpty()) {
                arazzo.setExtensions(extensions);
            }

            Set<String> keys = getKeys(node);
            Map<String, Set<String>> specKeys = KEYS.get("arazzo10");
            for (String key : keys) {
                if (!specKeys.get("ROOT_KEYS").contains(key) && !key.startsWith("x-")) {
                    parseResult.extra(location, key, node.get(key));
                }
                validateReservedKeywords(specKeys, key, location, parseResult);
            }
        } else {
            parseResult.invalidType(location, "arazzo", "object", rootNode);
            parseResult.invalid();
            return null;
        }
        return arazzo;
    }

    private Components getComponents(final ObjectNode rootNode,
                                     final String location,
                                     final ParseResult parseResult) {
        if (rootNode == null) {
            return null;
        }
        Components components = new Components();

        ObjectNode inputsObj = getObject("inputs", rootNode, false, location, parseResult);
        if (inputsObj != null) {
            components.setInputs(getSchemas(inputsObj, "%s.%s".formatted(location, "inputs"), parseResult, true));
        }

        ObjectNode parametersObj = getObject("parameters", rootNode, false, location, parseResult);
        if (Objects.nonNull(parametersObj)) {
            components.setParameters(getParameters(parametersObj, parseResult, true));
        }

        ObjectNode successActionsObj = getObject("successActions", rootNode, false, location, parseResult);
        if (Objects.nonNull(successActionsObj)) {
            components.setSuccessActions(getSuccessActions(successActionsObj, parseResult, true));
        }

        ObjectNode failureActionsObj = getObject("failureActions", rootNode, false, location, parseResult);
        if (Objects.nonNull(failureActionsObj)) {
            components.setFailureActions(getFailureActions(failureActionsObj, parseResult, true));
        }

        Map<String, Object> extensions = getExtensions(rootNode);
        if (!extensions.isEmpty()) {
            components.setExtensions(extensions);
        }

        Set<String> keys = getKeys(rootNode);
        Map<String, Set<String>> specKeys = KEYS.get("arazzo10");
        for (String key : keys) {
            if (!specKeys.get("COMPONENTS_KEYS").contains(key) && !key.startsWith("x-")) {
                parseResult.extra(location, key, rootNode.get(key));
            }
            validateReservedKeywords(specKeys, key, location, parseResult);
        }

        return components;
    }

    private Map<String, FailureAction> getFailureActions(final ObjectNode node,
                                                         final ParseResult parseResult,
                                                         boolean underComponents) {
        if (Objects.isNull(node)) return Collections.emptyMap();

        Map<String, FailureAction> failureActions = new LinkedHashMap<>();
        Set<String> failureActionKeys = getKeys(node);
        for (String failureActionKey : failureActionKeys) {
            if (underComponents) {
                if (!Pattern.matches("^[a-zA-Z0-9.\\-_]+$",
                        failureActionKey)) {
                    parseResult.warning("failureAction", "name: '%s' does not adhere to regular expression ^[a-zA-Z0-9\\.\\-_]+$"
                            .formatted(failureActionKey));
                }
            }

            JsonNode failureActionValue = node.get(failureActionKey);
            if (JsonNodeType.OBJECT.equals(failureActionValue.getNodeType())) {
                ObjectNode failureActionObj = (ObjectNode) failureActionValue;
                if (Objects.nonNull(failureActionObj)) {
                    var failureAction = getFailureAction(failureActionObj, "failureAction", parseResult);
                    if (Objects.nonNull(failureAction)) {
                        failureActions.put(failureActionKey, failureAction);
                    }
                }
            }
        }
        return failureActions;
    }

    private FailureAction getFailureAction(final ObjectNode node, final String location, final ParseResult parseResult) {
        if (Objects.isNull(node)) return null;

        FailureAction failureAction = FailureAction.builder().build();

        String name = getString("name", node, true, location, parseResult);
        if ((parseResult.isAllowEmptyStrings() && Objects.nonNull(name))
                || (!parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(name))) {
            failureAction.setName(name);
        }

        String type = getString("type", node, true, location, parseResult);
        if ((parseResult.isAllowEmptyStrings() && Objects.nonNull(type))
                || (!parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(type))) {
            failureAction.setType(FailureAction.FailureActionType.valueOf(type.toUpperCase()));
        }

        String workflowId = getString("workflowId", node, false, location, parseResult);
        if ((parseResult.isAllowEmptyStrings() && Objects.nonNull(workflowId))
                || (!parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(workflowId))) {
            failureAction.setWorkflowId(workflowId);
        }

        String stepId = getString("stepId", node, false, location, parseResult);
        if ((parseResult.isAllowEmptyStrings() && Objects.nonNull(stepId))
                || (!parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(stepId))) {
            failureAction.setStepId(stepId);
        }

        BigDecimal retryAfter = getBigDecimal("retryAfter", node, false, location, parseResult);
        if (Objects.nonNull(retryAfter)) {
            failureAction.setRetryAfter(retryAfter);
        }

        Integer retryLimit = getInteger("retryLimit", node, false, location, parseResult);
        if (Objects.nonNull(retryLimit)) {
            failureAction.setRetryLimit(retryLimit);
        }

        ArrayNode criteriaArray = getArray("criteria", node, false, location, parseResult);
        if (Objects.nonNull(criteriaArray) && !criteriaArray.isEmpty()) {
            failureAction.setCriteria(getCriteriaList(criteriaArray, location, parseResult));
        }

        Map<String, Object> extensions = getExtensions(node);
        if (!extensions.isEmpty()) {
            failureAction.setExtensions(extensions);
        }

        Set<String> keys = getKeys(node);
        Map<String, Set<String>> specKeys = KEYS.get("arazzo10");
        for (String key : keys) {
            if (!specKeys.get("FAILURE_ACTION_KEYS").contains(key) && !key.startsWith("x-")) {
                parseResult.extra(location, key, node.get(key));
            }
            validateReservedKeywords(specKeys, key, location, parseResult);
        }

        return failureAction;
    }

    private Map<String, SuccessAction> getSuccessActions(
            final ObjectNode node,
            final ParseResult parseResult,
            boolean underComponents) {
        if (Objects.isNull(node)) return Collections.emptyMap();

        Map<String, SuccessAction> successActions = new LinkedHashMap<>();
        Set<String> successActionKeys = getKeys(node);
        for (String successActionKey : successActionKeys) {
            if (underComponents) {
                if (!Pattern.matches("^[a-zA-Z0-9.\\-_]+$", successActionKey)) {
                    parseResult.warning("successAction",
                            "name: '%s' does not adhere to regular expression ^[a-zA-Z0-9\\.\\-_]+$"
                                    .formatted(successActionKey));
                }
            }

            JsonNode successActionValue = node.get(successActionKey);
            if (JsonNodeType.OBJECT.equals(successActionValue.getNodeType())) {
                ObjectNode successActionObj = (ObjectNode) successActionValue;
                if (Objects.nonNull(successActionObj)) {
                    var successAction = getSuccessAction(successActionObj, "successAction", parseResult);
                    if (Objects.nonNull(successAction)) {
                        successActions.put(successActionKey, successAction);
                    }
                }
            }
        }
        return successActions;
    }

    private SuccessAction getSuccessAction(final ObjectNode node,
                                           final String location,
                                           final ParseResult parseResult) {
        if (Objects.isNull(node)) return null;

        SuccessAction successAction = SuccessAction.builder().build();

        String name = getString("name", node, true, location, parseResult);
        if ((parseResult.isAllowEmptyStrings() && Objects.nonNull(name))
                || (!parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(name))) {
            successAction.setName(name);
        }

        String type = getString("type", node, true, location, parseResult);
        if ((parseResult.isAllowEmptyStrings() && Objects.nonNull(type))
                || (!parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(type))) {
            successAction.setType(SuccessAction.SuccessActionType.valueOf(type.toUpperCase()));
        }

        String workflowId = getString("workflowId", node, false, location, parseResult);
        if ((parseResult.isAllowEmptyStrings() && Objects.nonNull(workflowId))
                || (!parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(workflowId))) {
            successAction.setWorkflowId(workflowId);
        }

        String stepId = getString("stepId", node, false, location, parseResult);
        if ((parseResult.isAllowEmptyStrings() && Objects.nonNull(stepId))
                || (!parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(stepId))) {
            successAction.setStepId(stepId);
        }

        ArrayNode criteriaArray = getArray("criteria", node, false, location, parseResult);
        if (Objects.nonNull(criteriaArray) && !criteriaArray.isEmpty()) {
            successAction.setCriteria(getCriteriaList(criteriaArray, location, parseResult));
        }

        Map<String, Object> extensions = getExtensions(node);
        if (!extensions.isEmpty()) {
            successAction.setExtensions(extensions);
        }

        Set<String> keys = getKeys(node);
        Map<String, Set<String>> specKeys = KEYS.get("arazzo10");
        for (String key : keys) {
            if (!specKeys.get("SUCCESS_ACTION_KEYS").contains(key) && !key.startsWith("x-")) {
                parseResult.extra(location, key, node.get(key));
            }
            validateReservedKeywords(specKeys, key, location, parseResult);
        }

        return successAction;
    }

    private List<Criterion> getCriteriaList(final ArrayNode node,
                                            final String location,
                                            final ParseResult parseResult) {
        if (Objects.isNull(node)) return Collections.emptyList();

        List<Criterion> criteria = new ArrayList<>();
        for (JsonNode item : node) {
            if (JsonNodeType.OBJECT.equals(item.getNodeType())) {
                var criterion = getCriterion((ObjectNode) item, location, parseResult);
                if (Objects.nonNull(criterion)) {
                    criteria.add(criterion);
                }
            }
        }

        return criteria;
    }

    private Criterion getCriterion(final ObjectNode node, final String location, final ParseResult parseResult) {
        if (Objects.isNull(node)) return null;

        Criterion criterion = Criterion.builder().build();

        String condition = getString("condition", node, true, location, parseResult);
        if (parseResult.isAllowEmptyStrings() && Objects.nonNull(condition)
                || !parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(condition)) {
            criterion.setCondition(condition);
        }

        String context = getString("context", node, false, location, parseResult);
        if (parseResult.isAllowEmptyStrings() && Objects.nonNull(context)
                || !parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(context)) {
            criterion.setContext(context);
        }
        String typeAsString = getString("type", node, false, location, parseResult);
        if (parseResult.isAllowEmptyStrings() && Objects.nonNull(typeAsString)
                || !parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(typeAsString)) {
            Criterion.CriterionType type = Criterion.CriterionType.valueOf(typeAsString.toUpperCase());
            criterion.setType(type);
            if (Objects.nonNull(criterion.getExpressionTypeObject())) {
                String expTypeObjTypeAsType = getString("type", node, true, location, parseResult);
                String expTypeObjTypeAsVersion = getString("version", node, true, location, parseResult);
                if ((parseResult.isAllowEmptyStrings() && Objects.nonNull(expTypeObjTypeAsType)
                        || !parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(expTypeObjTypeAsType))
                        &&
                        (parseResult.isAllowEmptyStrings() && Objects.nonNull(expTypeObjTypeAsVersion)
                                || !parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(expTypeObjTypeAsVersion))
                ) {
                    criterion.getExpressionTypeObject().setVersion(expTypeObjTypeAsVersion);

                    Map<String, Object> extensions = getExtensions(node);
                    if (!extensions.isEmpty()) {
                        criterion.setExtensions(extensions);
                    }
                }
            }
        } else {
            criterion.setType(Criterion.CriterionType.SIMPLE);
        }

        Map<String, Object> extensions = getExtensions(node);
        if (!extensions.isEmpty()) {
            criterion.setExtensions(extensions);
        }

        Set<String> keys = getKeys(node);
        Map<String, Set<String>> specKeys = KEYS.get("arazzo10");
        for (String key : keys) {
            if (!specKeys.get("CRITERION_KEYS").contains(key) && !key.startsWith("x-")) {
                parseResult.extra(location, key, node.get(key));
            }
            validateReservedKeywords(specKeys, key, location, parseResult);
        }

        return criterion;
    }

    private Map<String, Parameter> getParameters(final ObjectNode obj,
                                                 final ParseResult parseResult,
                                                 final boolean underComponents) {
        if (Objects.isNull(obj)) return Collections.emptyMap();

        Map<String, Parameter> parameters = new LinkedHashMap<>();
        Set<String> parameterKeys = getKeys(obj);
        for (String parameterName : parameterKeys) {
            if (underComponents) {
                if (!Pattern.matches("^[a-zA-Z0-9.\\-_]+$",
                        parameterName)) {
                    parseResult.warning("parameter",
                            "name: '%s' does not adhere to regular expression ^[a-zA-Z0-9\\.\\-_]+$"
                                    .formatted(parameterName));
                }
            }

            JsonNode parameterValue = obj.get(parameterName);
            if (JsonNodeType.OBJECT.equals(parameterValue.getNodeType())) {
                ObjectNode parameterObj = (ObjectNode) parameterValue;
                if (Objects.nonNull(parameterObj)) {
                    var parameter = getParameter(parameterObj, "parameter", parseResult);
                    if (Objects.nonNull(parameter)) {
                        parameters.put(parameterName, parameter);
                    }
                }
            }
        }
        return parameters;
    }

    private Parameter getParameter(final ObjectNode obj, final String location, final ParseResult parseResult) {
        if (Objects.isNull(obj)) return null;

        Parameter parameter = Parameter.builder().build();

        String name = getString("name", obj, true, location, parseResult);
        if (parseResult.isAllowEmptyStrings() && Objects.nonNull(name)
                || !parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(name)) {
            parameter.setName(name);
        }

        String in = getString("in", obj, false, location, parseResult);
        if ((parseResult.isAllowEmptyStrings() && Objects.nonNull(in))
                || (!parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(in))) {
            parameter.setIn(Parameter.ParameterIn.valueOf(in.toUpperCase()));
        }

        Object value = getAnyType("value", obj, location, parseResult);
        if (Objects.isNull(value)) {
            parseResult.missing(location, "value");
        } else {
            parameter.setValue(value);
        }

        Map<String, Object> extensions = getExtensions(obj);
        if (!extensions.isEmpty()) {
            parameter.setExtensions(extensions);
        }

        Set<String> keys = getKeys(obj);
        Map<String, Set<String>> specKeys = KEYS.get("arazzo10");
        for (String key : keys) {
            if (!specKeys.get("PARAMETER_KEYS").contains(key) && !key.startsWith("x-")) {
                parseResult.extra(location, key, obj.get(key));
            }
            validateReservedKeywords(specKeys, key, location, parseResult);
        }

        return parameter;
    }

    private List<Workflow> getWorkflowList(final ArrayNode node,
                                           final ParseResult parseResult,
                                           final String path) {
        if (Objects.isNull(node)) return Collections.emptyList();

        List<Workflow> workflows = new ArrayList<>();
        for (JsonNode item : node) {
            if (JsonNodeType.OBJECT.equals(item.getNodeType())) {
                Workflow workflow = getWorkflow((ObjectNode) item, "workflow", parseResult, path);
                if (Objects.nonNull(workflow)) {
                    workflows.add(workflow);
                }
            }
        }
        return workflows;
    }

    private List<SourceDescription> getSourceDescriptionList(final ArrayNode node,
                                                             final ParseResult parseResult,
                                                             final String path) {
        if (Objects.isNull(node)) return Collections.emptyList();

        List<SourceDescription> sourceDescriptions = new ArrayList<>();
        for (JsonNode item : node) {
            if (JsonNodeType.OBJECT.equals(item.getNodeType())) {
                SourceDescription sourceDescription = getSourceDescription(
                        (ObjectNode) item, "sourceDescription", parseResult, path);
                if (Objects.nonNull(sourceDescription)) {
                    sourceDescriptions.add(sourceDescription);
                }
            }
        }
        return sourceDescriptions;
    }

    private Workflow getWorkflow(final ObjectNode node,
                                 final String location,
                                 final ParseResult parseResult,
                                 final String path) {
        if (Objects.isNull(node)) return null;

        Workflow workflow = new Workflow();

        String workflowId = getString("workflowId", node, true, location, parseResult, workflowIds);
        if (parseResult.isAllowEmptyStrings() && Objects.nonNull(workflowId)
                || !parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(workflowId)) {
            workflow.setWorkflowId(workflowId);
        }

        String summary = getString("summary", node, false, location, parseResult);
        if (parseResult.isAllowEmptyStrings() && Objects.nonNull(summary)
                || !parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(summary)) {
            workflow.setSummary(summary);
        }

        String description = getString("description", node, false, location, parseResult);
        if (parseResult.isAllowEmptyStrings() && Objects.nonNull(description)
                || !parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(description)) {
            workflow.setDescription(description);
        }

        ObjectNode inputsObj = getObject("inputs", node, false, location, parseResult);
        if (inputsObj != null) {
            workflow.setInputs(inputsObj);
        }

        ArrayNode dependsOnArray = getArray("dependsOn", node, false, location, parseResult);
        if (Objects.nonNull(dependsOnArray) && !dependsOnArray.isEmpty()) {
            workflow.setDependsOn(getDependsOnList(dependsOnArray));
        }

        ArrayNode stepsArray = getArray("steps", node, true, location, parseResult);
        if (Objects.nonNull(stepsArray) && !stepsArray.isEmpty()) {
            workflow.setSteps(getStepsList(stepsArray, parseResult));
        }

        ArrayNode successActionArray = getArray("successActions", node, false, location, parseResult);
        if (Objects.nonNull(successActionArray) && !successActionArray.isEmpty()) {
            workflow.setSuccessActions(getSuccessActionList(successActionArray, parseResult));
        }

        ArrayNode failureActionArray = getArray("failureActions", node, false, location, parseResult);
        if (Objects.nonNull(failureActionArray) && !failureActionArray.isEmpty()) {
            workflow.setFailureActions(getFailureActionList(failureActionArray, parseResult));
        }

        ObjectNode outputsObj = getObject("outputs", node, false, location, parseResult);
        if (Objects.nonNull(outputsObj)) {
            workflow.setOutputs(getOutputs(outputsObj, String.format("%s.%s", location, "outputs"), parseResult));
        }

        ArrayNode parameterArray = getArray("parameters", node, false, location, parseResult);
        if (Objects.nonNull(parameterArray) && !parameterArray.isEmpty()) {
            workflow.setParameters(getParameterList(parameterArray, parseResult));
        }

        Map<String, Object> extensions = getExtensions(node);
        if (!extensions.isEmpty()) {
            workflow.setExtensions(extensions);
        }

        Set<String> keys = getKeys(node);
        Map<String, Set<String>> specKeys = KEYS.get("arazzo10");
        for (String key : keys) {
            if (!specKeys.get("WORKFLOW_KEYS").contains(key) && !key.startsWith("x-")) {
                parseResult.extra(location, key, node.get(key));
            }
            validateReservedKeywords(specKeys, key, location, parseResult);
        }

        return workflow;
    }

    private List<Step> getStepsList(final ArrayNode node,
                                    final ParseResult parseResult) {
        if (Objects.isNull(node)) return Collections.emptyList();

        ArrayList<Step> steps = new ArrayList<>();
        for (JsonNode item : node) {
            if (JsonNodeType.OBJECT.equals(item.getNodeType())) {
                Step step = getStep((ObjectNode) item, "step", parseResult);
                if (Objects.nonNull(step)) {
                    steps.add(step);
                }
            }
        }

        return steps;
    }

    private Step getStep(final ObjectNode node, final String location, final ParseResult parseResult) {
        if (Objects.isNull(node)) return null;

        Step step = Step.builder().build();

        String description = getString("description", node, false, location, parseResult);
        if (parseResult.isAllowEmptyStrings() && Objects.nonNull(description)
                || !parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(description)) {
            step.setDescription(description);
        }

        String stepId = getString("stepId", node, true, location, parseResult);
        if (parseResult.isAllowEmptyStrings() && Objects.nonNull(stepId)
                || !parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(stepId)) {
            step.setStepId(stepId);
        }

        String operationId = getString("operationId", node, false, location, parseResult);
        if (parseResult.isAllowEmptyStrings() && Objects.nonNull(operationId)
                || !parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(operationId)) {
            step.setOperationId(operationId);
        }

        String operationPath = getString("operationPath", node, false, location, parseResult);
        if (parseResult.isAllowEmptyStrings() && Objects.nonNull(operationPath)
                || !parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(operationPath)) {
            step.setOperationPath(operationPath);
        }

        String workflowId = getString("workflowId", node, false, location, parseResult);
        if (parseResult.isAllowEmptyStrings() && Objects.nonNull(workflowId)
                || !parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(workflowId)) {
            step.setWorkflowId(workflowId);
        }

        ArrayNode parameterArray = getArray("parameters", node, false, location, parseResult);
        if (Objects.nonNull(parameterArray) && !parameterArray.isEmpty()) {
            step.setParameters(getParameterList(parameterArray, parseResult));
        }

        ObjectNode requestBodyObj = getObject("requestBody", node, false, location, parseResult);
        if (Objects.nonNull(requestBodyObj)) {
            step.setRequestBody(getRequestBody(requestBodyObj, "requestBody", parseResult));
        }

        ArrayNode successCriteriaArray = getArray("successCriteria", node, false, location, parseResult);
        if (Objects.nonNull(successCriteriaArray) && !successCriteriaArray.isEmpty()) {
            step.setSuccessCriteria(getCriteriaList(successCriteriaArray, location, parseResult));
        }

        ArrayNode onSuccessArray = getArray("onSuccess", node, false, location, parseResult);
        if (Objects.nonNull(onSuccessArray) && !onSuccessArray.isEmpty()) {
            step.setOnSuccess(getSuccessActionList(onSuccessArray, parseResult));
        }

        ArrayNode onFailureArray = getArray("onFailure", node, false, location, parseResult);
        if (Objects.nonNull(onFailureArray) && !onFailureArray.isEmpty()) {
            step.setOnFailure(getFailureActionList(onFailureArray, parseResult));
        }

        ObjectNode outputsObj = getObject("outputs", node, false, location, parseResult);
        if (Objects.nonNull(outputsObj)) {
            step.setOutputs(getOutputs(outputsObj, String.format("%s.%s", location, "outputs"), parseResult));
        }

        Map<String, Object> extensions = getExtensions(node);
        if (!extensions.isEmpty()) {
            step.setExtensions(extensions);
        }

        Set<String> keys = getKeys(node);
        Map<String, Set<String>> specKeys = KEYS.get("arazzo10");
        for (String key : keys) {
            if (!specKeys.get("STEP_KEYS").contains(key) && !key.startsWith("x-")) {
                parseResult.extra(location, key, node.get(key));
            }
            validateReservedKeywords(specKeys, key, location, parseResult);
        }

        return step;
    }

    private Map<String, Object> getOutputs(final ObjectNode node, final String location, final ParseResult parseResult) {

        if (Objects.isNull(node)) return Collections.emptyMap();

        Map<String, Object> outputs = new HashMap<>();
        if (JsonNodeType.OBJECT.equals(node.getNodeType())) {
            Set<String> keys = getKeys(node);
            for (String key : keys) {
                var nodeValue = node.get(key);
                outputs.put(key, nodeValue.asText());
            }
        } else {
            parseResult.invalidType(location, "outputs", "object", node);
            parseResult.invalid();
        }

        return outputs;
    }

    private List<FailureAction> getFailureActionList(final ArrayNode node,
                                                     final ParseResult parseResult) {
        if (Objects.isNull(node)) return Collections.emptyList();

        List<FailureAction> failureActionList = new ArrayList<>();
        for (JsonNode item : node) {
            if (JsonNodeType.OBJECT.equals(item.getNodeType())) {
                if (item.has("reference")) {
                    ReusableObject reusableObject = getReusableObject((ObjectNode) item, "reusableObject", parseResult);

                    var resolver = ComponentsReferenceResolver.getInstance(this.rootNode.get("components"));
                    var resolved = resolver.resolveComponent(Objects.requireNonNull(reusableObject).getReference().toString());
                    if (Objects.nonNull(resolved) && (resolved instanceof ObjectNode failureActionObj)) {
                        var failureAction = getFailureAction(failureActionObj, "failureAction", parseResult);
                        if (Objects.nonNull(failureAction)) {
                            failureActionList.add(failureAction);
                        }
                    }
                } else {
                    FailureAction failureAction = getFailureAction((ObjectNode) item, "failureAction", parseResult);
                    if (Objects.nonNull(failureAction)) {
                        failureActionList.add(failureAction);
                    }
                }
            }
        }

        return failureActionList;
    }

    private List<SuccessAction> getSuccessActionList(final ArrayNode node,
                                                     final ParseResult parseResult) {
        if (Objects.isNull(node)) return Collections.emptyList();

        List<SuccessAction> successActionList = new ArrayList<>();
        for (JsonNode item : node) {
            if (JsonNodeType.OBJECT.equals(item.getNodeType())) {
                if (item.has("reference")) {
                    ReusableObject reusableObject = getReusableObject((ObjectNode) item, "reusableObject", parseResult);
                    var resolver = ComponentsReferenceResolver.getInstance(this.rootNode.get("components"));
                    var resolved = resolver.resolveComponent(Objects.requireNonNull(reusableObject).getReference().toString());
                    if (Objects.nonNull(resolved) && (resolved instanceof ObjectNode successActionObj)) {
                        var successAction = getSuccessAction(successActionObj, "successAction", parseResult);
                        if (Objects.nonNull(successAction)) {
                            successActionList.add(successAction);
                        }
                    }
                } else {
                    SuccessAction successAction = getSuccessAction((ObjectNode) item, "successAction", parseResult);
                    if (Objects.nonNull(successAction)) {
                        successActionList.add(successAction);
                    }
                }
            }
        }

        return successActionList;
    }

    private ReusableObject getReusableObject(final ObjectNode node,
                                             final String location,
                                             final ParseResult parseResult) {
        if (Objects.isNull(node)) return null;
        ReusableObject reusableObject = ReusableObject.builder().build();

        String reference = getString("reference", node, true, location, parseResult);
        if (parseResult.isAllowEmptyStrings() && Objects.nonNull(reference)
                || !parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(reference)) {
            reusableObject.setReference(reference);
        }

        String value = getString("value", node, false, location, parseResult);
        if (parseResult.isAllowEmptyStrings() && Objects.nonNull(value)
                || !parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(value)) {
            reusableObject.setValue(value);
        }

        Set<String> keys = getKeys(node);
        Map<String, Set<String>> specKeys = KEYS.get("arazzo10");
        for (String key : keys) {
            if (!specKeys.get("REUSABLE_OBJECT_KEYS").contains(key) && !key.startsWith("x-")) {
                parseResult.extra(location, key, node.get(key));
            }
            validateReservedKeywords(specKeys, key, location, parseResult);
        }
        return reusableObject;
    }

    private RequestBody getRequestBody(final ObjectNode node,
                                       final String location,
                                       final ParseResult parseResult) {
        if (Objects.isNull(node)) return null;
        RequestBody requestBody = RequestBody.builder().build();

        String contentType = getString("contentType", node, false, location, parseResult);
        if (parseResult.isAllowEmptyStrings() && Objects.nonNull(contentType)
                || !parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(contentType)) {
            requestBody.setContentType(contentType);
        }

        JsonNode payloadNode = node.get("payload");
        if (Objects.nonNull(payloadNode)) {
            requestBody.setPayload(getPlayload(payloadNode, location, parseResult));
        }

        ArrayNode payloadReplacementObjectArray = getArray("replacements", node, false, location, parseResult);
        if (Objects.nonNull(payloadReplacementObjectArray)) {
            requestBody.setReplacements(getPayloadReplacementObjectList(payloadReplacementObjectArray, parseResult));
        }

        Map<String, Object> extensions = getExtensions(node);
        if (!extensions.isEmpty()) {
            requestBody.setExtensions(extensions);
        }

        Set<String> keys = getKeys(node);
        Map<String, Set<String>> specKeys = KEYS.get("arazzo10");
        for (String key : keys) {
            if (!specKeys.get("REQUEST_BODY_KEYS").contains(key) && !key.startsWith("x-")) {
                parseResult.extra(location, key, node.get(key));
            }
            validateReservedKeywords(specKeys, key, location, parseResult);
        }

        return requestBody;
    }

    private Object getPlayload(final JsonNode node,
                               final String location,
                               final ParseResult parseResult) {
        if (Objects.isNull(node)) return null;
        if (node.isTextual()) {
            return node.asText();
        } else if (node.isObject()) {
            return node;
        }
        parseResult.invalidType(location, "payload: ", "string|object", node);
        parseResult.invalid();
        return null;
    }

    private List<PayloadReplacementObject> getPayloadReplacementObjectList(final ArrayNode node,
                                                                           final ParseResult parseResult) {
        if (Objects.isNull(node) || node.isEmpty()) return Collections.emptyList();
        List<PayloadReplacementObject> payloadReplacementObjects = new ArrayList<>();

        for (JsonNode item : node) {
            if (JsonNodeType.OBJECT.equals(item.getNodeType())) {
                PayloadReplacementObject payloadReplacementObject = getPayloadReplacementObject((ObjectNode) item, "payloadReplacementObject", parseResult);
                if (Objects.nonNull(payloadReplacementObject)) {
                    payloadReplacementObjects.add(payloadReplacementObject);
                }
            }
        }

        return payloadReplacementObjects;
    }

    private PayloadReplacementObject getPayloadReplacementObject(final ObjectNode node,
                                                                 final String location,
                                                                 final ParseResult parseResult) {

        PayloadReplacementObject payloadReplacementObject = PayloadReplacementObject.builder().build();

        String target = getString("target", node, true, location, parseResult);
        if (parseResult.isAllowEmptyStrings() && Objects.nonNull(target)
                || !parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(target)) {
            payloadReplacementObject.setTarget(target);
        }

        JsonNode valueNode = node.get("value");
        if (Objects.nonNull(valueNode)) {
            payloadReplacementObject.setValue(valueNode.asText());
        }

        Map<String, Object> extensions = getExtensions(node);
        if (!extensions.isEmpty()) {
            payloadReplacementObject.setExtensions(extensions);
        }

        Set<String> keys = getKeys(node);
        Map<String, Set<String>> specKeys = KEYS.get("arazzo10");
        for (String key : keys) {
            if (!specKeys.get("PAYLOAD_REPLACEMENT_OBJECT_KEYS").contains(key) && !key.startsWith("x-")) {
                parseResult.extra(location, key, node.get(key));
            }
            validateReservedKeywords(specKeys, key, location, parseResult);
        }

        return payloadReplacementObject;
    }

    private List<Parameter> getParameterList(final ArrayNode node,
                                             final ParseResult parseResult) {
        if (Objects.isNull(node)) return Collections.emptyList();
        List<Parameter> parameters = new ArrayList<>();
        for (JsonNode item : node) {
            if (JsonNodeType.OBJECT.equals(item.getNodeType())) {
                if (item.has("reference")) {
                    ReusableObject reusableObject = getReusableObject((ObjectNode) item, "reusableObject", parseResult);
                    var resolver = ComponentsReferenceResolver.getInstance(this.rootNode.get("components"));
                    var resolved = resolver.resolveComponent(Objects.requireNonNull(reusableObject).getReference().toString());
                    if (Objects.nonNull(resolved) && (resolved instanceof ObjectNode parameterObj)) {
                        var parameter = getParameter(parameterObj, "parameter", parseResult);
                        if (Objects.nonNull(parameter)) {
                            // override value if applied to reusable object
                            if (!Strings.isNullOrEmpty(reusableObject.getValue())) {
                                parameter.setValue(reusableObject.getValue());
                            }
                            parameters.add(parameter);
                        }
                    }
                } else {
                    Parameter parameter = getParameter((ObjectNode) item, "parameter", parseResult);
                    if (Objects.nonNull(parameters)) {
                        parameters.add(parameter);
                    }
                }
            }
        }
        return parameters;
    }

    private List<String> getDependsOnList(final ArrayNode node) {
        if (Objects.isNull(node)) return Collections.emptyList();

        List<String> dependsOn = new ArrayList<>();
        for (JsonNode item : node) {
            if (JsonNodeType.STRING.equals(item.getNodeType())) {
                dependsOn.add(item.asText());
            }
        }
        return dependsOn;
    }

    private SourceDescription getSourceDescription(final ObjectNode node,
                                                   final String location,
                                                   final ParseResult parseResult,
                                                   final String path) {
        if (Objects.isNull(node)) return null;

        SourceDescription sourceDescription = new SourceDescription();

        String name = getString("name", node, true, location, parseResult);
        if (parseResult.isAllowEmptyStrings() && Objects.nonNull(name)
                || !parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(name)) {
            sourceDescription.setName(name);
        }

        String typeAsString = getString("type", node, false, location, parseResult);
        if ((parseResult.isAllowEmptyStrings() && Objects.nonNull(typeAsString))
                || (!parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(typeAsString))) {
            var type = SourceDescription.SourceDescriptionType.valueOf(typeAsString.toUpperCase());
            sourceDescription.setType(type);
        }

        String urlAsString = getString("url", node, true, location, parseResult);
        if ((parseResult.isAllowEmptyStrings() && Objects.nonNull(urlAsString)) || (!parseResult.isAllowEmptyStrings() && StringUtils.isNotBlank(urlAsString))) {
            if (!isValidURL(urlAsString) && Objects.nonNull(path)) {
                try {
                    final URI absURI = new URI(path.replace("\\\\", "/"));
                    if ("http".equals(absURI.getScheme()) || "https".equals(absURI.getScheme())) {
                        urlAsString = absURI.resolve(new URI(urlAsString)).toString();
                    }
                } catch (URISyntaxException e) {
                    parseResult.warning("", "sourceDescription '%s' contains an invalid url: %s"
                            .formatted(sourceDescription.getName(), urlAsString));
                }
            }
            sourceDescription.setUrl(urlAsString);
        }

        Map<String, Object> extensions = getExtensions(node);
        if (!extensions.isEmpty()) {
            sourceDescription.setExtensions(extensions);
        }

        Set<String> keys = getKeys(node);
        Map<String, Set<String>> specKeys = KEYS.get("arazzo10");
        for (String key : keys) {
            if (!specKeys.get("SOURCE_DESCRIPTION_KEYS").contains(key) && !key.startsWith("x-")) {
                parseResult.extra(location, key, node.get(key));
            }
            validateReservedKeywords(specKeys, key, location, parseResult);
        }

        return sourceDescription;
    }

    private Map<String, Object> getExtensions(final ObjectNode node) {
        Map<String, Object> extensions = tryDirectExtensions(node);
        if (extensions.isEmpty()) {
            extensions = tryUnwrapLookupExtensions(node);
        }
        return extensions;
    }

    private Map<String, Object> tryUnwrapLookupExtensions(final ObjectNode node) {
        Map<String, Object> extensions = new LinkedHashMap<>();

        JsonNode extensionsNode = node.get("extensions");
        if (Objects.nonNull(extensionsNode) && JsonNodeType.OBJECT.equals(node.getNodeType())) {
            ObjectNode extensionsObjectNode = (ObjectNode) extensionsNode;
            extensions.putAll(tryDirectExtensions(extensionsObjectNode));
        }

        return extensions;
    }

    private Map<String, Object> tryDirectExtensions(final ObjectNode node) {
        Map<String, Object> extensions = new LinkedHashMap<>();

        Set<String> keys = getKeys(node);
        for (String key : keys) {
            if (key.startsWith("x-")) {
                extensions.put(key, new ObjectMapper().convertValue(node.get(key), Object.class));
            }
        }

        return extensions;
    }

    private boolean isValidURL(final String urlAsString) {
        try {
            URL url = new URL(urlAsString);
            url.toURI();
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    public Info getInfo(final ObjectNode node,
                        final String location,
                        final ParseResult parseResult) {

        if (Objects.isNull(node)) return null;

        Info info = new Info();

        String value = getString("title", node, true, location, parseResult);
        if ((parseResult.isAllowEmptyStrings() && value != null) || (!parseResult.isAllowEmptyStrings() && !StringUtils.isBlank(value))) {
            info.setTitle(value);
        }

        value = getString("description", node, false, location, parseResult);
        if ((parseResult.isAllowEmptyStrings() && value != null) || (!parseResult.isAllowEmptyStrings() && !StringUtils.isBlank(value))) {
            info.setDescription(value);
        }

        value = getString("summary", node, false, location, parseResult);
        if (StringUtils.isNotBlank(value)) {
            info.setSummary(value);
        }

        value = getString("version", node, true, location, parseResult);
        if ((parseResult.isAllowEmptyStrings() && value != null) || (!parseResult.isAllowEmptyStrings() && !StringUtils.isBlank(value))) {
            info.setVersion(value);
        }

        Map<String, Object> extensions = getExtensions(node);
        if (!extensions.isEmpty()) {
            info.setExtensions(extensions);
        }

        Set<String> keys = getKeys(node);
        Map<String, Set<String>> specKeys = KEYS.get("arazzo10");
        for (String key : keys) {
            if (!specKeys.get("INFO_KEYS").contains(key) && !key.startsWith("x-")) {
                parseResult.extra(location, key, node.get(key));
            }
            validateReservedKeywords(specKeys, key, location, parseResult);
        }

        return info;
    }

    public ArrayNode getArray(final String key,
                              final ObjectNode node,
                              final boolean required,
                              final String location,
                              final ParseResult result) {
        return getArray(key, node, required, location, result, false);
    }

    public ArrayNode getArray(final String key,
                              final ObjectNode node,
                              final boolean required,
                              final String location,
                              final ParseResult result,
                              final boolean noInvalidError) {
        JsonNode value = node.get(key);
        ArrayNode arrayNode = null;
        if (value == null) {
            if (required) {
                result.missing(location, key);
                result.invalid();
            }
        } else if (!value.getNodeType().equals(JsonNodeType.ARRAY)) {
            if (!noInvalidError) {
                result.invalidType(location, key, "array", value);
            }
        } else {
            arrayNode = (ArrayNode) value;
        }
        return arrayNode;
    }

    public Set<String> getKeys(final ObjectNode node) {
        Set<String> keys = new LinkedHashSet<>();
        if (node == null) {
            return keys;
        }

        Iterator<String> it = node.fieldNames();
        while (it.hasNext()) {
            keys.add(it.next());
        }

        return keys;
    }

    private void validateReservedKeywords(final Map<String, Set<String>> specKeys,
                                          final String key,
                                          final String location,
                                          final ParseResult result) {
        if (!result.isOaiAuthor() && specKeys.get("RESERVED_KEYWORDS").stream()
                .filter(key::startsWith)
                .findAny()
                .orElse(null) != null) {
            result.reserved(location, key);
        }
    }

    public ObjectNode getObject(final String key,
                                final ObjectNode node,
                                final boolean required,
                                final String location,
                                final ParseResult result) {
        JsonNode value = node.get(key);
        ObjectNode object = null;
        if (Objects.isNull(value)) {
            if (required) {
                result.missing(location, key);
                result.invalid();
            }
        } else if (!value.getNodeType().equals(JsonNodeType.OBJECT)) {
            result.invalidType(location, key, "object", value);
            if (required) {
                result.invalid();
            }
        } else {
            object = (ObjectNode) value;
        }
        return object;
    }

    private String getString(final String key,
                             final ObjectNode node,
                             final boolean required,
                             final String location,
                             final ParseResult parseResult) {
        return getString(key, node, required, location, parseResult, null);
    }

    private String getString(final String key,
                             final ObjectNode node,
                             final boolean required,
                             final String location,
                             final ParseResult parseResult,
                             final Set<String> uniqueValues) {
        return getString(key, node, required, location, parseResult, uniqueValues, false);
    }

    private String getString(final String key,
                             final ObjectNode node,
                             final boolean required,
                             final String location,
                             final ParseResult parseResult,
                             final Set<String> uniqueValues,
                             final boolean noInvalidError) {
        String value = null;
        JsonNode v = node.get(key);
        if (Objects.isNull(v)) {
            if (required) {
                parseResult.missing(location, key);
                parseResult.invalid();
            }
        } else if (!v.isValueNode()) {
            if (!noInvalidError) {
                parseResult.invalidType(location, key, "string", node);
            }
        } else if (!v.isNull()) {
            value = v.asText();
            if (Objects.nonNull(uniqueValues) && !uniqueValues.add(value)) {
                parseResult.unique(location, "workflowId");
                parseResult.invalid();
            }
        }
        return value;
    }

    public Boolean getBoolean(String key, ObjectNode node, boolean required, String location, ParseResult result) {
        Boolean value = null;
        JsonNode v = node.get(key);
        if (Objects.isNull(node) || Objects.isNull(v)) {
            if (required) {
                result.missing(location, key);
                result.invalid();
            }
        } else {
            if (v.getNodeType().equals(JsonNodeType.BOOLEAN)) {
                value = v.asBoolean();
            } else if (v.getNodeType().equals(JsonNodeType.STRING)) {
                String stringValue = v.textValue();
                return Boolean.parseBoolean(stringValue);
            }
        }
        return value;
    }

    public BigDecimal getBigDecimal(String key, ObjectNode node, boolean required, String location, ParseResult
            result) {
        BigDecimal value = null;
        JsonNode v = node.get(key);
        if (Objects.isNull(node) || Objects.isNull(v)) {
            if (required) {
                result.missing(location, key);
                result.invalid();
            }
        } else if (v.getNodeType().equals(JsonNodeType.NUMBER)) {
            value = new BigDecimal(v.asText());
        } else if (!v.isValueNode()) {
            result.invalidType(location, key, "double", node);
        }
        return value;
    }

    public Integer getInteger(String key, ObjectNode node, boolean required, String location, ParseResult result) {
        Integer value = null;
        JsonNode v = node.get(key);
        if (Objects.isNull(node) || Objects.isNull(v)) {
            if (required) {
                result.missing(location, key);
                result.invalid();
            }
        } else if (v.getNodeType().equals(JsonNodeType.NUMBER)) {
            if (v.isInt()) {
                value = v.intValue();
            }
        } else if (!v.isValueNode()) {
            result.invalidType(location, key, "integer", node);
        }
        return value;
    }

    public Object getAnyType(String nodeKey, ObjectNode node, String location, ParseResult result) {
        JsonNode example = node.get(nodeKey);
        if (Objects.nonNull(example)) {
            if (example.getNodeType().equals(JsonNodeType.STRING)) {
                return getString(nodeKey, node, false, location, result);
            }
            if (example.getNodeType().equals(JsonNodeType.NUMBER)) {
                Integer integerExample = getInteger(nodeKey, node, false, location, result);
                if (Objects.nonNull(integerExample)) {
                    return integerExample;
                } else {
                    BigDecimal bigDecimalExample = getBigDecimal(nodeKey, node, false, location, result);
                    if (Objects.nonNull(bigDecimalExample)) {
                        return bigDecimalExample;
                    }
                }
            } else if (example.getNodeType().equals(JsonNodeType.OBJECT)) {
                ObjectNode objectValue = getObject(nodeKey, node, false, location, result);
                if (Objects.nonNull(objectValue)) {
                    return objectValue;
                }
            } else if (example.getNodeType().equals(JsonNodeType.ARRAY)) {
                ArrayNode arrayValue = getArray(nodeKey, node, false, location, result);
                if (Objects.nonNull(arrayValue)) {
                    return arrayValue;
                }
            } else if (example.getNodeType().equals(JsonNodeType.BOOLEAN)) {
                Boolean bool = getBoolean(nodeKey, node, false, location, result);
                if (Objects.nonNull(bool)) {
                    return bool;
                }
            } else if (example.getNodeType().equals(JsonNodeType.NULL)) {
                return example;
            }
        }
        return null;
    }

    public Map<String, JsonNode> getSchemas(final ObjectNode node,
                                            final String location,
                                            final ParseResult result,
                                            final boolean underComponents) {
        if (Objects.isNull(node)) return Collections.emptyMap();

        Map<String, JsonNode> schemas = new LinkedHashMap<>();
        Set<String> schemaKeys = getKeys(node);
        for (String schemaName : schemaKeys) {
            if (underComponents) {
                if (!Pattern.matches("^[a-zA-Z0-9.\\-_]+$",
                        schemaName)) {
                    result.warning(location, "schemaName: '%s' not not adhere to regular expression ^[a-zA-Z0-9\\.\\-_]+$"
                            .formatted(schemaName));
                }
            }
            JsonNode schemaValue = node.get(schemaName);
            if (!schemaValue.getNodeType().equals(JsonNodeType.OBJECT)) {
                result.invalidType(location, schemaName, "object", schemaValue);
            } else {
                schemas.put(schemaName, schemaValue);
            }
        }

        return schemas;
    }

    @Data
    @Builder
    public static class ParseResult {

        private boolean invalid;
        private boolean mustValidate;
        private boolean allowEmptyStrings;
        private boolean oaiAuthor;
        private final Map<Location, String> invalidType = new LinkedHashMap<>();
        private final List<Location> reserved = new ArrayList<>();
        private final Map<Location, JsonNode> extra = new LinkedHashMap<>();
        private final List<Location> missing = new ArrayList<>();
        private final List<Location> warnings = new ArrayList<>();
        private final List<Location> unique = new ArrayList<>();

        public void reserved(String location, String key) {
            reserved.add(new Location(location, key));
        }

        public void extra(String location, String key, JsonNode value) {
            extra.put(new Location(location, key), value);
        }

        public void missing(String location, String key) {
            missing.add(new Location(location, key));
        }

        public void warning(String location, String key) {
            warnings.add(new Location(location, key));
        }

        public void unique(String location, String key) {
            unique.add(new Location(location, key));
        }

        public void invalidType(String location, String key, String expectedType, JsonNode value) {
            invalidType.put(new Location(location, key), expectedType);
        }

        public void invalid() {
            this.invalid = true;
        }

        // TODO refactor: redundant to ValidationResult.java
        public List<String> getMessages() {
            List<String> messages = new ArrayList<String>();
            for (Location l : extra.keySet()) {
                String location = l.location.isEmpty() ? "" : l.location + ".";
                String message = "attribute %s%s is unexpected".formatted(location, l.key);
                messages.add("Extra: %s".formatted(message));
            }
            for (Location l : invalidType.keySet()) {
                String location = l.location.isEmpty() ? "" : l.location + ".";
                String message = "attribute %s%s is not of type `%s`".formatted(location, l.key, invalidType.get(l));
                messages.add("Invalid: %s".formatted(message));
            }
            for (Location l : missing) {
                String location = l.location.isEmpty() ? "" : l.location + ".";
                String message = "attribute %s%s is missing".formatted(location, l.key);
                messages.add("Missing: %s".formatted(message));
            }
            for (Location l : warnings) {
                String location = l.location.isEmpty() ? "" : l.location + ".";
                String message = location + l.key;
                messages.add("Warning: %s".formatted(message));
            }
            for (Location l : unique) {
                String location = l.location.isEmpty() ? "" : l.location + ".";
                String message = "attribute %s%s is repeated".formatted(location, l.key);
                messages.add("Unique: %s".formatted(message));
            }
            for (Location l : reserved) {
                String location = l.location.isEmpty() ? "" : l.location + ".";
                String message = "attribute %s%s is reserved by The OpenAPI Initiative".formatted(location, l.key);
                messages.add("Reserved: %s".formatted(message));
            }
            return messages;
        }

        public record Location(String location, String key) {

            public static ValidationResult.Location of(final String location, final String key) {
                return new ValidationResult.Location(location, key);
            }
        }
    }
}
