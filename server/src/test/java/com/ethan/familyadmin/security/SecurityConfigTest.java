package com.ethan.familyadmin.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    @Test
    void allowsTauriDesktopOriginsToCallTheApi() {
        CorsConfigurationSource source = new SecurityConfig().corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/auth/login");

        CorsConfiguration configuration = source.getCorsConfiguration(request);

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins())
            .contains("http://localhost:1420", "http://tauri.localhost", "tauri://localhost");
        assertThat(configuration.getAllowedMethods()).contains("POST", "OPTIONS");
        assertThat(configuration.getAllowedHeaders()).contains("Content-Type", "Authorization", "X-Family-Id");
    }
}
