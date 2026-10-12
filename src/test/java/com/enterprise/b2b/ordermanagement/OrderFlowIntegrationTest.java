package com.enterprise.b2b.ordermanagement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end flow against the real security chain, JPA and an in-memory H2 database. */
@SpringBootTest
@AutoConfigureMockMvc
class OrderFlowIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private ResultActions send(String url, String token, String body) throws Exception {
        var req = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) req.header("Authorization", "Bearer " + token);
        return mvc.perform(req);
    }

    private String register(String role) throws Exception {
        String name = "u" + UUID.randomUUID().toString().substring(0, 8);
        send("/api/v1/auth/register", null, String.format(
                "{\"username\":\"%s\",\"password\":\"pw-%s\",\"email\":\"%s@example.com\",\"role\":\"%s\"}",
                name, name, name, role)).andExpect(status().isCreated());
        return name;
    }

    private String login(String name) throws Exception {
        String res = send("/api/v1/auth/login", null,
                String.format("{\"username\":\"%s\",\"password\":\"pw-%s\"}", name, name))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("accessToken").asText();
    }

    private long idOf(String body) throws Exception {
        return json.readTree(body).get("id").asLong();
    }

    @Test
    void protectedEndpointsRequireToken() throws Exception {
        mvc.perform(get("/api/v1/products")).andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateUsernameAndBadLoginAreRejected() throws Exception {
        String name = register("ROLE_CLIENT");
        send("/api/v1/auth/register", null, String.format(
                "{\"username\":\"%s\",\"password\":\"x\",\"email\":\"other-%s@example.com\"}", name, name))
                .andExpect(status().isBadRequest());
        send("/api/v1/auth/login", null, String.format("{\"username\":\"%s\",\"password\":\"wrong\"}", name))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void fullPurchaseFlowDeductsWalletAndStock() throws Exception {
        String adminToken = login(register("ROLE_ADMIN"));
        String clientName = register("ROLE_CLIENT");
        String clientToken = login(clientName);
        String sku = "SKU-" + UUID.randomUUID().toString().substring(0, 8);

        // clients cannot create products
        send("/api/v1/products", clientToken,
                "{\"name\":\"Plan\",\"sku\":\"" + sku + "\",\"price\":299.00,\"stockQuantity\":5}")
                .andExpect(status().isForbidden());

        String product = send("/api/v1/products", adminToken,
                "{\"name\":\"Plan\",\"sku\":\"" + sku + "\",\"price\":299.00,\"stockQuantity\":5}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long productId = idOf(product);

        long clientId = lookupId(clientName);

        // wallet empty -> insufficient balance
        send("/api/v1/orders", clientToken, orderJson(clientId, productId, 1)).andExpect(status().isBadRequest());

        // clients cannot top up; admin can
        send("/api/v1/users/" + clientId + "/wallet/topup", clientToken, "{\"amount\":1000}")
                .andExpect(status().isForbidden());
        send("/api/v1/users/" + clientId + "/wallet/topup", adminToken, "{\"amount\":1000}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.walletBalance").value(1000.0));

        // buy 3 x 299 = 897
        send("/api/v1/orders", clientToken, orderJson(clientId, productId, 3))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.totalAmount").value(897.0))
                .andExpect(jsonPath("$.userId").value(clientId))
                .andExpect(jsonPath("$.user").doesNotExist());

        mvc.perform(get("/api/v1/users/" + clientId + "/wallet").header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.walletBalance").value(103.0));
        mvc.perform(get("/api/v1/products/sku/" + sku).header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stockQuantity").value(2));

        // out of stock and insufficient balance leave state untouched
        send("/api/v1/orders", clientToken, orderJson(clientId, productId, 3)).andExpect(status().isBadRequest());
        send("/api/v1/orders", clientToken, orderJson(clientId, productId, 0)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/users/" + clientId + "/wallet").header("Authorization", "Bearer " + clientToken))
                .andExpect(jsonPath("$.walletBalance").value(103.0));

        mvc.perform(get("/api/v1/orders/user/" + clientId).header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void clientCannotActOnAnotherUsersOrders() throws Exception {
        String adminToken = login(register("ROLE_ADMIN"));
        String victim = register("ROLE_CLIENT");
        long victimId = lookupId(victim);
        String attackerToken = login(register("ROLE_CLIENT"));

        mvc.perform(get("/api/v1/orders/user/" + victimId).header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isForbidden());
        send("/api/v1/orders", attackerToken, orderJson(victimId, 1, 1)).andExpect(status().isForbidden());
    }

    private String orderJson(long userId, long productId, int qty) {
        return String.format("{\"userId\":%d,\"productId\":%d,\"quantity\":%d}", userId, productId, qty);
    }

    @Autowired com.enterprise.b2b.ordermanagement.repository.UserRepository users;

    private long lookupId(String username) {
        return users.findByUsername(username).orElseThrow().getId();
    }
}
