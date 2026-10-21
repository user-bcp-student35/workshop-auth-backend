package com.bcp.workshop.auth;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.Mockito.when;

// Separate class: the injected mock would replace the real service for every test in AuthResourceTest.
@QuarkusTest
class AuthResourceInternalErrorTest {
    @InjectMock
    AuthenticationService authenticationService;

    @Test
    void authenticationFailureReturnsInternalErrorOverHttp() {
        when(authenticationService.authenticate("student", "Workshop123!"))
                .thenThrow(new IllegalStateException("controlled test failure"));

        given().contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
                .when().post("/auth/login").then()
                .statusCode(500).contentType("application/json")
                .body("message", is("controlled test failure"))
                .body("exception", is(IllegalStateException.class.getName()));
    }
}
