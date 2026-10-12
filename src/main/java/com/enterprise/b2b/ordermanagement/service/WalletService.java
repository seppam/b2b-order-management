package com.enterprise.b2b.ordermanagement.service;

import com.enterprise.b2b.ordermanagement.entity.User;

import java.math.BigDecimal;

public interface WalletService {
    User topUp(Long userId, BigDecimal amount);

    BigDecimal getBalance(Long userId);
}
