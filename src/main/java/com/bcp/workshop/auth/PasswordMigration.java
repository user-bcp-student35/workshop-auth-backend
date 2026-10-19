package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

@ApplicationScoped
public class PasswordMigration {
    private static final int HASHED_SCHEMA_VERSION = 1;
    private static final String STUDENT_HASH = "$argon2id$v=19$m=19456,t=2,p=1$C1UhpSkajNXpzONXb0cjAw$jvvDKRV1iQk9OrdIZKkQ+ubbsmzkve5U7AHBbpTACIw";
    private static final String TRAINER_HASH = "$argon2id$v=19$m=19456,t=2,p=1$O2T77t6j7QGjrzMqcEDmtQ$O4/eyeQ1WyTgBFLdrab01iauVU2NjlGvKYyRHRBdBgc";
    private static final Set<String> LEGACY_COLUMNS = Set.of("id", "username", "password", "display_name", "role");
    private static final Set<String> HASHED_COLUMNS = Set.of("id", "username", "password_hash", "display_name", "role");
    private static final String TABLE_DEFINITION = " (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password_hash TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL)";
    private static final String INSERT_MIGRATED = "INSERT INTO legacy_users_h02(id,username,password_hash,display_name,role) VALUES (?,?,?,?,?)";
    private static final String INSERT_SEED = "INSERT OR IGNORE INTO legacy_users(username,password_hash,display_name,role) VALUES (?,?,?,?)";
    private final PasswordHasher passwordHasher;

    @ConfigProperty(name = "auth.seed.student-hash", defaultValue = STUDENT_HASH)
    String studentHash = STUDENT_HASH;

    @ConfigProperty(name = "auth.seed.trainer-hash", defaultValue = TRAINER_HASH)
    String trainerHash = TRAINER_HASH;

    public PasswordMigration() {
        this(new PasswordHasher());
    }

    @Inject
    public PasswordMigration(PasswordHasher passwordHasher) {
        this.passwordHasher = passwordHasher;
    }

    public void migrate(Connection connection) throws SQLException {
        if (!connection.getAutoCommit()) {
            throw new SQLException("Password migration requires an independent transaction");
        }
        connection.setAutoCommit(false);
        try {
            migrateSchema(connection);
            seedUsers(connection);
            execute(connection, "PRAGMA user_version=" + HASHED_SCHEMA_VERSION);
            connection.commit();
        } catch (SQLException | RuntimeException error) {
            connection.rollback();
            throw error;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void migrateSchema(Connection connection) throws SQLException {
        int version = readVersion(connection);
        Set<String> columns = readColumns(connection);
        if (version == 0 && columns.isEmpty()) {
            execute(connection, "CREATE TABLE legacy_users" + TABLE_DEFINITION);
            return;
        }
        if (version == 0 && columns.equals(LEGACY_COLUMNS)) {
            migrateLegacyRows(connection);
            return;
        }
        if (version != HASHED_SCHEMA_VERSION || !columns.equals(HASHED_COLUMNS)) {
            throw new SQLException("Unsupported password database schema");
        }
    }

    private void migrateLegacyRows(Connection connection) throws SQLException {
        long previousSequence = readSequence(connection);
        execute(connection, "CREATE TABLE legacy_users_h02" + TABLE_DEFINITION);
        copyLegacyRows(connection);
        verifyRowCount(connection);
        execute(connection, "DROP TABLE legacy_users");
        execute(connection, "ALTER TABLE legacy_users_h02 RENAME TO legacy_users");
        restoreSequence(connection, Math.max(previousSequence, readSequence(connection)));
    }

    private void copyLegacyRows(Connection connection) throws SQLException {
        try (Statement query = connection.createStatement();
             ResultSet rows = query.executeQuery("SELECT id,username,password,display_name,role FROM legacy_users");
             PreparedStatement insert = connection.prepareStatement(INSERT_MIGRATED)) {
            while (rows.next()) {
                insert.setLong(1, rows.getLong("id"));
                insert.setString(2, rows.getString("username"));
                insert.setString(3, passwordHasher.hash(rows.getString("password")));
                insert.setString(4, rows.getString("display_name"));
                insert.setString(5, rows.getString("role"));
                insert.executeUpdate();
            }
        }
    }

    private static void verifyRowCount(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(
                "SELECT (SELECT COUNT(*) FROM legacy_users) = (SELECT COUNT(*) FROM legacy_users_h02)")) {
            if (!result.next() || !result.getBoolean(1)) {
                throw new SQLException("Password migration row count mismatch");
            }
        }
    }

    private void seedUsers(Connection connection) throws SQLException {
        if (!passwordHasher.isSupportedHash(studentHash) || !passwordHasher.isSupportedHash(trainerHash)) {
            throw new SQLException("Unsupported laboratory seed hashes");
        }
        insertSeed(connection, "student", studentHash, "Workshop Student", "STUDENT");
        insertSeed(connection, "trainer", trainerHash, "Workshop Trainer", "TRAINER");
    }

    private static void insertSeed(Connection connection, String username, String hash, String displayName, String role) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(INSERT_SEED)) {
            insert.setString(1, username);
            insert.setString(2, hash);
            insert.setString(3, displayName);
            insert.setString(4, role);
            insert.executeUpdate();
        }
    }

    private static int readVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("PRAGMA user_version")) {
            result.next();
            return result.getInt(1);
        }
    }

    private static Set<String> readColumns(Connection connection) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("PRAGMA table_info(legacy_users)")) {
            while (result.next()) {
                columns.add(result.getString("name"));
            }
        }
        return columns;
    }

    private static long readSequence(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT seq FROM sqlite_sequence WHERE name='legacy_users'")) {
            return result.next() ? result.getLong(1) : 0;
        }
    }

    private static void restoreSequence(Connection connection, long sequence) throws SQLException {
        execute(connection, "DELETE FROM sqlite_sequence WHERE name='legacy_users'");
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO sqlite_sequence(name,seq) VALUES ('legacy_users',?)")) {
            insert.setLong(1, sequence);
            insert.executeUpdate();
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }
}