package com.thatrico.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class TokenService {
    private final ObjectMapper mapper = new ObjectMapper();
    private final byte[] secret = getSecret().getBytes(StandardCharsets.UTF_8);

    public String createToken(DataStore.User user) {
        try {
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("sub", user.id);
            claims.put("role", user.role);
            claims.put("email", user.email);
            claims.put("name", user.name);
            claims.put("exp", Instant.now().plusSeconds(7L * 24 * 60 * 60).getEpochSecond());

            String payloadJson = mapper.writeValueAsString(claims);
            String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
            String signature = sign(payload);
            return payload + "." + signature;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create token", exception);
        }
    }

    public Map<String, Object> verifyToken(String token) {
        try {
            if (token == null || token.isBlank()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing authorization token");
            }

            String[] parts = token.split("\\.");
            if (parts.length != 2) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token format");
            }

            String payload = parts[0];
            String signature = parts[1];
            String expected = sign(payload);

            if (!MessageDigest.isEqual(signature.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8))) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token signature");
            }

            String payloadJson = new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
            Map<String, Object> claims = mapper.readValue(payloadJson, new TypeReference<Map<String, Object>>() {});
            Number exp = (Number) claims.get("exp");
            if (exp == null || Instant.now().getEpochSecond() > exp.longValue()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token expired");
            }
            return claims;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token", exception);
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] rawSignature = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(rawSignature);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not sign token", exception);
        }
    }

    private String getSecret() {
        String secretValue = System.getenv("THATRICO_TOKEN_SECRET");
        if (secretValue == null || secretValue.isBlank()) {
            secretValue = System.getenv("JWT_SECRET");
        }
        if (secretValue == null || secretValue.isBlank()) {
            secretValue = "thatrico-dev-secret";
        }
        return secretValue;
    }
}
