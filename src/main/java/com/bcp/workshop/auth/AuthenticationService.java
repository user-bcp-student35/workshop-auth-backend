package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AuthenticationService {
    private final LegacyDatabase database;
    private final PasswordHasher passwordHasher;

    @Inject
    public AuthenticationService(LegacyDatabase database, PasswordHasher passwordHasher) {
        this.database = database;
        this.passwordHasher = passwordHasher;
    }

    public UserRecord authenticate(String username, String password) {
        StoredCredentials credentials = database.findByUsername(username);
        if (credentials == null) {
            return null;
        }
        if (!passwordHasher.verify(password, credentials.passwordHash())) {
            return null;
        }
        return credentials.user();
    }
}