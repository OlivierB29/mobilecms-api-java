package org.mobilecms.api.service;

import java.util.ArrayList;
import java.util.List;

import org.mobilecms.api.util.JsonFiles;
import org.mobilecms.api.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class IdGenerator {

    private IdGenerator() {
    }

    public static List<String> getSources(JsonNode metadata, String keyname) {
        List<String> result = new ArrayList<>();
        if (metadata == null || !metadata.isArray()) {
            return result;
        }
        for (JsonNode field : metadata) {
            if (keyname.equals(JsonFiles.text(field.get("name"))) && field.has("generated")
                    && !JsonFiles.isBlank(field.get("generated"))) {
                for (String name : field.get("generated").asText().split(",")) {
                    String trimmed = name.trim();
                    if (!trimmed.isEmpty()) {
                        result.add(trimmed);
                    }
                }
                return result;
            }
        }
        return result;
    }

    public static String assignGeneratedId(String keyname, ObjectNode record, JsonNode metadata) {
        if (record.has(keyname) && !JsonFiles.isBlank(record.get(keyname))) {
            return record.get(keyname).asText();
        }
        List<String> sources = getSources(metadata, keyname);
        if (sources.isEmpty()) {
            throw new IllegalStateException("No generated ID sources available");
        }
        List<String> parts = new ArrayList<>();
        List<String> dateParts = new ArrayList<>();
        for (String field : sources) {
            if (!record.has(field) || JsonFiles.isBlank(record.get(field))) {
                continue;
            }
            String slug = slug(metadata, field, record.get(field).asText());
            if (!slug.isEmpty()) {
                if (isDate(metadata, field)) {
                    dateParts.add(slug);
                } else {
                    parts.add(slug);
                }
            }
        }
        dateParts.addAll(parts);
        if (dateParts.isEmpty()) {
            throw new IllegalStateException("No valid sources for generated ID");
        }
        String id = String.join("-", dateParts);
        record.put(keyname, id);
        return id;
    }

    public static String slug(JsonNode metadata, String fieldName, String value) {
        if (isDate(metadata, fieldName) && value.length() >= 4) {
            return StringUtils.slugify(value.substring(0, 4));
        }
        return StringUtils.slugify(value);
    }

    private static boolean isDate(JsonNode metadata, String fieldName) {
        if (metadata == null || !metadata.isArray()) {
            return false;
        }
        for (JsonNode field : metadata) {
            if (fieldName.equals(JsonFiles.text(field.get("name")))
                    && "date".equals(JsonFiles.text(field.get("editor")))) {
                return true;
            }
        }
        return false;
    }
}
