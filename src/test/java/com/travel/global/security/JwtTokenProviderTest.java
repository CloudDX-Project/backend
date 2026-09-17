package com.travel.global.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenProviderTest {

    private final JwtTokenProvider provider = new JwtTokenProvider(
            "test-secret-key-that-is-long-enough-for-hmac-sha-256",
            60_000,
            120_000
    );

    @Test
    void accessAndRefreshTokensAreNotInterchangeable() {
        String accessToken = provider.createAccessToken(1L, "user@example.com");
        String refreshToken = provider.createRefreshToken(1L, "user@example.com");

        assertTrue(provider.validateToken(accessToken));
        assertFalse(provider.validateRefreshToken(accessToken));
        assertTrue(provider.validateRefreshToken(refreshToken));
        assertFalse(provider.validateToken(refreshToken));
    }
}
