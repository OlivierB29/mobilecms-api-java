package org.mobilecms.api.impl.admin;

import java.util.List;

import org.mobilecms.api.config.AppProperties;
import org.mobilecms.api.error.ApiException;
import org.mobilecms.api.service.AuthService;
import org.mobilecms.api.service.ContentService;
import org.mobilecms.api.service.FileService;
import org.mobilecms.api.service.ServiceResult;
import org.mobilecms.api.service.UserService;
import org.mobilecms.api.util.JsonFiles;
import org.mobilecms.generated.admin.api.AdminApi;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@RestController
public class AdminController implements AdminApi {

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

    @Override
    @GetMapping("/mobilecmsapi/v50/adminapi/content")
    public ResponseEntity<Object> types1() {
        return ResponseEntity.ok(contentService.options(properties.getPrivateDir(), "types.json"));
    }

    @Override
    @GetMapping("/mobilecmsapi/v50/adminapi/content/{type}")
    public ResponseEntity<Object> list2(@PathVariable String type) {
        return toResponse(userService.getAllUsers());
    }

    @Override
    @PostMapping("/mobilecmsapi/v50/adminapi/content/{type}")
    public ResponseEntity<Object> create(@PathVariable String type, @RequestBody Object body) {
        ObjectNode requestUser = asObjectNode(body, "create");
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
        return toResponse(ServiceResult.error(400, createResult));
    }

    @Override
    @GetMapping("/mobilecmsapi/v50/adminapi/content/{type}/{id}")
    public ResponseEntity<Object> get(@PathVariable String type, @PathVariable String id) {
        var tmp = contentService.getRecord(properties.getPrivateDir(), type, id);
        if (tmp.isOk() && tmp.getResult() instanceof ObjectNode user) {
            return ResponseEntity.ok(authService.publicAdminUser(user));
        }
        return toResponse(tmp);
    }

    @Override
    @PostMapping("/mobilecmsapi/v50/adminapi/content/{type}/{id}")
    public ResponseEntity<Object> reset(@PathVariable String type, @PathVariable String id, @RequestBody Object body) {
        ObjectNode user = asObjectNode(body, "reset");
        if (user.has("newpassword")) {
            return toResponse(authService.resetPassword(user.get("email").asText(), user.get("newpassword").asText()));
        }
        var putResponse = contentService.update(properties.getPrivateDir(), type, "email", authService.publicAdminUser(user));
        if (putResponse.isOk() && putResponse.getResult() instanceof ObjectNode saved) {
            return toResponse(contentService.publishById(
                    properties.getPrivateDir(), type, "email", saved.get("email").asText()));
        }
        return toResponse(putResponse);
    }

    @Override
    @PostMapping("/mobilecmsapi/v50/adminapi/index/{type}")
    public ResponseEntity<Object> rebuild1(@PathVariable String type) {
        return toResponse(contentService.rebuildIndex(properties.getPrivateDir(), type, "email"));
    }

    @Override
    @GetMapping("/mobilecmsapi/v50/adminapi/index/{type}")
    public ResponseEntity<Object> index1(@PathVariable String type) {
        return toResponse(contentService.getAll(properties.getPrivateDir(), type + "/index/index.json"));
    }

    @Override
    @DeleteMapping("/mobilecmsapi/v50/adminapi/content/{type}/{id}")
    public ResponseEntity<Object> delete1(@PathVariable String type, @PathVariable String id) {
        var response = contentService.deleteRecord(properties.getPrivateDir(), type, id);
        if (response.isOk()) {
            response = contentService.rebuildIndex(properties.getPrivateDir(), type, "email");
        }
        return toResponse(response);
    }

    @Override
    @GetMapping("/mobilecmsapi/v50/adminapi/metadata/{type}")
    public ResponseEntity<Object> metadata1(@PathVariable String type) {
        return ResponseEntity.ok(jsonFiles.read(contentService.getMetadataFileName(properties.getPrivateDir(), type)));
    }

    @Override
    @GetMapping("/mobilecmsapi/v50/adminapi/theme")
    public ResponseEntity<Object> getTheme() {
        var themeFile = properties.getPublicDir().resolve("theme").resolve("theme.json");
        return ResponseEntity.ok(jsonFiles.read(themeFile));
    }

    @Override
    @PostMapping("/mobilecmsapi/v50/adminapi/theme")
    public ResponseEntity<Object> postTheme(@RequestBody Object body) {
        JsonNode data = asJsonNode(body);
        if (!data.isObject()) {
            throw new IllegalArgumentException("Theme must be a JSON object");
        }
        var themeFile = properties.getPublicDir().resolve("theme").resolve("theme.json");
        jsonFiles.write(themeFile, data);
        return ResponseEntity.ok(data);
    }

    @Override
    @PostMapping("/mobilecmsapi/v50/adminapi/theme/banner")
    public ResponseEntity<Object> banner(
            @RequestPart(value = "uploadfiles", required = false) List<MultipartFile> uploadfiles,
            @RequestPart(value = "banner", required = false) MultipartFile banner) {
        MultipartFile file = banner;
        if (file == null && uploadfiles != null && !uploadfiles.isEmpty()) {
            file = uploadfiles.get(0);
        }
        return ResponseEntity.ok(fileService.uploadBanner(file));
    }

    private JsonNode asJsonNode(Object body) {
        if (body == null) {
            return jsonFiles.mapper().createObjectNode();
        }
        if (body instanceof JsonNode jsonNode) {
            return jsonNode;
        }
        return jsonFiles.mapper().valueToTree(body);
    }

    private ObjectNode asObjectNode(Object body, String action) {
        JsonNode jsonNode = asJsonNode(body);
        if (!(jsonNode instanceof ObjectNode objectNode)) {
            throw ApiException.badRequest(action + " requires a JSON object");
        }
        return objectNode;
    }

    private static ResponseEntity<Object> toResponse(ServiceResult result) {
        return ResponseEntity.status(result.getCode()).body(result.getResult());
    }
}
