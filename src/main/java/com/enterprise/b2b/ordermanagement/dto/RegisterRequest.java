package com.enterprise.b2b.ordermanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequest {
    private String username;
    private String password;
    private String email;
    private String role; // Map to UserRole enum (e.g. ROLE_CLIENT, ROLE_ADMIN)
}
