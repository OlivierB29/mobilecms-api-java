package org.mobilecms.api.web;

import org.mobilecms.api.ApiConstants;
import org.mobilecms.api.config.AppProperties;
import org.mobilecms.api.service.AuthService;
import org.mobilecms.api.service.ContentService;
import org.mobilecms.api.service.FileService;
import org.mobilecms.api.service.UserService;
import org.mobilecms.api.util.JsonFiles;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@RestController
@RequestMapping(ApiConstants.API + "/adminapi")
public class AdminController {

    private final ContentService contentService;
    private final AuthService authService;
    private final UserService userService;
    private final FileService fileService;
    private final AppProperties properties;
    private final JsonFiles jsonFiles;

    public AdminController(
            ContentService contentService,
            AuthService authService,
            UserService userService,
            FileService fileService,
            AppProperties properties,
            JsonFiles jsonFiles) {
        this.contentService = contentService;
        this.authService = authService;
        this.userService = userService;
        this.fileService = fileService;
        this.properties = properties;
        this.jsonFiles = jsonFiles;
    }

    @GetMapping("/content")
    public ResponseEntity<Object> types() {
        return ResponseEntity.ok(contentService.options(properties.getPrivateDir(), "types.json"));
    }

    @GetMapping("/content/{type}")
    public ResponseEntity<Object> list(@PathVariable String type) {
        return AuthController.toResponse(userService.getAllUsers());
    }

    @PostMapping("/content/{type}")
    public ResponseEntity<Object> create(@PathVariable String type, @RequestBody ObjectNode requestUser) {
        ObjectNode user = jsonFiles.object();
        user.put("name", "");
        user.put("email", "");
        user.put("password", "");
        jsonFiles.copy(requestUser, user);
        String createResult = authService.createUser(
                user.path("name").asText(),
                user.path("email").asText(),
                user.path("password").asText(),
                "create");
        if (createResult == null || createResult.isEmpty()) {
            contentService.publishById(properties.getPrivateDir(), type, "email", user.get("email").asText());
            return ResponseEntity.ok(jsonFiles.object());
        }
        return AuthController.toResponse(org.mobilecms.api.service.ServiceResult.error(400, createResult));
    }

    @GetMapping("/content/{type}/{id}")
    public ResponseEntity<Object> get(@PathVariable String type, @PathVariable String id) {
        var tmp = contentService.getRecord(properties.getPrivateDir(), type, id);
        if (tmp.isOk() && tmp.getResult() instanceof ObjectNode user) {
            return ResponseEntity.ok(authService.publicAdminUser(user));
        }
        return AuthController.toResponse(tmp);
    }

    @PostMapping("/content/{type}/{id}")
    public ResponseEntity<Object> reset(@PathVariable String type, @PathVariable String id, @RequestBody ObjectNode user) {
        if (user.has("newpassword")) {
            return AuthController.toResponse(authService.resetPassword(user.get("email").asText(), user.get("newpassword").asText()));
        }
        var putResponse = contentService.update(properties.getPrivateDir(), type, "email", authService.publicAdminUser(user));
        if (putResponse.isOk() && putResponse.getResult() instanceof ObjectNode saved) {
            return AuthController.toResponse(contentService.publishById(
                    properties.getPrivateDir(), type, "email", saved.get("email").asText()));
        }
        return AuthController.toResponse(putResponse);
    }

    @DeleteMapping("/content/{type}/{id}")
    public ResponseEntity<Object> delete(@PathVariable String type, @PathVariable String id) {
        var response = contentService.deleteRecord(properties.getPrivateDir(), type, id);
        if (response.isOk()) {
            response = contentService.rebuildIndex(properties.getPrivateDir(), type, "email");
        }
        return AuthController.toResponse(response);
    }

    @GetMapping("/metadata/{type}")
    public ResponseEntity<Object> metadata(@PathVariable String type) {
        return ResponseEntity.ok(jsonFiles.read(contentService.getMetadataFileName(properties.getPrivateDir(), type)));
    }

    @GetMapping("/theme")
    public ResponseEntity<Object> getTheme() {
        var themeFile = properties.getPublicDir().resolve("theme").resolve("theme.json");
        return ResponseEntity.ok(jsonFiles.read(themeFile));
    }

    @PostMapping("/theme")
    public ResponseEntity<Object> postTheme(@RequestBody JsonNode data) {
        if (!data.isObject()) {
            throw new IllegalArgumentException("Theme must be a JSON object");
        }
        var themeFile = properties.getPublicDir().resolve("theme").resolve("theme.json");
        jsonFiles.write(themeFile, data);
        return ResponseEntity.ok(data);
    }

    @PostMapping("/theme/banner")
    public ResponseEntity<Object> banner(
            @RequestPart(value = "uploadfiles", required = false) MultipartFile[] uploadfiles,
            @RequestPart(value = "banner", required = false) MultipartFile banner) {
        MultipartFile file = banner;
        if (file == null && uploadfiles != null && uploadfiles.length > 0) {
            file = uploadfiles[0];
        }
        return ResponseEntity.ok(fileService.uploadBanner(file));
    }

    @GetMapping("/index/{type}")
    public ResponseEntity<Object> index(@PathVariable String type) {
        return AuthController.toResponse(contentService.getAll(properties.getPrivateDir(), type + "/index/index.json"));
    }

    @PostMapping("/index/{type}")
    public ResponseEntity<Object> rebuild(@PathVariable String type) {
        return AuthController.toResponse(contentService.rebuildIndex(properties.getPrivateDir(), type, "email"));
    }
}
