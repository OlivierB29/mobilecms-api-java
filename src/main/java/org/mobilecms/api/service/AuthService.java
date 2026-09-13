package org.mobilecms.api.service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Pattern;

import org.mobilecms.api.security.JwtService;
import org.mobilecms.api.util.JsonFiles;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class AuthService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserService userService;
    private final ThrottleService throttleService;
    private final JwtService jwtService;
    private final JsonFiles jsonFiles;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);
    private final SecureRandom random = new SecureRandom();

    public AuthService(
            UserService userService,
            ThrottleService throttleService,
            JwtService jwtService,
            JsonFiles jsonFiles) {
        this.userService = userService;
        this.throttleService = throttleService;
        this.jwtService = jwtService;
        this.jsonFiles = jsonFiles;
    }

    public ServiceResult getToken(String emailParam, String password, String ip) {
        String email = emailParam == null ? "" : emailParam.toLowerCase(Locale.ROOT);
        int retryAfter = throttleService.getRetryAfter(email, ip);
        if (retryAfter > 0) {
            return ServiceResult.error(429, "Too many login attempts");
        }
        try {
            ObjectNode user = userService.getJsonUser(email);
            if (passwordEncoder.matches(password, user.get("password").asText())) {
                throttleService.clearFailedLogins(email, ip);
                String token = jwtService.createToken(
                        user.get("name").asText(),
                        user.get("email").asText(),
                        user.get("role").asText(),
                        user.get("salt").asText());
                ObjectNode response = publicUser(user);
                response.put("username", user.get("name").asText());
                if (user.has("clientalgorithm")) {
                    response.put("clientalgorithm", user.get("clientalgorithm").asText());
                }
                if (user.has("newpasswordrequired")) {
                    response.put("newpasswordrequired", user.get("newpasswordrequired").asText());
                }
                response.put("token", token);
                return ServiceResult.ok(response);
            }
            throttleService.recordFailedLogin(email, ip);
            ObjectNode partial = jsonFiles.object();
            partial.put("name", user.get("name").asText());
            partial.put("username", user.get("name").asText());
            partial.put("email", user.get("email").asText());
            return new ServiceResult(401, partial, "wrong password");
        } catch (IllegalArgumentException e) {
            throttleService.recordFailedLogin(email, ip);
            return ServiceResult.error(401, "wrong user");
        }
    }

    public String login(String emailParam, String password) {
        String email = emailParam.toLowerCase(Locale.ROOT);
        try {
            ObjectNode user = userService.getJsonUser(email);
            if (passwordEncoder.matches(password, user.get("password").asText())) {
                throttleService.archiveOldFailed(user.get("email").asText());
                return "";
            }
            return "Wrong password";
        } catch (IllegalArgumentException e) {
            return "Wrong user";
        }
    }

    public ServiceResult changePassword(String emailParam, String password, String newPassword) {
        String email = emailParam.toLowerCase(Locale.ROOT);
        if (!login(email, password).isEmpty()) {
            return ServiceResult.error(401, "wrong password");
        }
        String updateMsg = createUser("", email, newPassword, "update");
        ObjectNode user = userService.getJsonUser(email);
        user.put("clientalgorithm", "hashmacbase64");
        user.put("newpasswordrequired", "false");
        jsonFiles.write(userService.getJsonUserFile(email), user);
        if (updateMsg == null || updateMsg.isEmpty()) {
            return getPublicInfo(email);
        }
        return ServiceResult.error(500, "createUserWithSecret error " + updateMsg);
    }

    public ServiceResult resetPassword(String emailParam, String newPassword) {
        String email = emailParam.toLowerCase(Locale.ROOT);
        if (!userService.exists(email)) {
            return new ServiceResult(401, jsonFiles.object(), "Wrong login");
        }
        String updateMsg = createUser(emailParam, emailParam, newPassword, "update");
        ObjectNode user = userService.getJsonUser(email);
        user.put("clientalgorithm", "none");
        user.put("newpasswordrequired", "true");
        jsonFiles.write(userService.getJsonUserFile(email), user);
        throttleService.archiveOldFailed(emailParam);
        if (updateMsg == null || updateMsg.isEmpty()) {
            return getPublicInfo(email);
        }
        return ServiceResult.error(500, "resetPassword error " + updateMsg);
    }

    public ServiceResult getPublicInfo(String email) {
        if (!userService.exists(email)) {
            return new ServiceResult(400, jsonFiles.object());
        }
        ObjectNode user = userService.getJsonUser(email);
        ObjectNode info = jsonFiles.object();
        info.put("name", "");
        info.put("clientalgorithm", "");
        info.put("newpasswordrequired", "");
        jsonFiles.copy(user, info);
        return ServiceResult.ok(info);
    }

    public String createUser(String username, String emailParam, String password, String mode) {
        String email = emailParam == null ? "" : emailParam.toLowerCase(Locale.ROOT);
        StringBuilder error = new StringBuilder();
        if (email.isEmpty()) {
            error.append("EmptyEmail ");
        }
        if (!EMAIL.matcher(email).matches()) {
            error.append("InvalidEmail ");
        }
        if (password == null || password.isEmpty()) {
            error.append("EmptyPassword ");
        }
        if (error.isEmpty() && "create".equals(mode)) {
            if (username == null || username.isEmpty()) {
                error.append("InvalidUser ");
            }
            if (userService.exists(email)) {
                error.append("AlreadyExists.");
            }
        }
        if (!error.isEmpty()) {
            return error.toString();
        }
        byte[] saltBytes = new byte[128];
        random.nextBytes(saltBytes);
        String randomSalt = Base64.getEncoder().encodeToString(saltBytes);
        String hashed = passwordEncoder.encode(password);
        if ("create".equals(mode)) {
            userService.addDbUser(email, username, hashed, randomSalt, "guest");
        } else {
            userService.updateUser(email, "", hashed, randomSalt, "");
        }
        return null;
    }

    public String generateRandomString(int length) {
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    public ObjectNode getJsonUserFromToken(String token) {
        return userService.getJsonUser(jwtService.getSubject(token));
    }

    public boolean isPermitted(String role, String requiredRole) {
        if (role == null || role.isBlank() || requiredRole == null || requiredRole.isBlank()) {
            return false;
        }
        if ("editor".equals(requiredRole)) {
            return "editor".equals(role) || "admin".equals(role);
        }
        if ("admin".equals(requiredRole)) {
            return "admin".equals(role);
        }
        return false;
    }

    private ObjectNode publicUser(ObjectNode user) {
        ObjectNode response = jsonFiles.object();
        response.put("name", user.path("name").asText());
        response.put("email", user.path("email").asText());
        response.put("role", user.path("role").asText());
        return response;
    }

    public ObjectNode publicAdminUser(ObjectNode user) {
        return publicUser(user);
    }
}
