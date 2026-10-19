package com.bcp.workshop.auth;

public record StoredCredentials(UserRecord user, String passwordHash) {
    @Override
    public String toString() {
        return "StoredCredentials[user=" + user + "]";
    }
}