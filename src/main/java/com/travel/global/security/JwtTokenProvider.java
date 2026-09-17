package com.travel.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration
    ) {
        this.secretKey = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );

        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    public String createAccessToken(
            Long userId,
            String email
    ) {
        return createToken(userId, email, "access", accessTokenExpiration);
    }

    public String createRefreshToken(Long userId, String email) {
        return createToken(userId, email, "refresh", refreshTokenExpiration);
    }

    private String createToken(
            Long userId,
            String email,
            String tokenType,
            long expirationMillis
    ) {
        Date now = new Date();
        Date expiration =
                new Date(now.getTime() + expirationMillis);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("tokenType", tokenType)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    public Long getUserId(String token) {
        Claims claims = parseClaims(token);

        return Long.valueOf(claims.getSubject());
    }

    public String getEmail(String token) {
        return parseClaims(token)
                .get("email", String.class);
    }

    public boolean validateToken(String token) {
        return validateTokenType(token, "access");
    }

    public boolean validateRefreshToken(String token) {
        return validateTokenType(token, "refresh");
    }

    private boolean validateTokenType(String token, String expectedType) {
        try {
            return expectedType.equals(
                    parseClaims(token).get("tokenType", String.class)
            );
        } catch (Exception e) {
            return false;
        }
    }

    public long getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
