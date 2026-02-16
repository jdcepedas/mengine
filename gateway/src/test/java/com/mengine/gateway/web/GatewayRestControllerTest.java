package com.mengine.gateway.web;

import com.mengine.api.OrderBookResponse;
import com.mengine.gateway.aeron.OrderPublisherRouter;
import com.mengine.gateway.client.MeCoreClient;
import com.mengine.gateway.client.TradeQuery;
import com.mengine.model.Order;
import com.mengine.model.OrderType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayRestControllerTest {

    @Autowired
    WebTestClient webTestClient;

    @MockBean
    OrderPublisherRouter orderPublisherRouter;

    @MockBean
    MeCoreClient meCoreClient;

    @MockBean
    TradeQuery tradeQuery;

    @Test
    void postOrder_valid_returns202() {
        when(orderPublisherRouter.publish(any())).thenReturn(true);
        String body = """
                {"symbol":"AAPL","type":"BUY","price":"150.00","quantity":"10"}
                """;
        webTestClient.post()
                .uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.status").isEqualTo("ACCEPTED")
                .jsonPath("$.orderId").exists();
    }

    @Test
    void postOrder_invalid_returns400() {
        String body = """
                {"symbol":"","type":"BUY","price":"150.00","quantity":"10"}
                """;
        webTestClient.post()
                .uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo("REJECTED");
    }

    @Test
    void getOrder_found_returns200() {
        Order order = Order.create("O1a2b3c4d5e6f", "AAPL", OrderType.BUY, new BigDecimal("150.00"), new BigDecimal("10"));
        when(meCoreClient.getOrder("O1a2b3c4d5e6f")).thenReturn(order);

        webTestClient.get()
                .uri("/orders/O1a2b3c4d5e6f")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.orderId").isEqualTo("O1a2b3c4d5e6f")
                .jsonPath("$.symbol").isEqualTo("AAPL");
    }

    @Test
    void getOrder_notFound_returns404() {
        when(meCoreClient.getOrder(anyString())).thenReturn(null);

        webTestClient.get()
                .uri("/orders/nonexistent")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.error").isEqualTo("Order not found");
    }

    @Test
    void getOrderBook_returns200() {
        OrderBookResponse ob = new OrderBookResponse("AAPL", List.of(), List.of());
        when(meCoreClient.getOrderBook("AAPL")).thenReturn(ob);

        webTestClient.get()
                .uri("/orderbook/AAPL")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.symbol").isEqualTo("AAPL");
    }

    @Test
    void getTrades_returns200() {
        when(tradeQuery.findRecentBySymbol("AAPL", 100)).thenReturn(List.of());

        webTestClient.get()
                .uri("/trades/AAPL")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray();
    }

    @Test
    void getTrades_withLimit_respectsLimit() {
        when(tradeQuery.findRecentBySymbol("AAPL", 50)).thenReturn(List.of());

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/trades/AAPL").queryParam("limit", 50).build())
                .exchange()
                .expectStatus().isOk();
    }
}
