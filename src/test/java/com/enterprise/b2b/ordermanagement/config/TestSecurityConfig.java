package com.enterprise.b2b.ordermanagement.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Test-only security configuration for controller integration tests.
 *
 * <p>Overrides the production SecurityConfig by permitting all HTTP requests
 * without authentication. This allows @WebMvcTest slices to exercise the
 * OrderController endpoints without JWT token generation overhead.
 *
 * <p>The production SecurityConfig remains untouched — only active during tests
 * via the @Import annotation on each test class that needs it.
 */
@TestConfiguration
public class TestSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeRequests()
                .anyRequest().permitAll();
        return http.build();
    }
}
