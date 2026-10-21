package com.bcp.workshop.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthTokenServiceTest {
    private static final String SECRET = "test-only-token-secret-with-at-least-32-bytes";
    private static final Instant ISSUED_AT = Instant.parse("2026-10-21T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(ISSUED_AT, ZoneOffset.UTC);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void issueCreatesHs256TokenWithRequiredClaimsAndFixedLifetime() throws Exception {
        String token = new AuthTokenService(SECRET, FIXED_CLOCK).issue("student", "angular-workshop");
        String[] segments = token.split("\\.", -1);

        assertEquals(3, segments.length);
        JsonNode header = OBJECT_MAPPER.readTree(Base64.getUrlDecoder().decode(segments[0]));
        JsonNode claims = OBJECT_MAPPER.readTree(Base64.getUrlDecoder().decode(segments[1]));
        assertEquals("HS256", header.get("alg").asText());
        assertEquals("student", claims.get("sub").asText());
        assertEquals("angular-workshop", claims.get("client").asText());
        assertEquals(ISSUED_AT.getEpochSecond(), claims.get("iat").asLong());
        assertEquals(3600, claims.get("exp").asLong() - claims.get("iat").asLong());
        assertFalse(claims.has("role"));
    }

    @Test
    void issueRejectsMissingOrShortSecretWithoutRevealingIt() {
        assertIssueRejected("");
        assertIssueRejected("short-lab-secret");
    }

    private static void assertIssueRejected(String secret) {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new AuthTokenService(secret, FIXED_CLOCK).issue("student", "angular-workshop"));

        assertTrue(error.getMessage() == null || !error.getMessage().contains(secret));
    }
}
