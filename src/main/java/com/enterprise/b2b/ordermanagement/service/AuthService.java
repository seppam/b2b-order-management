package com.enterprise.b2b.ordermanagement.service;

import com.enterprise.b2b.ordermanagement.dto.AuthResponse;
import com.enterprise.b2b.ordermanagement.dto.LoginRequest;
import com.enterprise.b2b.ordermanagement.dto.RegisterRequest;

public interface AuthService {
    /**
     * Register a new user with BCrypt password hashing.
     *
     * @param request Registration payload details
     */
    void registerUser(RegisterRequest request);

    /**
     * Authenticate user credentials and return a signed JWT access token.
     *
     * @param request Authentication credentials
     * @return Authentication response with JWT token details
     */
    AuthResponse loginUser(LoginRequest request);
}
