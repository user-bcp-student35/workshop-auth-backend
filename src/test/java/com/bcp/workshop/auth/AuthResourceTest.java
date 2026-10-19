package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.nio.charset.StandardCharsets;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AuthResourceTest {
    private static final int TOKEN_LIFETIME_SECONDS = 3600;

    @BeforeEach
    void resetUsers() throws Exception {
        LegacyDatabaseTest.resetDatabase();
    }

    @Test
    void logsInWithWorkshopUser() {
        assertSuccessfulLogin("student", "Workshop123!", "Workshop Student", "STUDENT");
    }

    @Test
    void logsInWithTrainer() {
        assertSuccessfulLogin("trainer", "Trainer123!", "Workshop Trainer", "TRAINER");
    }

    @Test
    void escapesDisplayNameWithoutChangingResponseFields() throws Exception {
        LegacyDatabaseTest.insertUser("viewer", "Visible123!", "<b>Reader & Reviewer</b>", "STUDENT");

        assertSuccessfulLogin("viewer", "Visible123!", "&lt;b&gt;Reader &amp; Reviewer&lt;/b&gt;", "STUDENT");
    }

    @Test
    void ignoresClientAndUnknownProperties() {
        Map<String, Object> request = new HashMap<>(Map.of("username", "student", "password", "Workshop123!"));
        request.put("client", "other-client");
        request.put("unknownProperty", "ignored");

        login(request).statusCode(200).body("user.username", is("student"));
    }

    @Test
    void acceptsMissingNullOrEmptyClient() {
        Map<String, Object> request = new HashMap<>(Map.of("username", "student", "password", "Workshop123!"));
        login(request).statusCode(200).body("user.username", is("student"));
        for (String client : Arrays.asList(null, "")) {
            request.put("client", client);
            login(request).statusCode(200).body("user.username", is("student"));
        }
    }

    @Test
    void repeatedLoginPreservesUserIdentity() {
        assertSuccessfulLogin("student", "Workshop123!", "Workshop Student", "STUDENT");
        assertSuccessfulLogin("student", "Workshop123!", "Workshop Student", "STUDENT");
    }

    @Test
    void authenticatesFullLongUnicodePassword() throws Exception {
        String prefix = "long-password-".repeat(10) + "\u00f1\u6f22";
        LegacyDatabaseTest.insertUser("long-user", prefix + "A", "Long User", "STUDENT");

        assertSuccessfulLogin("long-user", prefix + "A", "Long User", "STUDENT");
        assertUnauthorized("long-user", prefix + "B");
    }

    @Test
    void doesNotNormalizeUnicodePasswords() throws Exception {
        LegacyDatabaseTest.insertUser("unicode-user", "Caf\u00e9-password", "Unicode User", "STUDENT");

        assertSuccessfulLogin("unicode-user", "Caf\u00e9-password", "Unicode User", "STUDENT");
        assertUnauthorized("unicode-user", "Cafe\u0301-password");
    }

    @Test
    void rejectsJsonNullRequestOverHttp() {
        given().contentType("application/json").body("null")
                .when().post("/auth/login").then()
                .statusCode(400).contentType("application/json")
                .body("$", is(Map.of("message", "Request is null")));
    }

    @Test
    void rejectsEmptyRequestOverHttp() {
        given().contentType("application/json").body("")
                .when().post("/auth/login").then()
                .statusCode(400).contentType("application/json")
                .body("$", is(Map.of("message", "Request is null")));
    }

    @Test
    void rejectsMalformedJsonWithEmptyBody() {
        assertEmptyError(given().contentType("application/json").body("{")
                .when().post("/auth/login").then(), 400);
    }

    @Test
    void rejectsObjectInsteadOfUsername() {
        Map<String, Object> expected = new HashMap<>(Map.of(
                "objectName", "LoginRequest", "attributeName", "username", "line", 1, "column", 13));
        expected.put("value", null);

        Map<String, Object> response = given().contentType("application/json")
                .body("{\"username\":{},\"password\":\"Workshop123!\"}")
                .when().post("/auth/login").then()
                .statusCode(400).contentType("application/json")
                .extract().jsonPath().getMap("$");

        assertEquals(expected, response);
    }

    @Test
    void rejectsUnsupportedContentType() {
        assertEmptyError(given().contentType("text/plain").body("student")
                .when().post("/auth/login").then(), 415);
    }

    @Test
    void rejectsUnsupportedAccept() {
        assertEmptyError(given().contentType("application/json").accept("application/xml")
                .body(Map.of("username", "student", "password", "Workshop123!", "client", "angular-workshop"))
                .when().post("/auth/login").then(), 406);
    }

    @Test
    void validatesNullRequestBeforeAccessingDatabase() {
        AuthResource resource = new AuthResource();
        try (jakarta.ws.rs.core.Response response = resource.login(null)) {
            assertEquals(400, response.getStatus());
            assertEquals(Map.of("message", "Request is null"), response.getEntity());
        }
    }

    @Test
    void validatesUsernameBeforePassword() {
        for (String username : Arrays.asList(null, "", " \t ")) {
            Map<String, Object> request = new HashMap<>();
            request.put("username", username);
            request.put("password", "");

            login(request).statusCode(400).body("message", is("Username is required"));
        }
        login(Map.of()).statusCode(400).body("message", is("Username is required"));
    }

    @Test
    void validatesMissingOrBlankPassword() {
        for (String password : Arrays.asList(null, "", " \t ")) {
            Map<String, Object> request = new HashMap<>();
            request.put("username", "student");
            request.put("password", password);

            login(request).statusCode(400).body("message", is("Password is required"));
        }
        login(Map.of("username", "student")).statusCode(400).body("message", is("Password is required"));
    }

    @Test
    void rejectsIncorrectPassword() {
        assertUnauthorized("student", "incorrect");
    }

    @Test
    void rejectsUnknownUser() {
        assertUnauthorized("unknown", "Workshop123!");
    }

    @Test
    void doesNotNormalizeCredentials() {
        assertUnauthorized("Student", "Workshop123!");
        assertUnauthorized(" student ", "Workshop123!");
        assertUnauthorized("student", " Workshop123! ");
    }

    @Test
    void queryFailurePreservesUnauthorizedResponse() throws Exception {
        LegacyDatabaseTest.removeUsersTable();

        assertUnauthorized("student", "Workshop123!");
    }

    @Test
    void preservesInternalErrorResponse() {
        assertInternalError("controlled test failure", "controlled test failure");
    }

    @Test
    void preservesUnknownErrorFallback() {
        assertInternalError(null, "Unknown error");
    }

    @Test
    @Tag("correction")
    void logsInWithQuotedCredentials() throws Exception {
        LegacyDatabaseTest.insertUser("o'connor", "Pass'word", "Quoted User", "STUDENT");

        assertSuccessfulLogin("o'connor", "Pass'word", "Quoted User", "STUDENT");
    }

    @Test
    @Tag("correction")
    void rejectsSqlSyntaxInUsername() {
        assertUnauthorized("student' --", "incorrect");
    }

    @Test
    @Tag("correction")
    void rejectsSqlSyntaxInPassword() {
        assertUnauthorized("student", "' OR '1'='1");
    }

    private static ValidatableResponse login(Map<String, ?> request) {
        return given().contentType("application/json").body(request)
                .when().post("/auth/login").then().contentType("application/json");
    }

    private static void assertEmptyError(ValidatableResponse response, int status) {
        io.restassured.response.Response actual = response.statusCode(status).extract().response();
        assertEquals(0, actual.asByteArray().length);
        assertEquals(null, actual.getHeader("Content-Type"));
    }

    private static void assertUnauthorized(String username, String password) {
        Map<String, Object> response = login(Map.of("username", username, "password", password))
                .statusCode(401).extract().jsonPath().getMap("$");
        assertEquals(Map.of("message", "Invalid user " + username + " or password"), response);
    }

    private static void assertSuccessfulLogin(String username, String password, String displayName, String role) {
        long earliestExpiry = Instant.now().getEpochSecond() + TOKEN_LIFETIME_SECONDS;
        Map<String, Object> response = login(Map.of("username", username, "password", password, "client", "angular-workshop"))
                .statusCode(200).extract().jsonPath().getMap("$");
        long latestExpiry = Instant.now().getEpochSecond() + TOKEN_LIFETIME_SECONDS;

        assertEquals(Set.of("token", "expiresIn", "user", "debug"), response.keySet());
        assertInstanceOf(Integer.class, response.get("expiresIn"));
        assertEquals(TOKEN_LIFETIME_SECONDS, response.get("expiresIn"));
        assertEquals("authenticated-with-legacy-flow", response.get("debug"));
        assertEquals(Map.of("username", username, "displayName", displayName, "role", role), response.get("user"));
        String token = assertInstanceOf(String.class, response.get("token"));
        String[] fields = new String(Base64.getDecoder().decode(token), StandardCharsets.UTF_8).split("\\|", -1);
        assertEquals(4, fields.length);
        assertEquals(username, fields[0]);
        assertEquals(role, fields[1]);
        long expiry = Long.parseLong(fields[2]);
        assertTrue(expiry >= earliestExpiry && expiry <= latestExpiry);
        assertTrue(!fields[3].isBlank());
    }

    private static void assertInternalError(String message, String expectedMessage) {
        AuthResource resource = new AuthResource();
        resource.authenticationService = new AuthenticationService(new LegacyDatabase(), new PasswordHasher()) {
            @Override
            public UserRecord authenticate(String username, String password) {
                throw new IllegalStateException(message);
            }
        };
        LoginRequest request = new LoginRequest();
        request.username = "student";
        request.password = "Workshop123!";

        try (jakarta.ws.rs.core.Response response = resource.login(request)) {
            assertEquals(500, response.getStatus());
            assertEquals(Map.of("message", expectedMessage, "exception", IllegalStateException.class.getName()),
                    response.getEntity());
        }
    }
}
