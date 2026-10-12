package com.enterprise.b2b.ordermanagement.security;

import com.enterprise.b2b.ordermanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Authorization rule: admins may act for any user, clients only for themselves.
 */
@Component("orderAccess")
@RequiredArgsConstructor
public class OrderAccess {

    private final UserRepository userRepository;

    public boolean canAccess(Authentication authentication, Long userId) {
        if (authentication == null || !authentication.isAuthenticated() || userId == null) {
            return false;
        }
        if (authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            return true;
        }
        return userRepository.findByUsername(authentication.getName())
                .map(u -> userId.equals(u.getId()))
                .orElse(false);
    }
}
