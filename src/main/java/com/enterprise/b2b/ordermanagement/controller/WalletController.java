package com.enterprise.b2b.ordermanagement.controller;

import com.enterprise.b2b.ordermanagement.dto.TopUpRequest;
import com.enterprise.b2b.ordermanagement.entity.User;
import com.enterprise.b2b.ordermanagement.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    /** Admin-only: add credit to a client's wallet. */
    @PostMapping("/{userId}/wallet/topup")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> topUp(@PathVariable Long userId, @RequestBody TopUpRequest request) {
        User user = walletService.topUp(userId, request.getAmount());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", user.getId());
        body.put("walletBalance", user.getWalletBalance());
        return ResponseEntity.ok(body);
    }

    /** A client may read their own balance; admins any. */
    @GetMapping("/{userId}/wallet")
    @PreAuthorize("@orderAccess.canAccess(authentication, #userId)")
    public ResponseEntity<Map<String, Object>> balance(@PathVariable Long userId) {
        BigDecimal balance = walletService.getBalance(userId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", userId);
        body.put("walletBalance", balance);
        return ResponseEntity.ok(body);
    }
}
