package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Base64;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

@QuarkusTest
class AuthResourceTest {
    @Inject
    @ConfigProperty(name = "auth.token.secret")
    String signingSecret;

    @Test
    void logsInWithWorkshopUser() throws Exception {
        long beforeLogin = Instant.now().getEpochSecond();
        var response = given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("$", aMapWithSize(4))
                .body("user.username", is("student"))
                .body("user.displayName", is("Workshop Student"))
                .body("user.role", is("STUDENT"))
                .body("user", aMapWithSize(3))
                .body("debug", is("authenticated-with-legacy-flow"))
                .body("expiresIn", greaterThan(0))
                .body("token", not(emptyOrNullString()))
                .extract().response();
        long afterLogin = Instant.now().getEpochSecond();
        SignedJWT token = SignedJWT.parse(response.jsonPath().getString("token"));
        assertEquals(JWSAlgorithm.HS256, token.getHeader().getAlgorithm());
        assertTrue(token.verify(new MACVerifier(signingSecret.getBytes(StandardCharsets.UTF_8))));
        var claims = token.getJWTClaimsSet();
        assertEquals(Set.of("sub", "client", "role", "iat", "exp"), claims.getClaims().keySet());
        assertEquals("student", claims.getSubject());
        assertEquals("angular-workshop", claims.getStringClaim("client"));
        assertEquals("STUDENT", claims.getStringClaim("role"));
        long issuedAt = claims.getIssueTime().toInstant().getEpochSecond();
        long expiresAt = claims.getExpirationTime().toInstant().getEpochSecond();
        assertTrue(issuedAt >= beforeLogin && issuedAt <= afterLogin);
        assertEquals(3600, expiresAt - issuedAt);
        long expiresIn = response.jsonPath().getLong("expiresIn");
        assertTrue(Math.abs(expiresIn - (expiresAt - afterLogin)) <= 1);
    }

    @Test
    void preservesNonBlankClientWithoutNormalization() throws Exception {
        Map<String, Object> request = loginRequest("student", "Workshop123!");
        request.put("client", " angular-workshop ");
        String serialized = given().contentType("application/json").body(request)
                .when().post("/auth/login")
                .then().statusCode(200).extract().path("token");
        assertEquals(" angular-workshop ", SignedJWT.parse(serialized).getJWTClaimsSet().getStringClaim("client"));
    }

