package com.bcp.workshop.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyDatabaseTest {
    private static final org.sqlite.JDBC TEST_DRIVER = new org.sqlite.JDBC();
    private static final String LEGACY_SCHEMA_SQL = "CREATE TABLE legacy_users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL)";
    private static final int BENCHMARK_USERS = 100;
    private static final Duration MAX_LOGIN_TIME = Duration.ofSeconds(1);
    private static final Duration MAX_MIGRATION_TIME = Duration.ofSeconds(60);
    private LegacyDatabase database;
    private AuthenticationService authentication;

    @BeforeEach
    void resetUsers() throws Exception {
        database = resetDatabase();
        authentication = new AuthenticationService(database, new PasswordHasher());
    }

    @Test
    void findsStudentWithAllMappedFields() {
        UserRecord user = authentication.authenticate("student", "Workshop123!");

        assertNotNull(user);
        assertTrue(user.id() > 0);
        assertEquals("student", user.username());
        assertEquals("Workshop Student", user.displayName());
        assertEquals("STUDENT", user.role());
    }

    @Test
    void findsTrainerWithAllMappedFields() {
        UserRecord user = authentication.authenticate("trainer", "Trainer123!");

        assertNotNull(user);
        assertTrue(user.id() > 0);
        assertEquals("trainer", user.username());
        assertEquals("Workshop Trainer", user.displayName());
        assertEquals("TRAINER", user.role());
    }

    @Test
    void returnsNullForIncorrectPassword() {
        assertNull(authentication.authenticate("student", "incorrect"));
    }

    @Test
    void returnsNullForUnknownUser() {
        assertNull(authentication.authenticate("unknown", "Workshop123!"));
    }

    @Test
    void preservesCaseAndWhitespaceInCredentials() {
        assertNull(authentication.authenticate("Student", "Workshop123!"));
        assertNull(authentication.authenticate(" student ", "Workshop123!"));
        assertNull(authentication.authenticate("student", " Workshop123! "));
    }

    @Test
    void returnsNullWhenQueryFails() throws Exception {
        removeUsersTable();

        assertNull(authentication.authenticate("student", "Workshop123!"));
    }

    @Test
    void initializesWithoutChangingExistingUsers() {
        UserRecord original = authentication.authenticate("student", "Workshop123!");

        database.initialize();

        assertEquals(original, authentication.authenticate("student", "Workshop123!"));
    }

    @Test
    void preservesInitializationFailureHandling() throws Exception {
        removeUsersTable();
        try (Connection connection = openTestConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE legacy_users (id INTEGER PRIMARY KEY)");
        }

        assertDoesNotThrow(database::initialize);
    }

    @Test
    @Tag("correction")
    void authenticatesQuotedCredentialsAsLiteralValues() throws Exception {
        insertUser("o'connor", "Pass'word", "Quoted User", "STUDENT");

        UserRecord user = authentication.authenticate("o'connor", "Pass'word");

        assertNotNull(user);
        assertEquals("o'connor", user.username());
        assertEquals("Quoted User", user.displayName());
        assertEquals("STUDENT", user.role());
    }

    @Test
    @Tag("correction")
    void rejectsSqlSyntaxInUsername() {
        assertNull(authentication.authenticate("student' --", "incorrect"));
    }

    @Test
    @Tag("correction")
    void rejectsSqlSyntaxInPassword() {
        assertNull(authentication.authenticate("student", "' OR '1'='1"));
    }

    @Test
    @Tag("h02-correction")
    void freshDatabaseContainsOnlyPasswordHashes() throws Exception {
        assertHasOnlyPasswordHashes();
    }

    @Test
    @Tag("h02-correction")
    void migratesPlaintextWithoutLosingCredentials() throws Exception {
        removeUsersTable();
        try (Connection connection = openTestConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("PRAGMA user_version=0");
            statement.executeUpdate(LEGACY_SCHEMA_SQL);
            statement.executeUpdate("INSERT INTO legacy_users VALUES (42,'legacy-user','$argon2id$not-a-real-hash','Legacy User','TRAINER')");
        }

        database.initialize();

        UserRecord user = authentication.authenticate("legacy-user", "$argon2id$not-a-real-hash");
        assertEquals(new UserRecord(42, "legacy-user", "Legacy User", "TRAINER"), user);
        assertHasOnlyPasswordHashes();
    }

    @Test
    @Tag("h02-correction")
    void preservesHashesOnRestartAndUsesDistinctSalts() throws Exception {
        insertUser("same-a", "same-test-password", "Same A", "STUDENT");
        insertUser("same-b", "same-test-password", "Same B", "STUDENT");
        String originalHash = readHash("same-a");
        assertNotEquals(originalHash, readHash("same-b"));

        database.initialize();

        assertEquals(originalHash, readHash("same-a"));
        assertNotNull(authentication.authenticate("same-a", "same-test-password"));
        assertHasOnlyPasswordHashes();
    }

    @Test
    @Tag("h02-correction")
    void rollsBackEveryChangeWhenHashingFailsMidMigration() throws Exception {
        createLegacyUsers(2);
        PasswordHasher failingHasher = new PasswordHasher() {
            private int calls;

            @Override
            public String hash(String password) {
                calls++;
                if (calls == 2) {
                    throw new IllegalStateException("controlled migration failure");
                }
                return super.hash(password);
            }
        };
        try (Connection connection = openTestConnection()) {
            assertThrows(IllegalStateException.class, () -> new PasswordMigration(failingHasher).migrate(connection));
            assertTrue(connection.getAutoCommit());
            try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(
                    "SELECT password FROM legacy_users WHERE id=1")) {
                assertTrue(result.next());
                assertEquals("Legacy-Password-0", result.getString(1));
            }
            assertEquals(0, readVersion(connection));
            try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(
                    "SELECT COUNT(*) FROM sqlite_master WHERE name='legacy_users_h02'")) {
                assertTrue(result.next());
                assertEquals(0, result.getInt(1));
            }
        }
        assertNull(authentication.authenticate("legacy-0", "Legacy-Password-0"));
    }

    @Test
    @Tag("h02-correction")
    void rejectsUnsupportedVersionsWithoutChangingStoredUsers() throws Exception {
        String original = readHash("student");
        for (int version : new int[]{99, 0}) {
            try (Connection connection = openTestConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("PRAGMA user_version=" + version);
                assertThrows(SQLException.class, () -> new PasswordMigration().migrate(connection));
                assertEquals(version, readVersion(connection));
            }
            assertEquals(original, readHash("student"));
        }
    }

    @Test
    @Tag("h02-correction")
    void rejectsMalformedVersionOneSchema() throws Exception {
        removeUsersTable();
        try (Connection connection = openTestConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE legacy_users (id INTEGER PRIMARY KEY)");
            statement.executeUpdate("PRAGMA user_version=1");

            assertThrows(SQLException.class, () -> new PasswordMigration().migrate(connection));
            assertEquals(1, readVersion(connection));
        }
    }

    @Test
    @Tag("h02-correction")
    void refusesAnExistingTransaction() throws Exception {
        try (Connection connection = openTestConnection()) {
            connection.setAutoCommit(false);
            assertThrows(SQLException.class, () -> new PasswordMigration().migrate(connection));
            connection.rollback();
        }
    }

    @Test
    @Tag("h02-correction")
    void rejectsInvalidSeedConfigurationTransactionally() throws Exception {
        String original = readHash("student");
        for (boolean invalidStudent : new boolean[]{true, false}) {
            PasswordMigration migration = new PasswordMigration();
            if (invalidStudent) {
                migration.studentHash = "plaintext-is-not-a-hash";
            } else {
                migration.trainerHash = "plaintext-is-not-a-hash";
            }
            try (Connection connection = openTestConnection()) {
                assertThrows(SQLException.class, () -> migration.migrate(connection));
                assertEquals(1, readVersion(connection));
            }
            assertEquals(original, readHash("student"));
        }
    }

    @Test
    @Tag("h02-correction")
    void preservesSequenceUniquenessAndExistingSeedAccounts() throws Exception {
        createLegacyUsers(0);
        try (Connection connection = openTestConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO legacy_users VALUES (42,'student','Existing-Password','Existing Student','TRAINER')");
            statement.executeUpdate("INSERT INTO legacy_users VALUES (900,'deleted','Deleted-Password','Deleted','STUDENT')");
            statement.executeUpdate("DELETE FROM legacy_users WHERE id=900");
        }

        database.initialize();

        assertEquals(new UserRecord(42, "student", "Existing Student", "TRAINER"),
                authentication.authenticate("student", "Existing-Password"));
        assertNull(authentication.authenticate("student", "Workshop123!"));
        insertUser("new-user", "New-Password", "New User", "STUDENT");
        assertTrue(authentication.authenticate("new-user", "New-Password").id() > 900);
        assertThrows(SQLException.class, () -> insertUser("student", "Other-Password", "Other", "STUDENT"));
    }

    @Test
    @Tag("h02-correction")
    void migratesEmptyLegacyTableWithHistoricalSequence() throws Exception {
        createLegacyUsers(0);
        try (Connection connection = openTestConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO legacy_users VALUES (900,'deleted','Deleted-Password','Deleted','STUDENT')");
            statement.executeUpdate("DELETE FROM legacy_users");
        }

        database.initialize();

        assertTrue(authentication.authenticate("student", "Workshop123!").id() > 900);
        assertHasOnlyPasswordHashes();
    }

    @Test
    @Tag("h02-correction")
    void measuresMigrationAndRestoresTheIsolatedBackup() throws Exception {
        createLegacyUsers(BENCHMARK_USERS);
        Path backup = Path.of("target", "h02-recovery-fixture.db");
        Files.copy(testDatabasePath(), backup, StandardCopyOption.REPLACE_EXISTING);
        PasswordHasher hasher = new PasswordHasher();
        long heapBefore = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        long hashStart = System.nanoTime();
        String encoded = hasher.hash("benchmark-password");
        long hashNanos = System.nanoTime() - hashStart;
        long verifyStart = System.nanoTime();
        assertTrue(hasher.verify("benchmark-password", encoded));
        long verifyNanos = System.nanoTime() - verifyStart;
        long migrationStart = System.nanoTime();
        assertTimeout(MAX_MIGRATION_TIME, () -> {
            try (Connection connection = openTestConnection()) {
                new PasswordMigration(hasher).migrate(connection);
            }
        });
        long migrationNanos = System.nanoTime() - migrationStart;
        long heapAfter = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        assertTimeout(MAX_LOGIN_TIME, () -> assertNotNull(authentication.authenticate("legacy-0", "Legacy-Password-0")));
        assertEquals(1, authentication.authenticate("legacy-0", "Legacy-Password-0").id());
        assertHasOnlyPasswordHashes();

        Files.copy(backup, testDatabasePath(), StandardCopyOption.REPLACE_EXISTING);
        assertTimeout(MAX_MIGRATION_TIME, () -> {
            try (Connection connection = openTestConnection()) {
                new PasswordMigration(hasher).migrate(connection);
            }
        });
        assertNotNull(authentication.authenticate("legacy-99", "Legacy-Password-99"));
        assertHasOnlyPasswordHashes();
        System.out.printf(Locale.ROOT,
                "H02_BENCHMARK users=%d memoryKiB=19456 iterations=2 parallelism=1 hashMs=%.3f verifyMs=%.3f migrationMs=%.3f heapBeforeBytes=%d heapAfterBytes=%d recovery=PASS%n",
                BENCHMARK_USERS, hashNanos / 1_000_000.0, verifyNanos / 1_000_000.0,
                migrationNanos / 1_000_000.0, heapBefore, heapAfter);
    }

    private static String readHash(String username) throws Exception {
        try (Connection connection = openTestConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT password_hash FROM legacy_users WHERE username=?")) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }

    private static int readVersion(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("PRAGMA user_version")) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private static void createLegacyUsers(int count) throws Exception {
        removeUsersTable();
        try (Connection connection = openTestConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(LEGACY_SCHEMA_SQL);
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO legacy_users(username,password,display_name,role) VALUES (?,?,?,?)")) {
                for (int index = 0; index < count; index++) {
                    insert.setString(1, "legacy-" + index);
                    insert.setString(2, "Legacy-Password-" + index);
                    insert.setString(3, "Legacy User " + index);
                    insert.setString(4, "STUDENT");
                    insert.executeUpdate();
                }
            }
        }
    }

    private static void assertHasOnlyPasswordHashes() throws Exception {
        List<String> columns = new ArrayList<>();
        try (Connection connection = openTestConnection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA table_info(legacy_users)")) {
            while (result.next()) {
                columns.add(result.getString("name"));
            }
        }
        assertTrue(columns.contains("password_hash"), "Active schema must contain password_hash");
        assertFalse(columns.contains("password"), "Active schema must not contain plaintext password");
    }

    static LegacyDatabase resetDatabase() throws Exception {
        removeUsersTable();
        LegacyDatabase database = new LegacyDatabase();
        database.initialize();
        return database;
    }

    static void insertUser(String username, String password, String displayName, String role) throws Exception {
        String sql = "INSERT INTO legacy_users(username,password_hash,display_name,role) VALUES (?,?,?,?)";
        try (Connection connection = openTestConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, new PasswordHasher().hash(password));
            statement.setString(3, displayName);
            statement.setString(4, role);
            statement.executeUpdate();
        }
    }

    static void removeUsersTable() throws Exception {
        try (Connection connection = openTestConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE IF EXISTS legacy_users");
            statement.executeUpdate("DROP TABLE IF EXISTS legacy_users_h02");
            statement.executeUpdate("PRAGMA user_version=0");
        }
    }

    private static Connection openTestConnection() throws Exception {
        Path databasePath = testDatabasePath();
        Files.createDirectories(databasePath.getParent());
        DriverManager.registerDriver(TEST_DRIVER);
        return DriverManager.getConnection(System.getenv("AUTH_DB_URL"));
    }

    private static Path testDatabasePath() {
        String url = System.getenv("AUTH_DB_URL");
        assertNotNull(url, "Set AUTH_DB_URL to a SQLite file inside target before running these tests");
        String prefix = "jdbc:sqlite:";
        assertTrue(url.startsWith(prefix), "Tests require SQLite");
        Path databasePath = Path.of(url.substring(prefix.length())).toAbsolutePath().normalize();
        assertTrue(databasePath.startsWith(Path.of("target").toAbsolutePath().normalize()),
                "Tests must not modify a database outside target");
        return databasePath;
    }
}