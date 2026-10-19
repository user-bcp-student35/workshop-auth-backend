package com.bcp.workshop.auth;

import com.password4j.Argon2Function;
import com.password4j.Password;
import com.password4j.types.Argon2;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class PasswordHasher {
    private static final int DEFAULT_MEMORY_KIB = 19456;
    private static final int DEFAULT_ITERATIONS = 2;
    private static final int DEFAULT_PARALLELISM = 1;
    private static final int MAX_MEMORY_KIB = 65536;
    private static final int MAX_ITERATIONS = 10;
    private static final int MAX_PARALLELISM = 4;
    private static final int HASH_BYTES = 32;
    private static final int SALT_BYTES = 16;
    private final Argon2Function function;

    public PasswordHasher() {
        this(DEFAULT_MEMORY_KIB, DEFAULT_ITERATIONS, DEFAULT_PARALLELISM);
    }

    @Inject
    public PasswordHasher(@ConfigProperty(name = "auth.password.memory-kib", defaultValue = "19456") int memoryKib,
                          @ConfigProperty(name = "auth.password.iterations", defaultValue = "2") int iterations,
                          @ConfigProperty(name = "auth.password.parallelism", defaultValue = "1") int parallelism) {
        validateCosts(memoryKib, iterations, parallelism);
        function = Argon2Function.getInstance(memoryKib, iterations, parallelism, HASH_BYTES, Argon2.ID);
    }

    public String hash(String password) {
        if (password == null) {
            throw new IllegalArgumentException("Password must not be null");
        }
        return Password.hash(password).addRandomSalt(SALT_BYTES).with(function).getResult();
    }

    public boolean verify(String password, String encoded) {
        if (password == null) {
            return false;
        }
        try {
            Argon2Function storedFunction = supportedFunction(encoded);
            return storedFunction != null && Password.check(password, encoded).with(storedFunction);
        } catch (IllegalArgumentException | IndexOutOfBoundsException error) {
            return false;
        }
    }

    public boolean isSupportedHash(String encoded) {
        return supportedFunction(encoded) != null;
    }

    private static Argon2Function supportedFunction(String encoded) {
        if (encoded == null) {
            return null;
        }
        try {
            Argon2Function stored = Argon2Function.getInstanceFromHash(encoded);
            if (stored.getVariant() != Argon2.ID || stored.getVersion() != Argon2Function.ARGON2_VERSION_13) {
                return null;
            }
            validateCosts(stored.getMemory(), stored.getIterations(), stored.getParallelism());
            return stored;
        } catch (IllegalArgumentException | IndexOutOfBoundsException error) {
            return null;
        }
    }

    private static void validateCosts(int memoryKib, int iterations, int parallelism) {
        if (memoryKib < DEFAULT_MEMORY_KIB || memoryKib > MAX_MEMORY_KIB
                || iterations < DEFAULT_ITERATIONS || iterations > MAX_ITERATIONS
                || parallelism < DEFAULT_PARALLELISM || parallelism > MAX_PARALLELISM) {
            throw new IllegalArgumentException("Unsupported password hashing costs");
        }
    }
}