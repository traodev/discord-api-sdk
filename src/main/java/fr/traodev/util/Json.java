package fr.traodev.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;

public final class Json {
    public static final ObjectMapper MAPPER = JsonMapper.builder()
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .serializationInclusion(JsonInclude.Include.NON_NULL)
        .build();

    private Json() {
    }

    public static ObjectNode object() {
        return MAPPER.createObjectNode();
    }

    public static ArrayNode array() {
        return MAPPER.createArrayNode();
    }

    public static JsonNode parse(byte[] bytes) {
        try {
            return MAPPER.readTree(bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static JsonNode parse(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] toBytes(Object value) {
        try {
            return MAPPER.writeValueAsBytes(value);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String toString(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static <T> T convert(JsonNode node, Class<T> type) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        try {
            return MAPPER.treeToValue(node, type);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
