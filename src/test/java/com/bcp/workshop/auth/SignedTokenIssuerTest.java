package com.bcp.workshop.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.BadJWTException;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignedTokenIssuerTest {
    private static final byte[] TEST_KEY = "isolated-signing-fixture-key-32-bytes-minimum".getBytes(StandardCharsets.UTF_8);
    private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");
    private static final UserRecord USER = new UserRecord(1L, "fixture-user", "Fixture User", "STUDENT");

    @Test
    void signsRequiredClaimsAndPreservesClientAndRole() throws Exception {
        SignedTokenIssuer issuer = new SignedTokenIssuer(TEST_KEY, Clock.fixed(NOW, ZoneOffset.UTC));
        SignedTokenIssuer.IssuedToken issued = issuer.issue(USER, " angular-workshop ");
        SignedJWT token = SignedJWT.parse(issued.token());
        assertEquals(JWSAlgorithm.HS256, token.getHeader().getAlgorithm());
        assertTrue(token.verify(new MACVerifier(TEST_KEY)));
        JWTClaimsSet claims = token.getJWTClaimsSet();
        assertEquals(USER.username(), claims.getSubject());
        assertEquals(" angular-workshop ", claims.getStringClaim("client"));
        assertEquals(USER.role(), claims.getStringClaim("role"));
        assertEquals(NOW, claims.getIssueTime().toInstant());
        assertEquals(NOW.plusSeconds(3600), claims.getExpirationTime().toInstant());
        assertEquals(3600, issued.expiresIn());
    }

    @Test
    void rejectsAlteredPayloadAndWrongKey() throws Exception {
        SignedTokenIssuer issuer = new SignedTokenIssuer(TEST_KEY, Clock.fixed(NOW, ZoneOffset.UTC));
        SignedJWT original = SignedJWT.parse(issuer.issue(USER, "fixture-client").token());
        String changedPayload = new com.nimbusds.jose.Payload(
                new JWTClaimsSet.Builder(original.getJWTClaimsSet()).subject("altered-user").build().toJSONObject())
                .toBase64URL().toString();
        SignedJWT altered = SignedJWT.parse(original.getHeader().toBase64URL() + "." + changedPayload
                + "." + original.getSignature());
        assertFalse(altered.verify(new MACVerifier(TEST_KEY)));
        assertFalse(original.verify(new MACVerifier("different-isolated-signing-fixture-key-32-bytes-minimum")));
    }

    @Test
    void acceptsBeforeExpirationAndRejectsAtAndAfterExpiration() throws Exception {
        SignedTokenIssuer issuer = new SignedTokenIssuer(TEST_KEY, Clock.fixed(NOW, ZoneOffset.UTC));
        SignedJWT token = SignedJWT.parse(issuer.issue(USER, "fixture-client").token());
        assertTrue(token.verify(new MACVerifier(TEST_KEY)));
        JWTClaimsSet claims = token.getJWTClaimsSet();
        Instant expiresAt = claims.getExpirationTime().toInstant();
        assertDoesNotThrow(() -> verifierAt(expiresAt.minusSeconds(1)).verify(claims, null));
        assertThrows(BadJWTException.class, () -> verifierAt(expiresAt).verify(claims, null));
        assertThrows(BadJWTException.class, () -> verifierAt(expiresAt.plusSeconds(1)).verify(claims, null));
    }

    @Test
    void rejectsMissingOrShortSigningKey() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        assertThrows(IllegalArgumentException.class, () -> new SignedTokenIssuer(null, clock));
        assertThrows(IllegalArgumentException.class, () -> new SignedTokenIssuer(new byte[31], clock));
    }

    private DefaultJWTClaimsVerifier<SecurityContext> verifierAt(Instant instant) {
        DefaultJWTClaimsVerifier<SecurityContext> verifier = new DefaultJWTClaimsVerifier<>(
                new JWTClaimsSet.Builder().subject(USER.username()).build(),
                Set.of("sub", "client", "role", "iat", "exp")) {
            @Override
            protected Date currentTime() {
                return Date.from(instant);
            }
        };
        verifier.setMaxClockSkew(0);
        return verifier;
    }
}