package com.taskflow.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    @Test
    void tokenRoundTripsSubjectAndExpiration() {
        byte[] secret = new byte[32];
        new Random(7).nextBytes(secret);
        JwtService jwtService = new JwtService(
                Base64.getEncoder().encodeToString(secret), Duration.ofMinutes(15));
        Instant issuedAt = Instant.now().minusSeconds(1);

        String token = jwtService.generateToken("user@example.com", issuedAt);

        assertEquals("user@example.com", jwtService.extractSubject(token));
        assertEquals(issuedAt.plus(Duration.ofMinutes(15)), jwtService.expiresAt(issuedAt));
    }

    @Test
    void rejectsExpiredTokensAndTokensSignedByAnotherKey() {
        JwtService jwtService = new JwtService(secret(1), Duration.ofMinutes(15));
        JwtService differentKeyService = new JwtService(secret(2), Duration.ofMinutes(15));
        String expiredToken = jwtService.generateToken(
                "user@example.com", Instant.now().minus(Duration.ofHours(1)));
        String currentToken = jwtService.generateToken("user@example.com", Instant.now());

        assertThrows(io.jsonwebtoken.ExpiredJwtException.class,
                () -> jwtService.extractSubject(expiredToken));
        assertThrows(io.jsonwebtoken.JwtException.class,
                () -> differentKeyService.extractSubject(currentToken));
    }

    private String secret(long seed) {
        byte[] secret = new byte[32];
        new Random(seed).nextBytes(secret);
        return Base64.getEncoder().encodeToString(secret);
    }
}
