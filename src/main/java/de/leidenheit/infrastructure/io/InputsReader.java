package de.leidenheit.infrastructure.io;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.leidenheit.core.exception.ItarazzoIllegalStateException;
import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.infrastructure.parsing.ComponentsReferenceResolver;
import io.swagger.util.ObjectMapperFactory;
import org.apache.commons.io.FileUtils;
import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

public class InputsReader {

    private static final ObjectMapper JSON_MAPPER;
    private static final ObjectMapper YAML_MAPPER;

    static {
        JSON_MAPPER = ObjectMapperFactory.createJson();
        YAML_MAPPER = ObjectMapperFactory.createYaml();
    }

    public static Map<String, Object> parseAndValidateInputs(
            final ArazzoSpecification arazzo,
            final JsonNode arazzoInputs,
            final JsonNode schemaNode) {
        try {
            var jsonObject = new JSONObject(arazzoInputs.toString());

            var resolvedSchemaNode = schemaNode;
            // resolve if this is a component reference
            if (resolvedSchemaNode.has("$ref")) {
                var componentsNode = JSON_MAPPER.convertValue(arazzo.getComponents(), JsonNode.class);
                var resolver = ComponentsReferenceResolver.getInstance(componentsNode);
                resolvedSchemaNode = resolver.resolveComponent(schemaNode.get("$ref").asText());
            }

            if (Objects.isNull(resolvedSchemaNode)) throw new ItarazzoIllegalStateException("Schema must not be null");

            // validate against schema
            Schema schema;
            schema = loadSchema(resolvedSchemaNode);
            schema.validate(jsonObject);

            var schemaPropertiesNode = resolvedSchemaNode.get("properties");
            if (Objects.isNull(schemaPropertiesNode)) throw new ItarazzoIllegalStateException(
                    "Invalid schema node: %s".formatted(resolvedSchemaNode));
            var inputs = filterJsonBySchema(schemaPropertiesNode, arazzoInputs);
            return JSON_MAPPER.convertValue(inputs, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new ItarazzoIllegalStateException(e);
        }
    }

    public static JsonNode filterJsonBySchema(final JsonNode jsonSchemaNode, final JsonNode jsonDataNode) {
        try {
            var filteredNode = new ObjectMapper().createObjectNode();
            var fieldNameIterator = jsonSchemaNode.fieldNames();
            while (fieldNameIterator.hasNext()) {
                var fieldName = fieldNameIterator.next();
                if (jsonDataNode.has(fieldName)) {
                    filteredNode.set(fieldName, jsonDataNode.get(fieldName));
                }
            }
            return filteredNode;
        } catch (Exception e) {
            throw new ItarazzoIllegalStateException(e);
        }
    }

    public static JsonNode readInputs(final String inputsSchemaFilePath) {
        try {
            var file = new File(inputsSchemaFilePath);
            if (file.exists()) {
                var contentAsString = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
                var mapper = getMapper(contentAsString);
                return mapper.readTree(contentAsString);
            }
            return null;
        } catch (Exception e) {
            throw new ItarazzoIllegalStateException(e);
        }
    }

    private static Schema loadSchema(final JsonNode schemaNode) {
        JSONObject rawSchema = new JSONObject(new JSONTokener(schemaNode.toString()));
        return SchemaLoader.load(rawSchema);
    }

    private static ObjectMapper getMapper(final String data) {
        if (data.trim().startsWith("{")) {
            return JSON_MAPPER;
        }
        return YAML_MAPPER;
    }

    private InputsReader() {
    }
}
