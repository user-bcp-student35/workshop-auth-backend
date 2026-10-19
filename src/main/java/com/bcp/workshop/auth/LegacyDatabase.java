package com.bcp.workshop.auth;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@ApplicationScoped
public class LegacyDatabase {
    private static final String DB_URL = System.getenv().getOrDefault("AUTH_DB_URL", "jdbc:sqlite:./data/workshop.db");
    private static final String FIND_BY_USERNAME_SQL =
            "SELECT id, username, password_hash, display_name, role FROM legacy_users WHERE username=?";
    private static final int USERNAME_PARAMETER = 1;
    private final PasswordMigration passwordMigration;

    public LegacyDatabase() {
        this(new PasswordMigration());
    }

    @Inject
    public LegacyDatabase(PasswordMigration passwordMigration) {
        this.passwordMigration = passwordMigration;
    }

    @PostConstruct
    void initialize() {
        try {
            Files.createDirectories(Path.of("data"));
            try (Connection connection = DriverManager.getConnection(DB_URL)) {
                passwordMigration.migrate(connection);
            }
        } catch (Exception error) {
            error.printStackTrace();
        }
    }

    public StoredCredentials findByUsername(String username) {
        try (Connection connection = DriverManager.getConnection(DB_URL);
             PreparedStatement statement = connection.prepareStatement(FIND_BY_USERNAME_SQL)) {
            statement.setString(USERNAME_PARAMETER, username);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? new StoredCredentials(mapUser(result), result.getString("password_hash")) : null;
            }
        } catch (Exception error) {
            error.printStackTrace();
        }
        return null;
    }

    private static UserRecord mapUser(ResultSet result) throws SQLException {
        return new UserRecord(result.getLong("id"), result.getString("username"),
                result.getString("display_name"), result.getString("role"));
    }
}
