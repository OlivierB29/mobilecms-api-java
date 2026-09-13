package org.mobilecms.api.web;

import java.util.Map;

import org.mobilecms.api.ApiConstants;
import org.mobilecms.api.config.AppProperties;
import org.mobilecms.api.error.ApiException;
import org.mobilecms.api.service.AuthService;
import org.mobilecms.api.service.MailService;
import org.mobilecms.api.service.ServiceResult;
import org.mobilecms.api.util.JsonFiles;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping(ApiConstants.API + "/authapi")
public class AuthController {

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

    @GetMapping("/publicinfo")
    public ResponseEntity<Object> publicInfo() {
        return ResponseEntity.ok(jsonFiles.object());
    }

    @PostMapping("/publicinfo")
    public ResponseEntity<Object> publicInfoPost(@RequestBody JsonNode body) {
        if (!body.has(ApiConstants.LOGIN_USER)) {
            return ResponseEntity.badRequest().body(jsonFiles.object());
        }
        return toResponse(authService.getPublicInfo(body.get(ApiConstants.LOGIN_USER).asText()));
    }

    @PostMapping("/authenticate")
    public ResponseEntity<Object> authenticate(@RequestBody JsonNode body, HttpServletRequest request) {
        if (!body.has("password")) {
            throw ApiException.unauthorized("no password data");
        }
        if (!body.has(ApiConstants.LOGIN_USER)) {
            throw ApiException.unauthorized("no user data");
        }
        return toResponse(authService.getToken(
                body.get(ApiConstants.LOGIN_USER).asText(),
                body.get("password").asText(),
                clientIp(request)));
    }

    @PostMapping("/changepassword")
    public ResponseEntity<Object> changePassword(@RequestBody JsonNode body) {
        return toResponse(authService.changePassword(
                requireUser(body),
                body.path("password").asText(),
                body.path("newpassword").asText()));
    }

    @PostMapping("/resetpassword")
    public ResponseEntity<Object> resetPassword(@RequestBody JsonNode body, HttpServletRequest request) {
        String clearPassword = authService.generateRandomString(20);
        ServiceResult result = authService.resetPassword(requireUser(body), clearPassword);
        if (result.isOk()) {
            String email = requireUser(body);
            String from = properties.getString("mailsender");
            String date = mailService.now();
            String html = mailService.renderHtml("new password", clearPassword, clientInfo(request), date);
            String text = mailService.renderText("new password", clearPassword, clientInfo(request), date);
            if (properties.getBoolean("enablemail", true)) {
                mailService.send(from, email, "new password", html, text);
            } else if (properties.getBoolean("debugnotifications", false) && result.getResult() instanceof com.fasterxml.jackson.databind.node.ObjectNode object) {
                object.put("notification", jsonFiles.mapper().valueToTree(text).toString());
            }
        }
        return toResponse(result);
    }

    private String requireUser(JsonNode body) {
        if (!body.has(ApiConstants.LOGIN_USER)) {
            throw ApiException.unauthorized("no user data");
        }
        return body.get(ApiConstants.LOGIN_USER).asText();
    }

    static ResponseEntity<Object> toResponse(ServiceResult result) {
        return ResponseEntity.status(result.getCode()).body(result.getResult());
    }

    static String clientIp(HttpServletRequest request) {
        for (String header : new String[] {"X-Forwarded-For", "X-Real-IP"}) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank()) {
                return value.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr() == null ? "UNKNOWN" : request.getRemoteAddr();
    }

    private String clientInfo(HttpServletRequest request) {
        String agent = request.getHeader("User-Agent");
        return clientIp(request) + " " + (agent == null ? "" : agent);
    }
}
