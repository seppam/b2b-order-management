package com.enterprise.b2b.ordermanagement.controller;

import com.enterprise.b2b.ordermanagement.config.TestSecurityConfig;
import com.enterprise.b2b.ordermanagement.dto.OrderRequest;
import com.enterprise.b2b.ordermanagement.entity.Order;
import com.enterprise.b2b.ordermanagement.entity.OrderStatus;
import com.enterprise.b2b.ordermanagement.entity.Product;
import com.enterprise.b2b.ordermanagement.entity.User;
import com.enterprise.b2b.ordermanagement.entity.UserRole;
import com.enterprise.b2b.ordermanagement.exception.GlobalExceptionHandler;
import com.enterprise.b2b.ordermanagement.exception.InsufficientBalanceException;
import com.enterprise.b2b.ordermanagement.exception.OutOfStockException;
import com.enterprise.b2b.ordermanagement.exception.ResourceNotFoundException;
import com.enterprise.b2b.ordermanagement.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for OrderController using MockMvc.
 *
 * <p>Uses @WebMvcTest to slice the Spring context to only web layer beans.
 * Security is disabled for these tests (via custom security test config) so that
 * the controller can be exercised without JWT authentication overhead.
 *
 * <p>Enterprise mock data is used to avoid colliding with manual verification records.
 */
