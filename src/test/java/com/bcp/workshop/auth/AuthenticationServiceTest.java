package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthenticationServiceTest {
    private final PasswordHasher hasher = new PasswordHasher();
    private final UserRecord user = new UserRecord(42, "test-user", "Test User", "TRAINER");

    @Test
    void returnsOnlyPublicUserDataForValidCredentials() {
        AuthenticationService service = serviceWith(new StoredCredentials(user, hasher.hash("test-password")));

        assertEquals(user, service.authenticate("test-user", "test-password"));
    }

    @Test
    void rejectsMissingUserAndWrongPassword() {
        assertNull(serviceWith(null).authenticate("unknown", "test-password"));
        assertNull(serviceWith(new StoredCredentials(user, hasher.hash("test-password")))
                .authenticate("test-user", "incorrect"));
    }

    @Test
    void rejectsCorruptHashWithoutPlaintextFallback() {
        assertNull(serviceWith(new StoredCredentials(user, "test-password"))
                .authenticate("test-user", "test-password"));
    }

    @Test
    void leavesUnexpectedFailuresForTheExistingResourceHandler() {
        LegacyDatabase database = new LegacyDatabase() {
            @Override
            public StoredCredentials findByUsername(String username) {
                throw new IllegalStateException("controlled test failure");
            }
        };
        AuthenticationService service = new AuthenticationService(database, hasher);

        assertThrows(IllegalStateException.class, () -> service.authenticate("test-user", "test-password"));
    }

    private AuthenticationService serviceWith(StoredCredentials credentials) {
        LegacyDatabase database = new LegacyDatabase() {
            @Override
            public StoredCredentials findByUsername(String username) {
                return credentials;
            }
        };
        return new AuthenticationService(database, hasher);
    }
}