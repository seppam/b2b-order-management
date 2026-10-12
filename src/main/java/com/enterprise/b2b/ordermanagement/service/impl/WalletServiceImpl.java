package com.enterprise.b2b.ordermanagement.service.impl;

import com.enterprise.b2b.ordermanagement.entity.User;
import com.enterprise.b2b.ordermanagement.exception.ResourceNotFoundException;
import com.enterprise.b2b.ordermanagement.repository.UserRepository;
import com.enterprise.b2b.ordermanagement.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public User topUp(Long userId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Top-up amount must be greater than zero");
        }
        User user = find(userId);
        user.setWalletBalance(user.getWalletBalance().add(amount));
        return userRepository.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getBalance(Long userId) {
        return find(userId).getWalletBalance();
    }

    private User find(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
    }
}
