package com.enterprise.b2b.ordermanagement.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true) // Allows annotations like @PreAuthorize
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Industry-standard secure password hashing using BCrypt
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtTokenProvider jwtTokenProvider) throws Exception {
        http
            // 1. Disable CSRF as we use stateless JWT authentication
            .csrf().disable()
            
            // 2. Configure CORS settings (adjust origins as needed for production)
            .cors().and()
            
            // 3. Keep sessions stateless since JWT is used for each request
            .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
            
            // 4. Exception Handling configurations (unauthorized, access denied)
            .exceptionHandling()
                .authenticationEntryPoint((req, res, ex) -> res.sendError(401, "Unauthorized"))
                .and()
                
            // 5. URL Authorization rules
            .authorizeRequests()
                .antMatchers("/api/v1/auth/**").permitAll() // Authenticate/Register endpoints
                .antMatchers("/api/v1/public/**").permitAll() // Public endpoints
                .antMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll() // OpenAPI docs
                .anyRequest().authenticated(); // All other endpoints require authentication

        // 6. JWT Authentication Filter, registered before the username/password filter
        http.addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
