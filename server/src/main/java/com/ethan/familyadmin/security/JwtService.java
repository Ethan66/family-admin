package com.ethan.familyadmin.security;

import com.ethan.familyadmin.family.FamilyMemberEntity;
import com.ethan.familyadmin.family.UserEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.util.Date;

@Component
public class JwtService {

    private final SecretKey key;
    private final Duration lifetime;
    private final Clock clock;

    /** 从后端配置加载签名密钥和有效期，统一使用 UTC 时钟签发令牌。 */
    @Autowired
    public JwtService(
        @Value("${family-admin.security.jwt-secret}") String secret,
        @Value("${family-admin.security.access-token-minutes}") long accessTokenMinutes
    ) {
        this(secret, accessTokenMinutes, Clock.systemUTC());
    }

    JwtService(String secret, long accessTokenMinutes, Clock clock) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.lifetime = Duration.ofMinutes(accessTokenMinutes);
        this.clock = clock;
    }

    /** 登录成功后签发携带账号和密码版本的令牌，不把家庭权限固化到令牌中。 */
    public String issue(UserEntity user, FamilyMemberEntity membership) {
        return sign(user.getId(), user.getTokenVersion());
    }

    /** 仅在过滤器核实账号、家庭和密码版本后，为当前请求签发新的有效期。 */
    public String renew(CurrentUser user) {
        return sign(user.userId(), user.tokenVersion());
    }

    private String sign(Long userId, int tokenVersion) {
        Instant now = clock.instant();
        return Jwts.builder()
            .subject(String.valueOf(userId))
            .claim("version", tokenVersion)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(lifetime)))
            .signWith(key)
            .compact();
    }

    /** 校验签名和过期时间；账号停用及密码版本由认证过滤器继续检查。 */
    public VerifiedToken verify(String token) {
        Claims claims = Jwts.parser().verifyWith(key).clock(() -> Date.from(clock.instant())).build()
            .parseSignedClaims(token)
            .getPayload();
        return new VerifiedToken(Long.valueOf(claims.getSubject()), claims.get("version", Integer.class));
    }

    public record VerifiedToken(Long userId, int tokenVersion) {
    }
}