    @Test
    void escapesDisplayNameInSuccessfulResponse() {
        given()
                .contentType("application/json")
                .body(loginRequest("characterization-html-user", "characterization-fixture"))
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is("characterization-html-user"))
                .body("user.displayName", is("&lt;Student&gt; &amp; &quot;Workshop&quot;"))
                .body("user.role", is("STUDENT"));
    }

    @Test
    void rejectsNullRequest() {
        given()
                .contentType("application/json")
                .body("null")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .contentType("application/json")
                .body("$", aMapWithSize(1))
                .body("message", is("Request is null"));
    }

    @Test
    void rejectsMissingUsername() {
        given()
                .contentType("application/json")
                .body(Map.of("password", "Workshop123!", "client", "angular-workshop"))
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .contentType("application/json")
                .body("$", aMapWithSize(1))
                .body("message", is("Username is required"));
    }

    @Test
    void rejectsMissingPassword() {
        given()
                .contentType("application/json")
                .body(Map.of("username", "student", "client", "angular-workshop"))
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .contentType("application/json")
                .body("$", aMapWithSize(1))
                .body("message", is("Password is required"));
    }

    @Test
    void rejectsNullEmptyOrBlankUsernameBeforePassword() {
        for (String username : new String[]{null, "", " ", "\t\r\n"}) {
            given()
                    .contentType("application/json")
                    .body(loginRequest(username, null))
                    .when().post("/auth/login")
                    .then()
                    .statusCode(400)
                    .contentType("application/json")
                    .body("$", aMapWithSize(1))
                    .body("message", is("Username is required"));
        }
    }

    @Test
    void rejectsNullEmptyOrBlankPassword() {
        for (String password : new String[]{null, "", " ", "\t\r\n"}) {
            given()
                    .contentType("application/json")
                    .body(loginRequest("student", password))
                    .when().post("/auth/login")
                    .then()
                    .statusCode(400)
                    .contentType("application/json")
                    .body("$", aMapWithSize(1))
                    .body("message", is("Password is required"));
        }
    }

    @Test
    void rejectsUnknownUserAndIncorrectPassword() {
        for (String username : new String[]{"characterization-missing-user", "student"}) {
            given()
                    .contentType("application/json")
                    .body(loginRequest(username, "incorrect-characterization-password"))
                    .when().post("/auth/login")
                    .then()
                    .statusCode(401)
                    .contentType("application/json")
                    .body("$", aMapWithSize(1))
                    .body("message", is("Invalid user " + username + " or password"));
        }
    }

    @Test
    void rejectsMissingClientBeforeCheckingCredentials() {
        given()
                .contentType("application/json")
                .body(Map.of("username", "characterization-missing-user", "password", "incorrect-fixture"))
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("$", aMapWithSize(1))
                .body("message", is("Client is required"));
    }

    @Test
    void rejectsNullEmptyOrBlankClient() {
        for (String client : new String[]{null, "", " ", "\t\r\n"}) {
            Map<String, Object> request = loginRequest("student", "Workshop123!");
            request.put("client", client);
            given()
                    .contentType("application/json")
                    .body(request)
                    .when().post("/auth/login")
                    .then()
                    .statusCode(400)
                    .body("$", aMapWithSize(1))
                    .body("message", is("Client is required"));
        }
    }

    @Test
    void preservesUsernameAndPasswordValidationBeforeClient() {
        given().contentType("application/json").body("{}")
                .when().post("/auth/login")
                .then().statusCode(400).body("message", is("Username is required"));
        given().contentType("application/json").body(Map.of("username", "student"))
                .when().post("/auth/login")
                .then().statusCode(400).body("message", is("Password is required"));
    }

    @Test
    void mapsDatabaseExceptionToExistingErrorResponse() {
        given()
                .contentType("application/json")
                .body(loginRequest("characterization-failure", "characterization-fixture"))
                .when().post("/auth/login")
                .then()
                .statusCode(500)
                .contentType("application/json")
                .body("$", aMapWithSize(2))
                .body("message", is("Characterization database failure"))
                .body("exception", is("java.lang.IllegalStateException"));
    }

    @Test
    void usesUnknownErrorWhenExceptionHasNoMessage() {
        given()
                .contentType("application/json")
                .body(loginRequest("characterization-failure-without-message", "characterization-fixture"))
                .when().post("/auth/login")
                .then()
                .statusCode(500)
                .contentType("application/json")
                .body("$", aMapWithSize(2))
                .body("message", is("Unknown error"))
                .body("exception", is("java.lang.IllegalStateException"));
    }

    @Test
    void doesNotLogAuthenticationMaterialOnSuccessOrFailure() {
        ConcurrentLinkedQueue<String> messages = new ConcurrentLinkedQueue<>();
        SimpleFormatter formatter = new SimpleFormatter();
        Handler capture = new Handler() {
            @Override
            public void publish(LogRecord record) {
                messages.add(formatter.format(record));
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        Logger root = Logger.getLogger("");
        root.addHandler(capture);
        try {
            Logger.getLogger(AuthResource.class.getName()).info("authentication-log-capture-active");
            String password = "logging-unique-password-fixture";
            String failedPassword = "logging-failed-password-fixture";
            String token = given().contentType("application/json")
                    .body(loginRequest("logging-fixture-user", password))
                    .when().post("/auth/login")
                    .then().statusCode(200).extract().path("token");
            given().contentType("application/json")
                    .body(loginRequest("logging-fixture-user", failedPassword))
                    .when().post("/auth/login")
                    .then().statusCode(401);
            String logs = String.join("\n", messages);
            assertTrue(logs.contains("authentication-log-capture-active"));
            for (String sensitive : new String[]{password, failedPassword, signingSecret, token}) {
                assertFalse(logs.contains(sensitive));
                byte[] bytes = sensitive.getBytes(StandardCharsets.UTF_8);
                assertFalse(logs.contains(Base64.getEncoder().encodeToString(bytes)));
                assertFalse(logs.contains(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)));
            }
        } finally {
            root.removeHandler(capture);
        }
    }

    @Test
    void preservesHealthResponse() {
        given().when().get("/q/health")
                .then().statusCode(200)
                .body("status", is("UP"))
                .body("checks", not(org.hamcrest.Matchers.nullValue()));
    }

    private Map<String, Object> loginRequest(String username, String password) {
        Map<String, Object> request = new HashMap<>();
        request.put("username", username);
        request.put("password", password);
        request.put("client", "angular-workshop");
        return request;
    }
}
