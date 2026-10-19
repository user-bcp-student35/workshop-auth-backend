package com.bcp.workshop.auth;

import com.password4j.Argon2Function;
import com.password4j.Password;
import com.password4j.types.Argon2;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {
    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void usesIndependentSaltsAndSelfContainedArgon2id() {
        String first = hasher.hash("isolated-test-password");
        String second = hasher.hash("isolated-test-password");

        assertNotEquals(first, second);
        assertTrue(first.startsWith("$argon2id$v=19$m=19456,t=2,p=1$"));
        assertTrue(hasher.verify("isolated-test-password", first));
        assertTrue(hasher.verify("isolated-test-password", second));
        assertTrue(Argon2Function.getInstanceFromHash(first).check("isolated-test-password", first));
    }

    @Test
    void verifiesTheEntireLongUnicodePassword() {
        String prefix = "long-password-".repeat(10) + "\u00f1\u6f22";
        String encoded = hasher.hash(prefix + "A");

        assertTrue(hasher.verify(prefix + "A", encoded));
        assertFalse(hasher.verify(prefix + "B", encoded));
        assertFalse(hasher.verify(prefix, encoded));
    }

    @Test
    void preservesUnicodeAndWhitespace() {
        String encoded = hasher.hash(" Caf\u00e9-password ");

        assertTrue(hasher.verify(" Caf\u00e9-password ", encoded));
        assertFalse(hasher.verify("Caf\u00e9-password", encoded));
        assertFalse(hasher.verify(" Cafe\u0301-password ", encoded));
    }

    @Test
    void rejectsWrongMissingOrMalformedCredentials() {
        String encoded = hasher.hash("isolated-test-password");

        assertFalse(hasher.verify("incorrect", encoded));
        assertFalse(hasher.verify(null, encoded));
        assertFalse(hasher.verify("isolated-test-password", null));
        assertFalse(hasher.verify("isolated-test-password", ""));
        assertFalse(hasher.verify("isolated-test-password", "plaintext"));
        assertFalse(hasher.verify("isolated-test-password", "$argon2id$broken"));
        assertThrows(IllegalArgumentException.class, () -> hasher.hash(null));
    }

    @Test
    void rejectsUnsupportedOrExcessiveStoredHashParameters() {
        String encoded = hasher.hash("isolated-test-password");

        assertFalse(hasher.verify("isolated-test-password", encoded.replace("argon2id", "argon2i")));
        assertFalse(hasher.verify("isolated-test-password", encoded.replace("v=19", "v=16")));
        assertFalse(hasher.verify("isolated-test-password", encoded.replace("m=19456", "m=65537")));
    }

    @Test
    void rejectsInvalidCostConfiguration() {
        int[][] invalidCosts = {{19455, 2, 1}, {65537, 2, 1}, {19456, 1, 1},
                {19456, 11, 1}, {19456, 2, 0}, {19456, 2, 5}};
        for (int[] cost : invalidCosts) {
            assertThrows(IllegalArgumentException.class, () -> new PasswordHasher(cost[0], cost[1], cost[2]));
        }
    }

    @Test
    void interoperatesWithTheLibraryWithoutExposingCredentials() {
        Argon2Function function = Argon2Function.getInstance(19456, 2, 1, 32, Argon2.ID);
        String encoded = Password.hash("library-test-password").addRandomSalt(16).with(function).getResult();

        assertTrue(hasher.verify("library-test-password", encoded));
        StoredCredentials credentials = new StoredCredentials(new UserRecord(1, "test-user", "Test User", "STUDENT"), encoded);
        assertFalse(credentials.toString().contains(encoded));
    }
}