@WebMvcTest(controllers = OrderController.class)
@Import({GlobalExceptionHandler.class, TestSecurityConfig.class})
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    // ── Enterprise Mock Entities ──────────────────────────────────────────────

    private User bulkBuyerOmega;
    private Product saasSub12;

    @BeforeEach
    void setUp() {
        bulkBuyerOmega = User.builder()
                .id(1L)
                .username("bulk_buyer_omega")
                .password("SecureOmega99$")
                .email("omega@enterprise.com")
                .role(UserRole.ROLE_CLIENT)
                .walletBalance(new BigDecimal("15000.00"))
                .build();

        saasSub12 = Product.builder()
                .id(2L)
                .name("SaaS Premium Sub")
                .sku("SAAS-SUB-12")
                .price(new BigDecimal("299.00"))
                .stockQuantity(150)
                .build();
    }

    // ── Helper Builders ─────────────────────────────────────────────────────────

    private Order buildOrder(User user, BigDecimal totalAmount, OrderStatus status) {
        return Order.builder()
                .id(100L)
                .user(user)
                .totalAmount(totalAmount)
                .status(status)
                .build();
    }

    private OrderRequest buildOrderRequest(Long userId, Long productId, Integer quantity) {
        return OrderRequest.builder()
                .userId(userId)
                .productId(productId)
                .quantity(quantity)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCENARIO 1 ─ Successful Checkout
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Scenario 1: Successful Checkout")
    class SuccessfulCheckoutTests {

        @Test
        @DisplayName("POST /api/v1/orders → 201 CREATED when balance and stock are sufficient")
        void createOrder_SufficientBalanceAndStock_Returns201Created() throws Exception {
            // Arrange: buying 10 units of SAAS-SUB-12 @ 299.00 = 2,990.00
            // Expected remaining wallet balance: 15,000.00 - 2,990.00 = 12,010.00
            Order savedOrder = buildOrder(bulkBuyerOmega, new BigDecimal("2990.00"), OrderStatus.PAID);
            when(orderService.createOrder(1L, 2L, 10)).thenReturn(savedOrder);

            OrderRequest request = buildOrderRequest(1L, 2L, 10);
            String payload = objectMapper.writeValueAsString(request);

            // Act & Assert
            mockMvc.perform(post("/api/v1/orders")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(100))
                    .andExpect(jsonPath("$.totalAmount").value(2990.00))
                    .andExpect(jsonPath("$.status").value("PAID"));
        }

        @Test
        @DisplayName("POST /api/v1/orders with query params → 201 CREATED")
        void createOrderByParams_SufficientBalanceAndStock_Returns201Created() throws Exception {
            Order savedOrder = buildOrder(bulkBuyerOmega, new BigDecimal("2990.00"), OrderStatus.PAID);
            when(orderService.createOrder(1L, 2L, 10)).thenReturn(savedOrder);

            mockMvc.perform(post("/api/v1/orders")
                            .with(csrf())
                            .param("userId", "1")
                            .param("productId", "2")
                            .param("quantity", "10"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(100))
                    .andExpect(jsonPath("$.totalAmount").value(2990.00))
                    .andExpect(jsonPath("$.status").value("PAID"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCENARIO 2 ─ Insufficient Balance
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Scenario 2: Insufficient Balance Failure")
    class InsufficientBalanceTests {

        @Test
        @DisplayName("POST /api/v1/orders → 400 BAD REQUEST when wallet balance is insufficient")
        void createOrder_InsufficientBalance_Returns400BadRequest() throws Exception {
            // Arrange: requesting 100 units @ 299.00 = 29,900.00 against balance of 15,000.00
            String expectedMessage = "User balance is insufficient. Required: 29900.00, Available: 15000.00";
            when(orderService.createOrder(1L, 2L, 100))
                    .thenThrow(new InsufficientBalanceException(expectedMessage));

            OrderRequest request = buildOrderRequest(1L, 2L, 100);
            String payload = objectMapper.writeValueAsString(request);

            // Act & Assert
            mockMvc.perform(post("/api/v1/orders")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message", containsString("insufficient")));
        }

        @Test
        @DisplayName("GlobalExceptionHandler formats InsufficientBalanceException with correct structure")
        void createOrder_InsufficientBalance_ExceptionHandlerFormatsCorrectly() throws Exception {
            when(orderService.createOrder(anyLong(), anyLong(), anyInt()))
                    .thenThrow(new InsufficientBalanceException(
                            "User balance is insufficient. Required: 29900.00, Available: 15000.00"));

            OrderRequest request = buildOrderRequest(1L, 2L, 100);
            String payload = objectMapper.writeValueAsString(request);

            mockMvc.perform(post("/api/v1/orders")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.message",
                            containsString("29900.00")))
                    .andExpect(jsonPath("$.message",
                            containsString("15000.00")));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCENARIO 3 ─ Out of Stock
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Scenario 3: Out of Stock Failure")
    class OutOfStockTests {

        @Test
        @DisplayName("POST /api/v1/orders → 400 BAD REQUEST when requested quantity exceeds stock")
        void createOrder_OutOfStock_Returns400BadRequest() throws Exception {
            // Arrange: requesting 200 units against available stock of 150
            String expectedMessage = "Product 'SaaS Premium Sub' is out of stock. Requested: 200, Available: 150";
            when(orderService.createOrder(1L, 2L, 200))
                    .thenThrow(new OutOfStockException(expectedMessage));

            OrderRequest request = buildOrderRequest(1L, 2L, 200);
            String payload = objectMapper.writeValueAsString(request);

            // Act & Assert
            mockMvc.perform(post("/api/v1/orders")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message", containsString("out of stock")));
        }

        @Test
        @DisplayName("OutOfStockException returns exact product name and stock mismatch in message")
        void createOrder_OutOfStock_MessageContainsProductAndStockDetails() throws Exception {
            when(orderService.createOrder(anyLong(), anyLong(), anyInt()))
                    .thenThrow(new OutOfStockException(
                            "Product 'SaaS Premium Sub' is out of stock. Requested: 200, Available: 150"));

            OrderRequest request = buildOrderRequest(1L, 2L, 200);
            String payload = objectMapper.writeValueAsString(request);

            mockMvc.perform(post("/api/v1/orders")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("SaaS Premium Sub")))
                    .andExpect(jsonPath("$.message", containsString("200")))
                    .andExpect(jsonPath("$.message", containsString("150")));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCENARIO 4 ─ Resource Not Found (User / Product)
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Scenario 4: Resource Not Found — User / Product")
    class ResourceNotFoundTests {

        @Test
        @DisplayName("POST /api/v1/orders → 404 NOT FOUND when user does not exist")
        void createOrder_UserNotFound_Returns404NotFound() throws Exception {
            when(orderService.createOrder(anyLong(), anyLong(), anyInt()))
                    .thenThrow(new ResourceNotFoundException("User not found with id: 999"));

            OrderRequest request = buildOrderRequest(999L, 2L, 10);
            String payload = objectMapper.writeValueAsString(request);

            mockMvc.perform(post("/api/v1/orders")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message", containsString("User not found")));
        }

        @Test
        @DisplayName("POST /api/v1/orders → 404 NOT FOUND when product does not exist")
        void createOrder_ProductNotFound_Returns404NotFound() throws Exception {
            when(orderService.createOrder(anyLong(), anyLong(), anyInt()))
                    .thenThrow(new ResourceNotFoundException("Product not found with id: 999"));

            OrderRequest request = buildOrderRequest(1L, 999L, 10);
            String payload = objectMapper.writeValueAsString(request);

            mockMvc.perform(post("/api/v1/orders")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message", containsString("Product not found")));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCENARIO 5 ─ Validation Edge Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Scenario 5: Validation Edge Cases")
    class ValidationEdgeCaseTests {

        @Test
        @DisplayName("POST /api/v1/orders → 400 BAD REQUEST when quantity is zero")
        void createOrder_ZeroQuantity_Returns400BadRequest() throws Exception {
            when(orderService.createOrder(anyLong(), anyLong(), anyInt()))
                    .thenThrow(new IllegalArgumentException("Quantity must be greater than zero"));

            OrderRequest request = buildOrderRequest(1L, 2L, 0);
            String payload = objectMapper.writeValueAsString(request);

            mockMvc.perform(post("/api/v1/orders")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message", containsString("greater than zero")));
        }

        @Test
        @DisplayName("POST /api/v1/orders → 400 BAD REQUEST when quantity is negative")
        void createOrder_NegativeQuantity_Returns400BadRequest() throws Exception {
            when(orderService.createOrder(anyLong(), anyLong(), anyInt()))
                    .thenThrow(new IllegalArgumentException("Quantity must be greater than zero"));

            OrderRequest request = buildOrderRequest(1L, 2L, -5);
            String payload = objectMapper.writeValueAsString(request);

            mockMvc.perform(post("/api/v1/orders")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("POST /api/v1/orders with missing userId → IllegalArgumentException → 400 BAD REQUEST")
        void createOrder_MissingUserId_Returns400() throws Exception {
            // When Jackson deserializes {"productId":2,"quantity":10}, userId defaults to null.
            // Mock throws IllegalArgumentException to simulate userRepository.findById(null)
            // which GlobalExceptionHandler maps to 400.
            when(orderService.createOrder(isNull(), anyLong(), anyInt()))
                    .thenThrow(new IllegalArgumentException("User id must not be null"));

            String incompletePayload = "{\"productId\":2,\"quantity\":10}";

            mockMvc.perform(post("/api/v1/orders")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(incompletePayload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message", containsString("null")));
        }

        @Test
        @DisplayName("POST /api/v1/orders with null quantity → NPE during auto-unboxing at call site → 500")
        void createOrder_NullQuantity_Returns500() throws Exception {
            // Jackson deserializes quantity:null as Integer(null). When the controller calls
            // orderService.createOrder(1L, 2L, nullInteger), Java auto-unboxing the null Integer
            // to primitive int throws NullPointerException AT THE CALL SITE — before the mock is even reached.
            // The GlobalExceptionHandler's catch-all (Exception.class) returns 500.
            String nullQuantityPayload = "{\"userId\":1,\"productId\":2,\"quantity\":null}";

            mockMvc.perform(post("/api/v1/orders")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(nullQuantityPayload))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.message", containsString("internal server error")));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCENARIO 6 — GET /api/v1/orders/user/{userId}
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Scenario 6: Get Orders By User ID")
    class GetOrdersByUserIdTests {

        @Test
        @DisplayName("GET /api/v1/orders/user/{userId} → 200 OK with list of orders")
        void getOrdersByUserId_ExistingUser_ReturnsOrderList() throws Exception {
            Order order1 = buildOrder(bulkBuyerOmega, new BigDecimal("2990.00"), OrderStatus.PAID);
            Order order2 = buildOrder(bulkBuyerOmega, new BigDecimal("5980.00"), OrderStatus.PAID);
            when(orderService.getOrdersByUserId(1L)).thenReturn(List.of(order1, order2));

            mockMvc.perform(get("/api/v1/orders/user/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].status").value("PAID"))
                    .andExpect(jsonPath("$[1].totalAmount").value(5980.00));
        }

        @Test
        @DisplayName("GET /api/v1/orders/user/{userId} → 200 OK with empty list for user with no orders")
        void getOrdersByUserId_NoOrders_ReturnsEmptyList() throws Exception {
            when(orderService.getOrdersByUserId(1L)).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/v1/orders/user/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("GET /api/v1/orders/user/{userId} → 404 NOT FOUND when user does not exist")
        void getOrdersByUserId_UserNotFound_Returns404() throws Exception {
            when(orderService.getOrdersByUserId(999L))
                    .thenThrow(new ResourceNotFoundException("User not found with id: 999"));

            mockMvc.perform(get("/api/v1/orders/user/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message", containsString("User not found")));
        }
    }
}
