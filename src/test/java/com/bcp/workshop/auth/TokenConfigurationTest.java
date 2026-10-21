package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TokenConfigurationTest {
    @Test
    void rejectsAbsentEmptyAndShortSecretsWithoutExposingThem() {
        for (Optional<String> secret : java.util.List.of(Optional.<String>empty(), Optional.of(""), Optional.of("short-fixture"))) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> new TokenConfiguration(secret));
            assertEquals("AUTH_TOKEN_SECRET must contain at least 32 bytes", error.getMessage());
        }
    }

    @Test
    void acceptsAtLeastThirtyTwoBytes() {
        TokenConfiguration configuration = new TokenConfiguration(Optional.of("x".repeat(32)));
        assertNotNull(configuration.issuer());
    }

    @Test
    void confinesConfiguredFixturesToDevAndTestProfiles() throws Exception {
        Properties properties = new Properties();
        try (var resource = getClass().getResourceAsStream("/application.properties")) {
            assertNotNull(resource);
            properties.load(resource);
        }
        assertEquals("${AUTH_TOKEN_SECRET:}", properties.getProperty("auth.token.secret"));
        assertNotNull(properties.getProperty("%dev.auth.token.secret"));
        assertNotNull(properties.getProperty("%test.auth.token.secret"));
        assertEquals(null, properties.getProperty("%prod.auth.token.secret"));
    }
}