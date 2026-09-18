package org.mobilecms.api.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;

@Component
public class AppProperties {

    private static final Pattern SIZE = Pattern.compile("^\\s*(\\d+(?:\\.\\d+)?)\\s*(B|KB|MB|GB)?\\s*$", Pattern.CASE_INSENSITIVE);

    private final ObjectMapper mapper;
    private final String configuredRoot;
    private final String confFile;

    private Path rootDir;
    private JsonNode conf;

    public AppProperties(
            ObjectMapper mapper,
            @Value("${mobilecms.root-dir:webhost}") String configuredRoot,
            @Value("${mobilecms.conf-file:webhost/private/conf/conf.json}") String confFile) {
        this.mapper = mapper;
        this.configuredRoot = configuredRoot;
        this.confFile = confFile;
    }

    @PostConstruct
    public void load() {
        rootDir = Path.of(configuredRoot).toAbsolutePath().normalize();
        Path file = Path.of(confFile).toAbsolutePath().normalize();
      /*   if (!file.isAbsolute()) {
            file = rootDir.resolve(confFile);
        }*/
        if (!Files.exists(file)) {
            throw new IllegalStateException("conf file not found " + file);
        }
        try {
            conf = mapper.readTree(Files.readString(file));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read conf " + file, e);
        }
    }

    public Path getRootDir() {
        return rootDir;
    }

    public JsonNode getConf() {
        return conf;
    }

    public String getString(String key) {
        JsonNode node = conf.get(key);
        return node == null || node.isNull() ? "" : node.asText();
    }

    public String getString(String key, String defaultValue) {
        String value = getString(key);
        return value.isEmpty() ? defaultValue : value;
    }

    public int getInteger(String key, int defaultValue) {
        JsonNode node = conf.get(key);
        if (node == null || node.isNull() || node.asText().isEmpty()) {
            return defaultValue;
        }
        return node.isNumber() ? node.intValue() : Integer.parseInt(node.asText());
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        JsonNode node = conf.get(key);
        if (node == null || node.isNull()) {
            return defaultValue;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        String text = node.asText();
        if ("true".equals(text)) {
            return true;
        }
        if ("false".equals(text)) {
            return false;
        }
        return defaultValue;
    }

    public List<Integer> getIntegerArray(String key) {
        List<Integer> result = new ArrayList<>();
        JsonNode node = conf.get(key);
        if (node != null && node.isArray()) {
            node.forEach(item -> result.add(item.intValue()));
        }
        return result;
    }

    public List<String> getStringArray(String key) {
        List<String> result = new ArrayList<>();
        JsonNode node = conf.get(key);
        if (node != null && node.isArray()) {
            node.forEach(item -> result.add(item.asText()));
        }
        return result;
    }

    public Path getPublicDir() {
        return concat(rootDir, getString("publicdir"));
    }

    public Path getPrivateDir() {
        return concat(rootDir, getString("privatedir"));
    }

    public Path getMediaDir() {
        Path configuredMediaDir = concat(rootDir, getString("media"));
        if (Files.isDirectory(configuredMediaDir)) {
            return configuredMediaDir;
        }
        Path webMediaDir = rootDir.resolve("www").resolve("media");
        if (Files.isDirectory(webMediaDir)) {
            return webMediaDir;
        }
        return configuredMediaDir;
    }

    public Path getUsersDir() {
        return getPrivateDir().resolve("users");
    }

    public String getJwtImpl() {
        return getString("jwt", "php-jwt");
    }

    public long getUploadMaxFileSize() {
        return parseFileSize(conf.has("uploadmaxfilesize") ? conf.get("uploadmaxfilesize").asText() : "0");
    }

    public static Path concat(Path base, String relative) {
        if (relative == null || relative.isBlank()) {
            return base;
        }
        Path extra = Path.of(relative);
        if (extra.isAbsolute()) {
            return extra.normalize();
        }
        return base.resolve(relative.replaceFirst("^/+", "")).normalize();
    }

    public static long parseFileSize(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        if (value.chars().allMatch(Character::isDigit)) {
            return Long.parseLong(value);
        }
        Matcher matcher = SIZE.matcher(value);
        if (!matcher.matches()) {
            return 0;
        }
        double amount = Double.parseDouble(matcher.group(1));
        String unit = matcher.group(2) == null ? "B" : matcher.group(2).toUpperCase(Locale.ROOT);
        int power = switch (unit) {
            case "KB" -> 1;
            case "MB" -> 2;
            case "GB" -> 3;
            default -> 0;
        };
        return Math.round(amount * Math.pow(1024, power));
    }
}
