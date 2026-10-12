package com.enterprise.b2b.ordermanagement.service.impl;

import com.enterprise.b2b.ordermanagement.entity.Order;
import com.enterprise.b2b.ordermanagement.entity.OrderStatus;
import com.enterprise.b2b.ordermanagement.entity.Product;
import com.enterprise.b2b.ordermanagement.entity.User;
import com.enterprise.b2b.ordermanagement.exception.InsufficientBalanceException;
import com.enterprise.b2b.ordermanagement.exception.OutOfStockException;
import com.enterprise.b2b.ordermanagement.exception.ResourceNotFoundException;
import com.enterprise.b2b.ordermanagement.repository.OrderRepository;
import com.enterprise.b2b.ordermanagement.repository.ProductRepository;
import com.enterprise.b2b.ordermanagement.repository.UserRepository;
import com.enterprise.b2b.ordermanagement.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public Order createOrder(Long userId, Long productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }

        // 1. Retrieve purchasing User
        // Note: For concurrent production deployments, consider pessimistic write locks (SELECT ... FOR UPDATE)
        // or optimistic locking (@Version in entity) to guarantee serialization and prevent double-spending.
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        // 2. Retrieve Product
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        // 3. Verify stock availability
        if (product.getStockQuantity() < quantity) {
            throw new OutOfStockException(String.format("Product '%s' is out of stock. Requested: %d, Available: %d",
                    product.getName(), quantity, product.getStockQuantity()));
        }

        // 4. Calculate total price (using BigDecimal scale and rounding to preserve financial precision)
        BigDecimal totalPrice = product.getPrice().multiply(BigDecimal.valueOf(quantity));

        // 5. Verify user's balance
        if (user.getWalletBalance().compareTo(totalPrice) < 0) {
            throw new InsufficientBalanceException(String.format("User balance is insufficient. Required: %s, Available: %s",
                    totalPrice.toPlainString(), user.getWalletBalance().toPlainString()));
        }

        // 6. Deduct assets
        user.setWalletBalance(user.getWalletBalance().subtract(totalPrice));
        product.setStockQuantity(product.getStockQuantity() - quantity);

        // 7. Flush state modifications to database
        userRepository.save(user);
        productRepository.save(product);

        // 8. Record transaction order
        Order order = Order.builder()
                .user(user)
                .totalAmount(totalPrice)
                .status(OrderStatus.PAID) // Immediately marked PAID as wallet deduction succeeded
                .build();

        return orderRepository.save(order);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> getOrdersByUserId(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }
        return orderRepository.findByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Order getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
    }
}
