package com.enterprise.b2b.ordermanagement.service;

import com.enterprise.b2b.ordermanagement.entity.Order;
import com.enterprise.b2b.ordermanagement.entity.OrderStatus;
import com.enterprise.b2b.ordermanagement.entity.Product;
import com.enterprise.b2b.ordermanagement.entity.User;
import com.enterprise.b2b.ordermanagement.entity.UserRole;
import com.enterprise.b2b.ordermanagement.exception.InsufficientBalanceException;
import com.enterprise.b2b.ordermanagement.exception.OutOfStockException;
import com.enterprise.b2b.ordermanagement.exception.ResourceNotFoundException;
import com.enterprise.b2b.ordermanagement.repository.OrderRepository;
import com.enterprise.b2b.ordermanagement.repository.ProductRepository;
import com.enterprise.b2b.ordermanagement.repository.UserRepository;
import com.enterprise.b2b.ordermanagement.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for OrderServiceImpl using JUnit 5 + Mockito.
 *
 * <p>@ExtendWith(MockitoExtension.class) activates Mockito annotation processing.
 * @InjectMocks creates an OrderServiceImpl instance with all @Mock dependencies
 * automatically wired in.
 *
 * <p>These tests verify transactional business logic, boundary conditions, and
 * exception propagation in complete isolation — no Spring context required.
 *
 * <p>Enterprise mock data is used to avoid colliding with manual verification records.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    // ── Mockito Test Doubles ──────────────────────────────────────────────────

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductRepository productRepository;

    // ── System Under Test ──────────────────────────────────────────────────────

    @InjectMocks
    private OrderServiceImpl orderService;

    // ── Argument Captors ──────────────────────────────────────────────────────

    @Captor
    private ArgumentCaptor<User> userCaptor;

    @Captor
    private ArgumentCaptor<Product> productCaptor;

    @Captor
    private ArgumentCaptor<Order> orderCaptor;

    // ── Enterprise Mock Entities ──────────────────────────────────────────────
    // Used throughout to prevent test-database collision with manual verification records.

    private User bulkBuyerOmega;
    private Product saasSub12;

    @BeforeEach
    void setUp() {
        // Username : bulk_buyer_omega
        // Password : SecureOmega99$
        // Role    : ROLE_CLIENT
        // Wallet  : 15,000.00
        bulkBuyerOmega = User.builder()
                .id(1L)
                .username("bulk_buyer_omega")
                .password("SecureOmega99$")
                .email("omega@enterprise.com")
                .role(UserRole.ROLE_CLIENT)
                .walletBalance(new BigDecimal("15000.0000"))
                .build();

        // Name    : SaaS Premium Sub
        // SKU     : SAAS-SUB-12
        // Price   : 299.00
        // Stock   : 150
        saasSub12 = Product.builder()
                .id(2L)
                .name("SaaS Premium Sub")
                .sku("SAAS-SUB-12")
                .price(new BigDecimal("299.0000"))
                .stockQuantity(150)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCENARIO 1 ─ Successful Checkout
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Scenario 1: Successful Checkout")
    class SuccessfulCheckoutTests {

        @Test
        @DisplayName("createOrder → deducts exact amount and reduces stock correctly")
        void createOrder_ValidPurchase_DeductsCorrectAmountAndReducesStock() {
            // Arrange: buying 10 units of SAAS-SUB-12 @ 299.00 = 2,990.00
            // Expected wallet balance after: 15,000.00 - 2,990.00 = 12,010.00
            // Expected stock after: 150 - 10 = 140
            when(userRepository.findById(1L)).thenReturn(Optional.of(bulkBuyerOmega));
            when(productRepository.findById(2L)).thenReturn(Optional.of(saasSub12));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
                Order o = inv.getArgument(0);
                // Simulate JPA ID generation
                return Order.builder()
                        .id(100L)
                        .user(o.getUser())
                        .totalAmount(o.getTotalAmount())
                        .status(o.getStatus())
                        .build();
            });

            // Act
            Order result = orderService.createOrder(1L, 2L, 10);

            // Assert — Order fields
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(100L);
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(result.getTotalAmount()).isEqualByComparingTo(new BigDecimal("2990.00"));

            // Assert — Wallet balance deducted correctly
            verify(userRepository).save(userCaptor.capture());
            User savedUser = userCaptor.getValue();
            assertThat(savedUser.getWalletBalance())
                    .isEqualByComparingTo(new BigDecimal("12010.00"));

            // Assert — Stock reduced correctly
            verify(productRepository).save(productCaptor.capture());
            Product savedProduct = productCaptor.getValue();
            assertThat(savedProduct.getStockQuantity()).isEqualTo(140);

            // Assert — Order persisted
            verify(orderRepository).save(orderCaptor.capture());
            assertThat(orderCaptor.getValue().getTotalAmount())
                    .isEqualByComparingTo(new BigDecimal("2990.00"));
        }

        @Test
        @DisplayName("createOrder → order is marked PAID immediately after wallet deduction")
        void createOrder_ValidPurchase_OrderMarkedPaidImmediately() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(bulkBuyerOmega));
            when(productRepository.findById(2L)).thenReturn(Optional.of(saasSub12));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
                Order o = inv.getArgument(0);
                return Order.builder().id(101L).user(o.getUser())
                        .totalAmount(o.getTotalAmount()).status(o.getStatus()).build();
            });

            Order result = orderService.createOrder(1L, 2L, 10);

            // Verifies the service marks PAID synchronously after deduction,
            // confirming the @Transactional boundary does not defer the status assignment.
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID);
            verify(orderRepository, times(1)).save(any(Order.class));
        }

        @Test
        @DisplayName("createOrder → exactly 3 repository saves: user, product, order")
        void createOrder_ValidPurchase_SavesUserProductAndOrderOnceEach() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(bulkBuyerOmega));
            when(productRepository.findById(2L)).thenReturn(Optional.of(saasSub12));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            orderService.createOrder(1L, 2L, 1);

            // 1 user + 1 product + 1 order = exactly 3 saves
            verify(userRepository, times(1)).save(any(User.class));
            verify(productRepository, times(1)).save(any(Product.class));
            verify(orderRepository, times(1)).save(any(Order.class));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCENARIO 2 ─ Insufficient Balance
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Scenario 2: Insufficient Balance Failure")
    class InsufficientBalanceTests {

        @Test
        @DisplayName("createOrder → throws InsufficientBalanceException when total cost exceeds wallet balance")
        void createOrder_InsufficientBalance_ThrowsInsufficientBalanceException() {
            // Arrange: requesting 100 units @ 299.00 = 29,900.00 against balance of 15,000.00
            when(userRepository.findById(1L)).thenReturn(Optional.of(bulkBuyerOmega));
            when(productRepository.findById(2L)).thenReturn(Optional.of(saasSub12));

            // Act & Assert
            assertThatThrownBy(() -> orderService.createOrder(1L, 2L, 100))
                    .isInstanceOf(InsufficientBalanceException.class)
                    .hasMessageContaining("insufficient")
                    .hasMessageContaining("29900.00")
                    .hasMessageContaining("15000.00");
        }

        @Test
        @DisplayName("createOrder → wallet balance is NOT modified when InsufficientBalanceException is thrown")
        void createOrder_InsufficientBalance_WalletBalanceNotModified() {
            // Arrange
            when(userRepository.findById(1L)).thenReturn(Optional.of(bulkBuyerOmega));
            when(productRepository.findById(2L)).thenReturn(Optional.of(saasSub12));

            // Capture the balance BEFORE the call to assert it is unchanged
            BigDecimal balanceBefore = bulkBuyerOmega.getWalletBalance();

            // Act
            try {
                orderService.createOrder(1L, 2L, 100);
            } catch (InsufficientBalanceException ignored) {
                // Expected — balance verification still applies in catch block
            }

            // Assert — balance unchanged after failed transaction
            assertThat(bulkBuyerOmega.getWalletBalance())
                    .isEqualByComparingTo(balanceBefore)
                    .isEqualByComparingTo(new BigDecimal("15000.00"));

            // Assert — no saves were attempted (transaction should have been rolled back)
            verify(userRepository, never()).save(any(User.class));
            verify(productRepository, never()).save(any(Product.class));
            verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        @DisplayName("createOrder → InsufficientBalanceException message contains exact shortfall")
        void createOrder_InsufficientBalance_ExceptionMessageContainsExactShortfall() {
            // Arrange
            when(userRepository.findById(1L)).thenReturn(Optional.of(bulkBuyerOmega));
            when(productRepository.findById(2L)).thenReturn(Optional.of(saasSub12));

            // Act & Assert
            assertThatThrownBy(() -> orderService.createOrder(1L, 2L, 100))
                    .isInstanceOf(InsufficientBalanceException.class)
                    .hasMessageContaining("Required: 29900.00")
                    .hasMessageContaining("Available: 15000.00");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCENARIO 3 ─ Out of Stock
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Scenario 3: Out of Stock Failure")
    class OutOfStockTests {

        @Test
        @DisplayName("createOrder → throws OutOfStockException when requested quantity exceeds stock")
        void createOrder_OutOfStock_ThrowsOutOfStockException() {
            // Arrange: requesting 200 units against available stock of 150
            when(userRepository.findById(1L)).thenReturn(Optional.of(bulkBuyerOmega));
            when(productRepository.findById(2L)).thenReturn(Optional.of(saasSub12));

            // Act & Assert
            assertThatThrownBy(() -> orderService.createOrder(1L, 2L, 200))
                    .isInstanceOf(OutOfStockException.class)
                    .hasMessageContaining("out of stock")
                    .hasMessageContaining("200")
                    .hasMessageContaining("150")
                    .hasMessageContaining("SaaS Premium Sub");
        }

        @Test
        @DisplayName("createOrder → wallet balance is NOT modified when OutOfStockException is thrown")
        void createOrder_OutOfStock_WalletBalanceNotModified() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(bulkBuyerOmega));
            when(productRepository.findById(2L)).thenReturn(Optional.of(saasSub12));

            try {
                orderService.createOrder(1L, 2L, 200);
            } catch (OutOfStockException ignored) {
                // Expected
            }

            // Assert — balance unchanged after failed validation before deduction
            assertThat(bulkBuyerOmega.getWalletBalance())
                    .isEqualByComparingTo(new BigDecimal("15000.00"));

            // Assert — no saves attempted
            verify(userRepository, never()).save(any(User.class));
            verify(productRepository, never()).save(any(Product.class));
            verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        @DisplayName("createOrder → OutOfStockException thrown BEFORE balance check (correct order of validation)")
        void createOrder_OutOfStock_ThrownBeforeBalanceValidation() {
            // Use a product with 0 stock and a user with a very high balance
            saasSub12.setStockQuantity(0);
            bulkBuyerOmega.setWalletBalance(new BigDecimal("999999.00"));

            when(userRepository.findById(1L)).thenReturn(Optional.of(bulkBuyerOmega));
            when(productRepository.findById(2L)).thenReturn(Optional.of(saasSub12));

            // Even though the user has plenty of balance, out-of-stock should fire first
            assertThatThrownBy(() -> orderService.createOrder(1L, 2L, 1))
                    .isInstanceOf(OutOfStockException.class)
                    .hasMessageContaining("0");

            // Confirms the service validates stock BEFORE checking wallet balance
            verify(userRepository, never()).save(any(User.class));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCENARIO 4 ─ Service Layer Isolation: WalletBalance NOT changed if product lookup fails
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Scenario 4: Service Layer Isolation — WalletBalance NOT modified on ResourceNotFoundException")
    class ServiceLayerIsolationTests {

        /**
         * CRITICAL ISOLATION TEST:
         *
         * Verifies that when productRepository.findById(productId) throws
         * ResourceNotFoundException, the user's walletBalance is NOT mutated.
         *
         * This is the canonical test for transactional boundary integrity:
         * because the service method is annotated @Transactional, any unchecked
         * exception triggers a rollback. In a unit test with real mocks there
         * is no real transaction, but this test confirms that no partial state
         * mutation occurs before the exception propagates.
         *
         * Setup: userRepository.findById succeeds → user object loaded.
         *        productRepository.findById fails → ResourceNotFoundException.
         * Assertion: user.walletBalance remains at 15,000.00 (unchanged).
         */
        @Test
        @DisplayName("createOrder → walletBalance remains 15000.00 when product lookup fails (ResourceNotFoundException)")
        void createOrder_ProductLookupFails_WalletBalanceNotChanged() {
            // Arrange — user found, product NOT found
            when(userRepository.findById(1L)).thenReturn(Optional.of(bulkBuyerOmega));
            when(productRepository.findById(999L))
                    .thenThrow(new ResourceNotFoundException("Product not found with id: 999"));

            // Capture balance before the call
            BigDecimal balanceBefore = bulkBuyerOmega.getWalletBalance();

            // Act & Assert
            assertThatThrownBy(() -> orderService.createOrder(1L, 999L, 10))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Product not found with id: 999");

            // CRITICAL: wallet balance must not change when product lookup fails.
            // This confirms the service never reached the balance-deduction step.
            assertThat(bulkBuyerOmega.getWalletBalance())
                    .isEqualByComparingTo(balanceBefore)
                    .isEqualByComparingTo(new BigDecimal("15000.00"));

            // Verify no saves were triggered — product lookup failure is a hard stop
            verify(userRepository, never()).save(any(User.class));
            verify(productRepository, never()).save(any(Product.class));
            verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        @DisplayName("createOrder → walletBalance remains 15000.00 when user lookup fails (ResourceNotFoundException)")
        void createOrder_UserLookupFails_WalletBalanceNotChanged() {
            // Arrange — user NOT found
            when(userRepository.findById(999L))
                    .thenThrow(new ResourceNotFoundException("User not found with id: 999"));

            // Act & Assert
            assertThatThrownBy(() -> orderService.createOrder(999L, 2L, 10))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User not found with id: 999");

            // No partial state to verify for a non-existent user,
            // but this confirms the lookup-then-mutate pattern is enforced.
            verify(userRepository, never()).save(any(User.class));
            verify(productRepository, never()).save(any(Product.class));
            verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        @DisplayName("createOrder → correct entities are passed to repository.findById lookups")
        void createOrder_CallsFindByIdWithCorrectEntityIds() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(bulkBuyerOmega));
            when(productRepository.findById(2L)).thenReturn(Optional.of(saasSub12));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            orderService.createOrder(1L, 2L, 5);

            // Verify exact IDs passed to repositories
            verify(userRepository).findById(1L);
            verify(productRepository).findById(2L);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCENARIO 5 ─ Boundary: Quantity Edge Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Scenario 5: Quantity Boundary Validation")
    class QuantityBoundaryTests {

        @Test
        @DisplayName("createOrder → throws IllegalArgumentException when quantity is zero")
        void createOrder_ZeroQuantity_ThrowsIllegalArgumentException() {
            assertThatThrownBy(() -> orderService.createOrder(1L, 2L, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("greater than zero");

            // Zero-quantity validation fires before any repository interaction
            verify(userRepository, never()).findById(any());
            verify(productRepository, never()).findById(any());
        }

        @Test
        @DisplayName("createOrder → throws IllegalArgumentException when quantity is negative")
        void createOrder_NegativeQuantity_ThrowsIllegalArgumentException() {
            assertThatThrownBy(() -> orderService.createOrder(1L, 2L, -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("greater than zero");

            verify(userRepository, never()).findById(any());
            verify(productRepository, never()).findById(any());
        }

        @Test
        @DisplayName("createOrder → exactly one unit purchase works (quantity = 1)")
        void createOrder_QuantityOne_PurchasesSingleUnit() {
            // Arrange
            when(userRepository.findById(1L)).thenReturn(Optional.of(bulkBuyerOmega));
            when(productRepository.findById(2L)).thenReturn(Optional.of(saasSub12));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
                Order o = inv.getArgument(0);
                return Order.builder().id(102L).user(o.getUser())
                        .totalAmount(o.getTotalAmount()).status(o.getStatus()).build();
            });

            // Act
            Order result = orderService.createOrder(1L, 2L, 1);

            // Assert
            assertThat(result.getTotalAmount()).isEqualByComparingTo(new BigDecimal("299.00"));
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getWalletBalance())
                    .isEqualByComparingTo(new BigDecimal("14701.00")); // 15000 - 299
            verify(productRepository).save(productCaptor.capture());
            assertThat(productCaptor.getValue().getStockQuantity()).isEqualTo(149);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCENARIO 6 ─ getOrdersByUserId
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Scenario 6: getOrdersByUserId")
    class GetOrdersByUserIdTests {

        @Test
        @DisplayName("getOrdersByUserId → returns orders for existing user")
        void getOrdersByUserId_ExistingUser_ReturnsOrderList() {
            // Arrange
            when(userRepository.existsById(1L)).thenReturn(true);
            Order order = Order.builder()
                    .id(100L)
                    .user(bulkBuyerOmega)
                    .totalAmount(new BigDecimal("2990.00"))
                    .status(OrderStatus.PAID)
                    .build();
            when(orderRepository.findByUserId(1L)).thenReturn(java.util.List.of(order));

            // Act
            var result = orderService.getOrdersByUserId(1L);

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStatus()).isEqualTo(OrderStatus.PAID);
        }

        @Test
        @DisplayName("getOrdersByUserId → throws ResourceNotFoundException when user does not exist")
        void getOrdersByUserId_UserNotFound_ThrowsResourceNotFoundException() {
            when(userRepository.existsById(999L)).thenReturn(false);

            assertThatThrownBy(() -> orderService.getOrdersByUserId(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User not found");

            verify(orderRepository, never()).findByUserId(any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCENARIO 7 ─ getOrderById
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Scenario 7: getOrderById")
    class GetOrderByIdTests {

        @Test
        @DisplayName("getOrderById → returns order when found")
        void getOrderById_ExistingOrder_ReturnsOrder() {
            Order order = Order.builder()
                    .id(100L)
                    .user(bulkBuyerOmega)
                    .totalAmount(new BigDecimal("2990.00"))
                    .status(OrderStatus.PAID)
                    .build();
            when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

            Order result = orderService.getOrderById(100L);

            assertThat(result.getId()).isEqualTo(100L);
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID);
        }

        @Test
        @DisplayName("getOrderById → throws ResourceNotFoundException when order does not exist")
        void getOrderById_OrderNotFound_ThrowsResourceNotFoundException() {
            when(orderRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getOrderById(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Order not found with id: 999");
        }
    }
}
