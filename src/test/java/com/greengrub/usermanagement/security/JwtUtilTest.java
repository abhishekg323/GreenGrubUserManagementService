package com.greengrub.usermanagement.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.assertj.core.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    private static final String SECRET =
            "test-secret-key-that-is-at-least-256-bits-long-for-hs256-algorithm";
    private static final long EXPIRATION_MS = 3_600_000L; // 1 hour

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expiration", EXPIRATION_MS);
    }

    @Test
    void generateToken_returnsNonBlankToken() {
        String token = jwtUtil.generateToken("user-001", "john@example.com", "DONOR");
        assertThat(token).isNotBlank();
    }

    @Test
    void extractUsername_returnsEmail() {
        String token = jwtUtil.generateToken("user-001", "john@example.com", "DONOR");
        assertThat(jwtUtil.extractUsername(token)).isEqualTo("john@example.com");
    }

    @Test
    void extractUserId_returnsUserId() {
        String token = jwtUtil.generateToken("user-001", "john@example.com", "DONOR");
        assertThat(jwtUtil.extractUserId(token)).isEqualTo("user-001");
    }

    @Test
    void extractRole_returnsRole() {
        String token = jwtUtil.generateToken("user-001", "john@example.com", "DONOR");
        assertThat(jwtUtil.extractRole(token)).isEqualTo("DONOR");
    }

    @Test
    void extractExpiration_returnsDateInFuture() {
        String token = jwtUtil.generateToken("user-001", "john@example.com", "DONOR");
        Date expiration = jwtUtil.extractExpiration(token);
        assertThat(expiration).isAfter(new Date());
    }

    @Test
    void validateToken_validTokenAndMatchingEmail_returnsTrue() {
        String token = jwtUtil.generateToken("user-001", "john@example.com", "DONOR");
        assertThat(jwtUtil.validateToken(token, "john@example.com")).isTrue();
    }

    @Test
    void validateToken_wrongEmail_returnsFalse() {
        String token = jwtUtil.generateToken("user-001", "john@example.com", "DONOR");
        assertThat(jwtUtil.validateToken(token, "other@example.com")).isFalse();
    }

    @Test
    void validateToken_expiredToken_throwsExpiredJwtException() {
        ReflectionTestUtils.setField(jwtUtil, "expiration", -1000L);
        String token = jwtUtil.generateToken("user-001", "john@example.com", "DONOR");
        assertThatThrownBy(() -> jwtUtil.validateToken(token, "john@example.com"))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void extractUsername_tamperedToken_throwsSignatureException() {
        String token = jwtUtil.generateToken("user-001", "john@example.com", "DONOR");
        String tampered = token.substring(0, token.length() - 4) + "xxxx";
        assertThatThrownBy(() -> jwtUtil.extractUsername(tampered))
                .isInstanceOf(Exception.class);
    }

    @Test
    void generateToken_differentUsersProduceDifferentTokens() {
        String t1 = jwtUtil.generateToken("user-001", "a@example.com", "DONOR");
        String t2 = jwtUtil.generateToken("user-002", "b@example.com", "RECIPIENT");
        assertThat(t1).isNotEqualTo(t2);
    }

    @Test
    void extractUserId_adminRole_returnsCorrectId() {
        String token = jwtUtil.generateToken("admin-999", "admin@example.com", "ADMIN");
        assertThat(jwtUtil.extractUserId(token)).isEqualTo("admin-999");
        assertThat(jwtUtil.extractRole(token)).isEqualTo("ADMIN");
    }
}
