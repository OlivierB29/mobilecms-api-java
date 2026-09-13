package org.mobilecms.api.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import org.mobilecms.api.config.AppProperties;
import org.mobilecms.api.util.JsonFiles;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class ThrottleService {

    private static final NavigableMap<Integer, Integer> LOCKOUTS = new TreeMap<>();

    static {
        LOCKOUTS.put(5, 60);
        LOCKOUTS.put(10, 300);
        LOCKOUTS.put(20, 1800);
    }

    private final AppProperties properties;
    private final JsonFiles jsonFiles;

    public ThrottleService(AppProperties properties, JsonFiles jsonFiles) {
        this.properties = properties;
        this.jsonFiles = jsonFiles;
    }

    public Path getLoginHistoryFileName(String user, String ip) {
        Path historyDir = properties.getUsersDir().resolve("history");
        if (ip != null) {
            String digest = sha256(user.toLowerCase(Locale.ROOT) + "|" + ip);
            return historyDir.resolve(digest + ".json");
        }
        return historyDir.resolve(user + ".json");
    }

    public int getRetryAfter(String user, String ip) {
        List<ObjectNode> failedList = getFailedLoginList(user, ip);
        int count = failedList.size();
        int duration = 0;
        for (Map.Entry<Integer, Integer> entry : LOCKOUTS.entrySet()) {
            if (count >= entry.getKey()) {
                duration = entry.getValue();
            }
        }
        if (duration == 0 || count == 0) {
            return 0;
        }
        long lastFailure = failedList.get(count - 1).get("timestamp").asLong();
        long retryAfter = lastFailure + duration - Instant.now().getEpochSecond();
        return (int) Math.max(0, retryAfter);
    }

    public int recordFailedLogin(String user, String ip) {
        Path file = getLoginHistoryFileName(user, ip);
        ObjectNode history = readHistory(file);
        ArrayNode failedList = failedArray(history);
        failedList.add(createFailedLoginRecord(user, ip));
        history.set("failed", failedList);
        jsonFiles.write(file, history);
        return failedList.size();
    }

    public void clearFailedLogins(String user, String ip) {
        Path file = getLoginHistoryFileName(user, ip);
        if (Files.exists(file)) {
            ObjectNode history = jsonFiles.object();
            history.set("failed", jsonFiles.array());
            jsonFiles.write(file, history);
        }
    }

    public int archiveOldFailed(String user) {
        Path file = getLoginHistoryFileName(user, null);
        ObjectNode history = readHistory(file);
        ArrayNode failedList = failedArray(history);
        history.set("failed", jsonFiles.array());
        if (failedList.isEmpty()) {
            String key = "archive" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.systemDefault())
                    .format(Instant.now());
            history.set(key, failedList);
        }
        jsonFiles.write(file, history);
        return failedList.size();
    }

    public ObjectNode createFailedLoginRecord(String user, String ip) {
        ObjectNode result = jsonFiles.object();
        result.put("date", DateTimeFormatter.ofPattern("EEE MMM dd yyyy H:mm", Locale.ENGLISH)
                .withZone(ZoneId.systemDefault())
                .format(Instant.now()));
        result.put("timestamp", Instant.now().getEpochSecond());
        result.put("ip", ip == null ? "UNKNOWN" : ip);
        return result;
    }

    private List<ObjectNode> getFailedLoginList(String user, String ip) {
        ObjectNode history = readHistory(getLoginHistoryFileName(user, ip));
        List<ObjectNode> result = new ArrayList<>();
        for (JsonNode failed : failedArray(history)) {
            if (failed.has("timestamp")) {
                result.add((ObjectNode) failed);
            }
        }
        return result;
    }

    private ObjectNode readHistory(Path file) {
        if (!Files.exists(file)) {
            return jsonFiles.object();
        }
        JsonNode history = jsonFiles.read(file);
        return history.isObject() ? (ObjectNode) history : jsonFiles.object();
    }

    private ArrayNode failedArray(ObjectNode history) {
        JsonNode failed = history.get("failed");
        if (failed != null && failed.isArray()) {
            return (ArrayNode) failed;
        }
        ArrayNode array = jsonFiles.array();
        history.set("failed", array);
        return array;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : digest) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
