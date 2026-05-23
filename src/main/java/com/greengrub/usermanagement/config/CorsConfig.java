package com.greengrub.usermanagement.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS configuration driven by the active Spring profile.
 *
 * - local: application-local.properties sets allowed-origins to the React dev servers
 *   (CRA :3000, Vite :5173) so the frontend can call this service directly during development.
 * - k8s:  application-k8s.yml binds allowed-origins to the CORS_ALLOWED_ORIGINS env var
 *   (comma-separated). Empty default = no Access-Control-Allow-Origin header emitted,
 *   which is the expected production posture since the API Gateway is the only legitimate
 *   external caller.
 */
@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins:}")
    private List<String> allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
            config.setAllowedOrigins(allowedOrigins);
            config.setAllowCredentials(true);
        }

        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
