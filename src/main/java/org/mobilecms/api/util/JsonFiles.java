package org.mobilecms.api.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

@Component
public class JsonFiles {

    private final ObjectMapper mapper;

    public JsonFiles(ObjectMapper mapper) {
        this.mapper = mapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public JsonNode read(Path file) {
        try {
            return mapper.readTree(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read JSON file " + file, e);
        }
    }

    public void write(Path file, Object data) {
        try {
            Files.createDirectories(file.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), data);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write JSON file " + file, e);
        }
    }

    public JsonNode getByKey(ArrayNode data, String name, String value) {
        JsonNode result = null;
        if (name != null && !name.isEmpty() && value != null && !value.isEmpty()) {
            for (JsonNode element : data) {
                if (element.has(name) && value.equals(text(element.get(name)))) {
                    result = element;
                }
            }
        }
        return result;
    }

    public int getIndexByKey(ArrayNode data, String name, String value) {
        int result = -1;
        if (name != null && !name.isEmpty() && value != null && !value.isEmpty()) {
            int pos = 0;
            for (JsonNode element : data) {
                if (element.has(name) && value.equals(text(element.get(name)))) {
                    result = pos;
                }
                pos++;
            }
        }
        return result;
    }

    public ArrayNode put(ArrayNode data, String name, ObjectNode item) {
        int existing = getIndexByKey(data, name, text(item.get(name)));
        if (existing != -1) {
            data.remove(existing);
        }
        data.add(item);
        return data;
    }

    public void copy(JsonNode source, ObjectNode dest) {
        Iterator<String> names = dest.fieldNames();
        List<String> keys = new ArrayList<>();
        names.forEachRemaining(keys::add);
        for (String key : keys) {
            if (source.has(key)) {
                dest.set(key, source.get(key).deepCopy());
            }
        }
    }

    public void replace(JsonNode source, ObjectNode dest) {
        source.fields().forEachRemaining(entry -> dest.set(entry.getKey(), entry.getValue().deepCopy()));
    }

    public ObjectNode object() {
        return mapper.createObjectNode();
    }

    public ArrayNode array() {
        return mapper.createArrayNode();
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    public static String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        return node.asText();
    }

    public static boolean isBlank(JsonNode node) {
        return node == null || node.isNull() || (node.isTextual() && node.asText().isEmpty());
    }
}
