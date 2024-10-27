package de.leidenheit.infrastructure.parsing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.leidenheit.core.exception.ItarazzoIllegalStateException;
import de.leidenheit.core.exception.ItarazzoUnsupportedException;
import io.swagger.util.ObjectMapperFactory;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

@RequiredArgsConstructor
// TODO parse messages
public class Parser implements ParserExtension {

    private static final Charset ENCODING = StandardCharsets.UTF_8;
    private static final ObjectMapper JSON_MAPPER;
    private static final ObjectMapper YAML_MAPPER;

    static {
        JSON_MAPPER = ObjectMapperFactory.createJson();
        YAML_MAPPER = ObjectMapperFactory.createYaml();
    }

    @Override
    public ParseResult readLocation(final String arazzoUrl, final ParseOptions options) {
        try {
            var content = readContentFromLocation(arazzoUrl);
            return readContents(content, options, arazzoUrl);
        } catch (Exception e) {
            return ParseResult.ofError(e.getMessage());
        }
    }

    private String readContentFromLocation(final String location) {
        final String adjustedLocation = location.replace("\\\\", "/");
        try {
            final String fileScheme = "file:";
            final Path path = adjustedLocation.toLowerCase().startsWith(fileScheme) ?
                    Paths.get(URI.create(adjustedLocation)) : Paths.get(adjustedLocation);
            if (Files.exists(path)) {
                return FileUtils.readFileToString(path.toFile(), ENCODING);
            } else {
                try (var is = getClass().getClassLoader().getResourceAsStream(location)) {
                    return new String(Objects.requireNonNull(is).readAllBytes(), ENCODING);
                }
            }
        } catch (Exception e) {
            throw new ItarazzoIllegalStateException(e);
        }
    }

    @Override
    public ParseResult readContents(final String arazzoAsString, final ParseOptions options) {
        throw new ItarazzoUnsupportedException("Not yet implemented.");
    }

    private ParseResult readContents(final String arazzoAsString,
                                     final ParseOptions options,
                                     final String location) {
        if (Objects.isNull(arazzoAsString) || arazzoAsString.trim().isEmpty()) {
            return ParseResult.ofError("Null or empty definition");
        }

        try {
            ParseResult parseResult;

            final var mapper = getMapper(arazzoAsString);
            JsonNode rootNode = mapper.readTree(arazzoAsString);

            if (Objects.nonNull(options)) {
                parseResult = parseJsonNode(location, rootNode, options);
            } else {
                parseResult = parseJsonNode(location, rootNode);
            }
            return parseResult;
        } catch (Exception e) {
            var msg = String.format("location: %s; msg=%s", location, e.getMessage());
            return ParseResult.ofError(msg);
        }
    }

    private ParseResult parseJsonNode(final String path, final JsonNode node, final ParseOptions options) throws Exception {
        return new ArazzoDeserializer().deserialize(node, path, options);
    }

    private ParseResult parseJsonNode(final String path, final JsonNode node) {
        var options = ParseOptions.builder().build();
        return new ArazzoDeserializer().deserialize(node, path, options);
    }

    private ObjectMapper getMapper(final String data) {
        if (data.trim().startsWith("{")) {
            return JSON_MAPPER;
        }
        return YAML_MAPPER;
    }
}
