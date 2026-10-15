package com.taskflow.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final Duration expiration;

    public JwtService(
            @Value("${taskflow.jwt.secret-base64}") String secretBase64,
            @Value("${taskflow.jwt.expiration}") Duration expiration) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretBase64));
        this.expiration = expiration;
        if (expiration.isZero() || expiration.isNegative()) {
            throw new IllegalArgumentException("JWT expiration must be positive.");
        }
    }

    public String generateToken(String subject, Instant issuedAt) {
        Instant expiresAt = issuedAt.plus(expiration);
        return Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    public String extractSubject(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    public Instant expiresAt(Instant issuedAt) {
        return issuedAt.plus(expiration);
    }
}
