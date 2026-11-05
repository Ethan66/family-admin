package com.ethan.familyadmin.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.assertThat;

class TokenRenewalCorsTest {
    @Test void desktopCanReadRenewedTokenHeaderAcrossOrigins() {
        var request = new MockHttpServletRequest("GET", "/api/auth/me");
        var config = new SecurityConfig().corsConfigurationSource().getCorsConfiguration(request);
        assertThat(config.getExposedHeaders()).contains("X-Access-Token");
        assertThat(config.getAllowedOrigins()).contains("http://tauri.localhost", "tauri://localhost");
    }
}
