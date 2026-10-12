package com.ethan.familyadmin.security;

import com.ethan.familyadmin.family.FamilyMemberEntity;
import com.ethan.familyadmin.family.UserEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtService {

    private final SecretKey key;
    private final Duration lifetime;

    public JwtService(
        @Value("${family-admin.security.jwt-secret}") String secret,
        @Value("${family-admin.security.access-token-minutes}") long accessTokenMinutes
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.lifetime = Duration.ofMinutes(accessTokenMinutes);
    }

    public String issue(UserEntity user, FamilyMemberEntity membership) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(String.valueOf(user.getId()))
            .claim("version", user.getTokenVersion())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(lifetime)))
            .signWith(key)
            .compact();
    }

    public VerifiedToken verify(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build()
            .parseSignedClaims(token)
            .getPayload();
        return new VerifiedToken(Long.valueOf(claims.getSubject()), claims.get("version", Integer.class));
    }

    public record VerifiedToken(Long userId, int tokenVersion) {
    }
}

