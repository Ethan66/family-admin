package com.ethan.familyadmin.security;

import com.ethan.familyadmin.family.UserEntity;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {
    private final String secret = UUID.randomUUID().toString().repeat(2);
    private final Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

    @Test void loginAndRenewalEachExpireOneDayAfterIssuance() {
        var user = new UserEntity(); user.setId(7L); user.setTokenVersion(3);
        var service = new JwtService(secret, 1440, Clock.fixed(now, ZoneOffset.UTC));
        var original = service.issue(user, null);
        var later = new JwtService(secret, 1440, Clock.fixed(now.plusSeconds(3600), ZoneOffset.UTC));
        var renewed = later.renew(new CurrentUser(7L, 1L, Role.FAMILY_MEMBER, 3));
        var parser = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).build();
        assertThat(parser.parseSignedClaims(original).getPayload().getExpiration().toInstant()).isEqualTo(now.plus(Duration.ofDays(1)));
        assertThat(parser.parseSignedClaims(renewed).getPayload().getExpiration().toInstant()).isEqualTo(now.plusSeconds(3600).plus(Duration.ofDays(1)));
        assertThat(later.verify(renewed)).isEqualTo(new JwtService.VerifiedToken(7L, 3));
        var afterOriginalExpiry = new JwtService(secret, 1440, Clock.fixed(now.plus(Duration.ofDays(1)).plusSeconds(1), ZoneOffset.UTC));
        assertThatThrownBy(() -> afterOriginalExpiry.verify(original)).isInstanceOf(ExpiredJwtException.class);
        assertThat(afterOriginalExpiry.verify(renewed)).isEqualTo(new JwtService.VerifiedToken(7L, 3));
    }

    @Test void expiredTokenCannotAuthenticateAfterOneDayOfInactivity() {
        var user = new UserEntity(); user.setId(7L); user.setTokenVersion(3);
        var service = new JwtService(secret, 1440, Clock.fixed(now.minus(Duration.ofDays(2)), ZoneOffset.UTC));
        assertThatThrownBy(() -> new JwtService(secret, 1440).verify(service.issue(user, null)))
            .isInstanceOf(ExpiredJwtException.class);
    }
}
