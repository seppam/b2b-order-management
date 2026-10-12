package com.enterprise.b2b.ordermanagement.service;

import com.enterprise.b2b.ordermanagement.entity.Order;

import java.util.List;

public interface OrderService {
    /**
     * Executes order logic atomically: validates stock/balances, deducts assets, and saves transactions.
     *
     * @param userId The User making the purchase
     * @param productId The Product being bought
     * @param quantity Number of units purchased
     * @return Generated Order record
     */
    Order createOrder(Long userId, Long productId, int quantity);

    /**
     * Retrieve all orders initiated by a specific user.
     *
     * @param userId The User identifier
     * @return List of user orders
     */
    List<Order> getOrdersByUserId(Long userId);

    /**
     * Retrieve an order by its primary identifier.
     *
     * @param orderId The Order identifier
     * @return The Order record
     */
    Order getOrderById(Long orderId);
}
