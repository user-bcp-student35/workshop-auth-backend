package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class HealthResourceTest {
    @Test
    void reportsHealthWithoutCredentialsOrRequestBody() {
        Map<String, Object> response = given().accept("application/json")
                .when().get("/q/health").then()
                .statusCode(200).contentType("application/json")
                .extract().jsonPath().getMap("$");

        assertEquals(Map.of("status", "UP", "checks", List.of()), response);
    }

    @Test
    void supportsAnEmptyCheckListWithoutOptionalHeaders() {
        Map<String, Object> response = given()
                .when().get("/q/health").then()
                .statusCode(200).contentType("application/json")
                .extract().jsonPath().getMap("$");

        assertEquals(Map.of("status", "UP", "checks", List.of()), response);
    }

    @Test
    void returnsJsonEvenWhenAcceptRequestsXml() {
        Map<String, Object> response = given().accept("application/xml")
                .when().get("/q/health").then()
                .statusCode(200).contentType("application/json")
                .extract().jsonPath().getMap("$");

        assertEquals(Map.of("status", "UP", "checks", List.of()), response);
    }
}