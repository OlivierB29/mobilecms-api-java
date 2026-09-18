package org.mobilecms.api.security;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.mobilecms.api.ApiConstants;
import org.mobilecms.api.service.AuthService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Pattern BEARER = Pattern.compile("Bearer\\s+(.*)$", Pattern.CASE_INSENSITIVE);
    private static final List<String> PUBLIC_PREFIXES = List.of(
            ApiConstants.API + "/authapi",
            ApiConstants.API + "/webapi",
            ApiConstants.API + "/agenda",
            ApiConstants.API + "/cmsapi/status",
            "/media",
            "/v3/api-docs",
            "/swagger-ui",
            "/swagger-ui.html",
            "/webjars");

    private final AuthService authService;
    private final JwtService jwtService;

    public JwtAuthenticationFilter(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        if (path.equals("/") || path.equals(ApiConstants.API) || path.equals(ApiConstants.API + "/")) {
            return true;
        }
        return PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String token = fetchToken(request);
            ObjectNode user = authService.getJsonUserFromToken(token);
            if (!jwtService.verify(token, user.get("salt").asText())) {
                unauthorized(response, "verifyToken false");
                return;
            }
            String role = user.get("role").asText();
            String path = request.getRequestURI();
            if (path.startsWith(ApiConstants.API + "/adminapi") && !authService.isPermitted(role, "admin")) {
                forbidden(response, "operation not permitted");
                return;
            }
            if ((path.startsWith(ApiConstants.API + "/cmsapi") || path.startsWith(ApiConstants.API + "/fileapi"))
                    && !authService.isPermitted(role, "editor")) {
                forbidden(response, "operation not permitted");
                return;
            }
            CmsUserDetails details = new CmsUserDetails(user.get("email").asText(), role, user.get("salt").asText());
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(details, token, details.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (RuntimeException ex) {
            unauthorized(response, ex.getMessage() == null ? "Token not found." : ex.getMessage());
        }
    }

    private String fetchToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null) {
            Matcher matcher = BEARER.matcher(header);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        if (request.getCookies() != null) {
            for (var cookie : request.getCookies()) {
                if ("token".equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                    return cookie.getValue();
                }
            }
        }
        throw new IllegalArgumentException("Token not found.");
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        write(response, 401, message);
    }

    private void forbidden(HttpServletResponse response, String message) throws IOException {
        write(response, 403, message);
    }

    private void write(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + message.replace("\"", "'") + "\"}");
    }
}
