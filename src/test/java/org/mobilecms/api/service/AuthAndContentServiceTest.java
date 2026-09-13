package org.mobilecms.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mobilecms.api.config.AppProperties;
import org.mobilecms.api.security.JwtService;
import org.mobilecms.api.util.JsonFiles;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class AuthAndContentServiceTest {

    @TempDir
    Path temp;

    private AppProperties properties;
    private JsonFiles jsonFiles;
    private AuthService authService;
    private ContentService contentService;
    private FileService fileService;

    @BeforeEach
    void setUp() throws Exception {
        Path root = temp.resolve("www");
        Path priv = temp.resolve("private");
        Files.createDirectories(root.resolve("public/news/index"));
        Files.createDirectories(root.resolve("media"));
        Files.createDirectories(priv.resolve("users"));
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode conf = mapper.createObjectNode();
        conf.put("publicdir", "public");
        conf.put("privatedir", priv.toString());
        conf.put("media", "media");
        conf.put("jwt", "php-jwt");
        conf.put("enablemail", "false");
        conf.putArray("thumbnailsizes").add(300);
        conf.putArray("fileextensions").add("png");
        conf.putArray("mimetypes").add("image/png");
        conf.put("uploadmaxfilesize", "1MB");
        Path confFile = temp.resolve("conf.json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(confFile.toFile(), conf);

        jsonFiles = new JsonFiles(mapper);
        properties = new AppProperties(mapper, root.toString(), confFile.toString());
        properties.load();
        UserService userService = new UserService(properties, jsonFiles);
        ThrottleService throttle = new ThrottleService(properties, jsonFiles);
        JwtService jwt = new JwtService(properties, mapper);
        authService = new AuthService(userService, throttle, jwt, jsonFiles);
        ImageService images = new ImageService(jsonFiles);
        fileService = new FileService(properties, jsonFiles, images, null);
        contentService = new ContentService(jsonFiles, properties, fileService);

        jsonFiles.write(root.resolve("public/news/index/index_template.json"), mapper.readTree("{\"id\":\"\",\"title\":\"\"}"));
        jsonFiles.write(root.resolve("public/news/index/metadata.json"), mapper.readTree("[{\"name\":\"id\",\"generated\":\"title\"}]"));
        jsonFiles.write(root.resolve("public/news/index/conf.json"), mapper.readTree("{\"sortby\":\"title\",\"sortdirection\":\"asc\"}"));
        jsonFiles.write(root.resolve("public/types.json"), mapper.readTree("[{\"type\":\"news\",\"labels\":[{\"i18n\":\"en\",\"label\":\"News\"}]}]"));
    }

    @Test
    void authenticateIssuesJwt() {
        assertTrue(authService.createUser("Editor", "editor@example.com", "Sample#123456", "create") == null);
        ServiceResult result = authService.getToken("editor@example.com", "Sample#123456", "127.0.0.1");
        assertEquals(200, result.getCode());
        ObjectNode user = (ObjectNode) result.getResult();
        assertEquals("editor@example.com", user.get("email").asText());
        assertTrue(user.get("token").asText().length() > 20);
    }

    @Test
    void postCreatesRecordAndIndex() {
        ObjectMapper mapper = jsonFiles.mapper();
        ObjectNode record = mapper.createObjectNode();
        record.put("title", "Hello world");
        ServiceResult saved = contentService.post(properties.getPublicDir(), "news", "id", record);
        assertEquals(200, saved.getCode());
        String id = ((ObjectNode) saved.getResult()).get("id").asText();
        assertEquals("hello-world", id);
        contentService.publishById(properties.getPublicDir(), "news", "id", id);
        ServiceResult fetched = contentService.getRecord(properties.getPublicDir(), "news", id);
        assertEquals(200, fetched.getCode());
        ServiceResult index = contentService.getAllObjectsFromIndexByType(properties.getPublicDir(), "news");
        assertEquals(200, index.getCode());
    }

    @Test
    void wrongPasswordIsUnauthorized() {
        authService.createUser("Editor", "editor@example.com", "Sample#123456", "create");
        ServiceResult result = authService.getToken("editor@example.com", "nope", "127.0.0.1");
        assertEquals(401, result.getCode());
    }
}
