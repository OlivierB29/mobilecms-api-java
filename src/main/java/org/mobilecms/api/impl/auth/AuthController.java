package org.mobilecms.api.impl.auth;

import org.mobilecms.api.ApiConstants;
import org.mobilecms.api.config.AppProperties;
import org.mobilecms.api.error.ApiException;
import org.mobilecms.api.service.AuthService;
import org.mobilecms.api.service.MailService;
import org.mobilecms.api.service.ServiceResult;
import org.mobilecms.api.util.JsonFiles;
import org.mobilecms.generated.auth.api.AuthApi;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class AuthController implements AuthApi {

    private final AuthService authService;
    private final AppProperties properties;
    private final MailService mailService;
    private final JsonFiles jsonFiles;

    public AuthController(AuthService authService, AppProperties properties, MailService mailService, JsonFiles jsonFiles) {
        this.authService = authService;
        this.properties = properties;
        this.mailService = mailService;
        this.jsonFiles = jsonFiles;
    }

    @Override
    @PostMapping("/mobilecmsapi/v50/authapi/authenticate")
    public ResponseEntity<Object> authenticate(@RequestBody Object body) {
        JsonNode jsonBody = ensureJsonNode(body);
        if (!jsonBody.has("password")) {
            throw ApiException.unauthorized("no password data");
        }
        if (!jsonBody.has(ApiConstants.LOGIN_USER)) {
            throw ApiException.unauthorized("no user data");
        }
        return toResponse(authService.getToken(
                jsonBody.get(ApiConstants.LOGIN_USER).asText(),
                jsonBody.get("password").asText(),
                clientIp()));
    }

    @Override
    @PostMapping("/mobilecmsapi/v50/authapi/changepassword")
    public ResponseEntity<Object> changePassword(@RequestBody Object body) {
        JsonNode jsonBody = ensureJsonNode(body);
        return toResponse(authService.changePassword(
                requireUser(jsonBody),
                jsonBody.path("password").asText(),
                jsonBody.path("newpassword").asText()));
    }

    @Override
    @PostMapping("/mobilecmsapi/v50/authapi/publicinfo")
    public ResponseEntity<Object> publicInfoPost(@RequestBody Object body) {
        JsonNode jsonBody = ensureJsonNode(body);
        if (!jsonBody.has(ApiConstants.LOGIN_USER)) {
            return ResponseEntity.badRequest().body(jsonFiles.object());
        }
        return toResponse(authService.getPublicInfo(jsonBody.get(ApiConstants.LOGIN_USER).asText()));
    }

    @Override
    @GetMapping("/mobilecmsapi/v50/authapi/publicinfo")
    public ResponseEntity<Object> publicInfo() {
        return ResponseEntity.ok(jsonFiles.object());
    }

    @Override
    @PostMapping("/mobilecmsapi/v50/authapi/resetpassword")
    public ResponseEntity<Object> resetPassword(@RequestBody Object body) {
        JsonNode jsonBody = ensureJsonNode(body);
        String clearPassword = authService.generateRandomString(20);
        ServiceResult result = authService.resetPassword(requireUser(jsonBody), clearPassword);
        if (result.isOk()) {
            String email = requireUser(jsonBody);
            String from = properties.getString("mailsender");
            String date = mailService.now();
            String html = mailService.renderHtml("new password", clearPassword, clientInfoForCurrentRequest(), date);
            String text = mailService.renderText("new password", clearPassword, clientInfoForCurrentRequest(), date);
            if (properties.getBoolean("enablemail", true)) {
                mailService.send(from, email, "new password", html, text);
            } else if (properties.getBoolean("debugnotifications", false) && result.getResult() instanceof com.fasterxml.jackson.databind.node.ObjectNode object) {
                object.put("notification", jsonFiles.mapper().valueToTree(text).toString());
            }
        }
        return toResponse(result);
    }

    private JsonNode ensureJsonNode(Object body) {
        if (body == null) {
            return jsonFiles.mapper().createObjectNode();
        }
        if (body instanceof JsonNode jsonNode) {
            return jsonNode;
        }
        return jsonFiles.mapper().valueToTree(body);
    }

    private String requireUser(JsonNode body) {
        if (!body.has(ApiConstants.LOGIN_USER)) {
            throw ApiException.unauthorized("no user data");
        }
        return body.get(ApiConstants.LOGIN_USER).asText();
    }

    private String clientIp() {
        HttpServletRequest request = currentRequest();
        return request == null ? "UNKNOWN" : clientIp(request);
    }

    private static String clientIp(HttpServletRequest request) {
        for (String header : new String[] {"X-Forwarded-For", "X-Real-IP"}) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank()) {
                return value.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr() == null ? "UNKNOWN" : request.getRemoteAddr();
    }

    private String clientInfoForCurrentRequest() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return "UNKNOWN";
        }
        return clientIp(request) + " " + (request.getHeader("User-Agent") == null ? "" : request.getHeader("User-Agent"));
    }

    private static HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest();
    }

    private static ResponseEntity<Object> toResponse(ServiceResult result) {
        return ResponseEntity.status(result.getCode()).body(result.getResult());
    }
}
