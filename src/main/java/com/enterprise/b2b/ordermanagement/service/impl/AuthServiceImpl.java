package com.enterprise.b2b.ordermanagement.service.impl;

import com.enterprise.b2b.ordermanagement.dto.AuthResponse;
import com.enterprise.b2b.ordermanagement.dto.LoginRequest;
import com.enterprise.b2b.ordermanagement.dto.RegisterRequest;
import com.enterprise.b2b.ordermanagement.entity.User;
import com.enterprise.b2b.ordermanagement.entity.UserRole;
import com.enterprise.b2b.ordermanagement.exception.ResourceNotFoundException;
import com.enterprise.b2b.ordermanagement.repository.UserRepository;
import com.enterprise.b2b.ordermanagement.security.JwtTokenProvider;
import com.enterprise.b2b.ordermanagement.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    @Transactional
    public void registerUser(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username is already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email is already in use");
        }

        // Map role from request, defaulting to CLIENT
        UserRole userRole = UserRole.ROLE_CLIENT;
        if (request.getRole() != null) {
            try {
                userRole = UserRole.valueOf(request.getRole().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid user role: " + request.getRole());
            }
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .role(userRole)
                .walletBalance(BigDecimal.ZERO) // Bootstrapping wallet with empty balance
                .build();

        userRepository.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse loginUser(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + request.getUsername()));

        // BCrypt validation against hashed database record
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        // Set up the principal and permission set representation for the JWT provider
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                user.getUsername(),
                null,
                Collections.singletonList(new SimpleGrantedAuthority(user.getRole().name()))
        );

        String token = jwtTokenProvider.generateToken(authentication);

        return AuthResponse.builder()
                .accessToken(token)
                .username(user.getUsername())
                .userId(user.getId())
                .build();
    }
}
