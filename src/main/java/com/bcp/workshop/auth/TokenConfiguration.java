package com.bcp.workshop.auth;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Optional;

@Startup
@ApplicationScoped
public class TokenConfiguration {
    private final SignedTokenIssuer issuer;

    @Inject
    public TokenConfiguration(@ConfigProperty(name = "auth.token.secret") Optional<String> configuredSecret) {
        byte[] secret = configuredSecret.orElse("").getBytes(StandardCharsets.UTF_8);
        issuer = new SignedTokenIssuer(secret, Clock.systemUTC());
    }

    @Produces
    @Singleton
    public SignedTokenIssuer issuer() {
        return issuer;
    }
}