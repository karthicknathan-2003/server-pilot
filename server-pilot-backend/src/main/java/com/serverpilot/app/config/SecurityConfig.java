package com.serverpilot.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configures security for the application. No user authentication is required.
 * The SSH connection is used to authenticate access to the target server.
 * All API endpoints are currently open, and access depends on a successful SSH connection.
 *
 * @author karthicknathan
 * @since 08 Sep, 2026
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${cors.allowed-origin}")
    private String allowedOrigin;

    /**
     * Defines the rules Spring Security applies to every HTTP request.
     *
     * @param http Spring Security's builder for HTTP security rules.
     *
     * @return The constructed {@link SecurityFilterChain}.
     *
     * @throws Exception If any part of the security configuration fails to build.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsSource()))
                .authorizeHttpRequests(
                        auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /**
     * Configures which origins, methods, and headers are allowed to call this API.
     *
     * @return A {@link CorsConfigurationSource} applied to all {@code /**} routes.
     */
    @Bean
    public CorsConfigurationSource corsSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Allow requests from the deployed or local dev servers.
        config.setAllowedOrigins(List.of(allowedOrigin));
        // Standard REST methods — OPTIONS is required for preflight requests
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        // Allow all request headers (Content-Type, Authorization, etc.)
        config.setAllowedHeaders(List.of("*"));
        // Must be true for cookies to be included in cross-origin requests.
        config.setAllowCredentials(true);
        // Apply this config to every endpoint in the application.
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}