package org.mobilecms.api.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.mobilecms.api.config.AppProperties;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

@Service
public class JwtService {

    private final AppProperties properties;
    private final ObjectMapper mapper;

    public JwtService(AppProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    public String createToken(String username, String email, String role, String salt) {
        if ("php-jwt".equals(properties.getJwtImpl())) {
            return createPhpJwt(username, email, role, salt);
        }
        return createLegacyToken(username, email, role, salt);
    }

    public String getSubject(String token) {
        JsonNode payload = readPayload(token);
        if (payload == null || !payload.has("sub")) {
            throw new IllegalArgumentException("empty payload");
        }
        return payload.get("sub").asText();
    }

    public JsonNode readPayload(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }
            byte[] decoded = tryDecode(parts[1]);
            return mapper.readTree(decoded);
        } catch (Exception e) {
            throw new IllegalArgumentException("empty payload", e);
        }
    }

    public boolean verify(String token, String salt) {
        if ("php-jwt".equals(properties.getJwtImpl())) {
            return verifyPhpJwt(token, salt);
        }
        return verifyLegacy(token, salt);
    }

    private String createPhpJwt(String username, String email, String role, String salt) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(email)
                    .claim("name", username)
                    .claim("role", role)
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS512), claims);
            jwt.sign(new MACSigner(hmacKey(salt)));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create JWT", e);
        }
    }

    private boolean verifyPhpJwt(String token, String salt) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            String alg = jwt.getHeader().getAlgorithm().getName();
            if (!alg.equals("HS512") && !alg.equals("HS256") && !alg.equals("HS384")) {
                return false;
            }
            return jwt.verify(new MACVerifier(hmacKey(salt)));
        } catch (Exception e) {
            return false;
        }
    }

    private SecretKey hmacKey(String salt) {
        byte[] bytes = salt.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 64) {
            bytes = sha512(bytes);
        }
        return new SecretKeySpec(bytes, "HmacSHA512");
    }

    private byte[] sha512(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-512").digest(input);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String createLegacyToken(String username, String email, String role, String salt) {
        String header = Base64.getEncoder().encodeToString("{ \"alg\": \"HS512\",\"typ\": \"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payloadJson = "{ \"sub\": \"" + email + "\", \"name\": \"" + username + "\", \"role\": \"" + role + "\"}";
        String payload = Base64.getEncoder().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + "." + legacySignature(header, payload, salt);
    }

    private boolean verifyLegacy(String token, String salt) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return false;
        }
        String computed = legacySignature(parts[0], parts[1], salt);
        return MessageDigest.isEqual(computed.getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8));
    }

    private String legacySignature(String header, String payload, String salt) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(legacySecret(salt).getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] raw = mac.doFinal((header + "." + payload).getBytes(StandardCharsets.UTF_8));
            return toHex(raw);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    String legacySecret(String salt) {
        LocalDate now = LocalDate.now();
        int dayOfYear = now.get(ChronoField.DAY_OF_YEAR) - 1;
        return salt + now.getYear() + dayOfYear;
    }

    private byte[] tryDecode(String value) {
        try {
            return Base64.getUrlDecoder().decode(pad(value));
        } catch (IllegalArgumentException e) {
            return Base64.getDecoder().decode(pad(value));
        }
    }

    private String pad(String value) {
        int mod = value.length() % 4;
        if (mod == 0) {
            return value;
        }
        return value + "=".repeat(4 - mod);
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    public Map<String, Object> payloadMap(String username, String email, String role) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", email);
        payload.put("name", username);
        payload.put("role", role);
        return payload;
    }
}
