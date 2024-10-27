package de.leidenheit.infrastructure.validation;

import de.leidenheit.core.model.ArazzoSpecification;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ValidationResult {

    private boolean invalid;
    private final Map<Location, String> invalidTypeMap = new LinkedHashMap<>();
    private final List<Location> warningList = new ArrayList<>();
    private final List<Location> uniqueList = new ArrayList<>();
    private final List<Location> errorList = new ArrayList<>();

    private ArazzoSpecification arazzo;

    public void merge(final ValidationResult otherResult) {
        if (otherResult.invalid) {
            setInvalid(true);
        }
        this.errorList.addAll(otherResult.errorList);
        this.invalidTypeMap.putAll(otherResult.invalidTypeMap);
        this.warningList.addAll(otherResult.warningList);
        this.uniqueList.addAll(otherResult.uniqueList);
    }

    public void addInvalidType(final String location, final String key, final String expectedType) {
        invalidTypeMap.put(new Location(location, key), expectedType);
        setInvalid(true);
    }

    public void addWarning(final String location, final String key) {
        warningList.add(new Location(location, key));
    }

    public void addUnique(final String location, final String key) {
        uniqueList.add(new Location(location, key));
        setInvalid(true);
    }

    public void addError(final String location, final String key) {
        errorList.add(new Location(location, key));
        setInvalid(true);
    }

    // TODO refactor: redundant to ArazzoDeserializer.ParseResult.java
    public List<String> getMessages() {
        List<String> messages = new ArrayList<>();

        for (Map.Entry<Location, String> entry : invalidTypeMap.entrySet()) {
            var l = entry.getKey();
            String location = l.location.isEmpty() ? "" : l.location + ".";
            String message = "Invalid: attribute %s%s is not of type `%s`".formatted(location, l.key, invalidTypeMap.get(l));
            messages.add(message);
        }
        for (Location l : warningList) {
            String location = l.location.isEmpty() ? "" : l.location + ".";
            String message = location + l.key;
            messages.add("Warning: %s".formatted(message));
        }
        for (Location l : uniqueList) {
            String location = l.location.isEmpty() ? "" : l.location + ".";
            String message = "Unique: attribute %s%s is repeated".formatted(location, l.key);
            messages.add(message);
        }
        for (Location l : errorList) {
            String location = l.location.isEmpty() ? "" : l.location + ".";
            String message = location + l.key;
            messages.add("Error: %s".formatted(message));
        }
        return messages;
    }

    public record Location(String location, String key) {
        public static Location of(final String location, final String key) {
            return new Location(location, key);
        }
    }
}
