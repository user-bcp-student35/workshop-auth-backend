package com.bcp.workshop.auth;

import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class AuthTokenService {
    static final long TOKEN_LIFETIME_SECONDS = 3600;

    private final String secret;
    private final Clock clock;

    @Inject
    public AuthTokenService(@ConfigProperty(name = "auth.token.secret", defaultValue = "") String secret,
                            Instance<Clock> clock) {
        this(secret, clock.get());
    }

    AuthTokenService(String secret, Clock clock) {
        this.secret = secret;
        this.clock = clock;
    }

    public String issue(String username, String client) {
        SecretKey signingKey = signingKey();
        Instant issuedAt = clock.instant();
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", username);
        claims.put("client", client);
        claims.put("iat", issuedAt.getEpochSecond());
        claims.put("exp", issuedAt.plusSeconds(TOKEN_LIFETIME_SECONDS).getEpochSecond());
        return Jwt.claims(claims).jws().sign(signingKey);
    }

    private SecretKey signingKey() {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("AUTH_TOKEN_SECRET must contain at least 32 bytes");
        }
        return new SecretKeySpec(secretBytes, "HmacSHA256");
    }
}