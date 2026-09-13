package org.mobilecms.api.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.mobilecms.api.config.AppProperties;
import org.mobilecms.api.util.JsonFiles;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class UserService {

    private final AppProperties properties;
    private final JsonFiles jsonFiles;

    public UserService(AppProperties properties, JsonFiles jsonFiles) {
        this.properties = properties;
        this.jsonFiles = jsonFiles;
    }

    public Path getJsonUserFile(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("getJsonUserFile()  empty email");
        }
        return properties.getUsersDir().resolve(email.toLowerCase(Locale.ROOT) + ".json");
    }

    public ObjectNode getJsonUser(String email) {
        Path file = getJsonUserFile(email);
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("user not found:" + email);
        }
        JsonNode user = jsonFiles.read(file);
        if (!user.has("email") || !user.has("password")) {
            throw new IllegalStateException("empty user ");
        }
        return (ObjectNode) user;
    }

    public boolean exists(String email) {
        return Files.exists(getJsonUserFile(email));
    }

    public void addDbUser(String email, String name, String password, String salt, String role) {
        ObjectNode user = jsonFiles.object();
        user.put("name", name);
        user.put("email", email);
        user.put("password", password);
        user.put("salt", salt);
        user.put("role", role);
        jsonFiles.write(getJsonUserFile(email), user);
    }

    public boolean updateUser(String email, String name, String password, String salt, String role) {
        ObjectNode user = getJsonUser(email);
        if (!name.isEmpty()) {
            user.put("name", name);
        }
        if (!password.isEmpty()) {
            user.put("password", password);
        }
        if (!salt.isEmpty()) {
            user.put("salt", salt);
        }
        if (!role.isEmpty()) {
            user.put("role", role);
        }
        jsonFiles.write(getJsonUserFile(email), user);
        return true;
    }

    public ServiceResult getAllUsers() {
        ArrayNode list = jsonFiles.array();
        Path dir = properties.getUsersDir();
        if (Files.isDirectory(dir)) {
            try (Stream<Path> stream = Files.list(dir)) {
                List<Path> files = new ArrayList<>();
                stream.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                        .forEach(files::add);
                for (Path file : files) {
                    ObjectNode item = jsonFiles.object();
                    item.put("filename", file.getFileName().toString());
                    item.put("email", file.getFileName().toString().replace(".json", ""));
                    list.add(item);
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        ObjectNode result = jsonFiles.object();
        result.set("list", list);
        return ServiceResult.ok(result);
    }
}
