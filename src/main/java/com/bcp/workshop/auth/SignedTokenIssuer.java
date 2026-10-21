package com.bcp.workshop.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;

public class SignedTokenIssuer {
    private static final long TOKEN_TTL_SECONDS = 3600;
    private static final int MINIMUM_KEY_BYTES = 32;
    private final byte[] secret;
    private final Clock clock;

    public SignedTokenIssuer(byte[] secret, Clock clock) {
        if (secret == null || secret.length < MINIMUM_KEY_BYTES) {
            throw new IllegalArgumentException("AUTH_TOKEN_SECRET must contain at least 32 bytes");
        }
        this.secret = secret.clone();
        this.clock = clock;
    }

    public IssuedToken issue(UserRecord user, String client) {
        Instant issuedAt = Instant.ofEpochSecond(clock.instant().getEpochSecond());
        Instant expiresAt = issuedAt.plusSeconds(TOKEN_TTL_SECONDS);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(user.username())
                .claim("client", client)
                .claim("role", user.role())
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .build();
        SignedJWT token = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(), claims);
        try {
            token.sign(new MACSigner(secret));
        } catch (JOSEException error) {
            throw new IllegalStateException("Token signing failed");
        }
        long expiresIn = expiresAt.getEpochSecond() - clock.instant().getEpochSecond();
        if (expiresIn <= 0) {
            throw new IllegalStateException("Token expired before response");
        }
        return new IssuedToken(token.serialize(), expiresIn);
    }

    public record IssuedToken(String token, long expiresIn) {}
}