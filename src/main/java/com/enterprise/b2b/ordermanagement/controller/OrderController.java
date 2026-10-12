package com.enterprise.b2b.ordermanagement.controller;

import com.enterprise.b2b.ordermanagement.dto.OrderRequest;
import com.enterprise.b2b.ordermanagement.entity.Order;
import com.enterprise.b2b.ordermanagement.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @PreAuthorize("@orderAccess.canAccess(authentication, #request.userId)")
    public ResponseEntity<Order> createOrder(@RequestBody OrderRequest request) {
        Order order = orderService.createOrder(request.getUserId(), request.getProductId(), request.getQuantity());
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    // Support query parameters for placing orders as specified in the request
    @PreAuthorize("@orderAccess.canAccess(authentication, #userId)")
    @PostMapping(params = {"userId", "productId", "quantity"})
    public ResponseEntity<Order> createOrderByParams(
            @RequestParam Long userId,
            @RequestParam Long productId,
            @RequestParam int quantity) {
        Order order = orderService.createOrder(userId, productId, quantity);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("@orderAccess.canAccess(authentication, #userId)")
    public ResponseEntity<List<Order>> getOrdersByUserId(@PathVariable Long userId) {
        List<Order> orders = orderService.getOrdersByUserId(userId);
        return ResponseEntity.ok(orders);
    }
}
