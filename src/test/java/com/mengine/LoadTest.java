package com.mengine;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Load test scenarios compatible with JMeter testing approach.
 * Run the server with ./gradlew run before executing these tests.
 */
@Disabled("Requires server to be running - use for manual load testing")
class LoadTest {

    private static final String BASE_URL = "http://localhost:8080";

    @Test
    void singleOrderSubmission() throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        String body = """
                {"symbol":"AAPL","type":"BUY","price":100,"quantity":10}
                """;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/orders"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(202, response.statusCode());
        assertTrue(response.body().contains("orderId"));
        assertTrue(response.body().contains("ACCEPTED"));
    }

    @Test
    void baselineLoad_1300OrdersPerMinute() throws Exception {
        int ordersPerBatch = 22;
        int batches = 60;
        int totalOrders = ordersPerBatch * batches;

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        AtomicInteger successCount = new AtomicInteger(0);
        long start = System.currentTimeMillis();

        for (int batch = 0; batch < batches; batch++) {
            var futures = new CompletableFuture[ordersPerBatch];
            for (int i = 0; i < ordersPerBatch; i++) {
                String body = String.format(
                        "{\"symbol\":\"AAPL\",\"type\":\"%s\",\"price\":%d,\"quantity\":%d}",
                        (batch + i) % 2 == 0 ? "BUY" : "SELL",
                        100 + (i % 10),
                        1 + (i % 5)
                );
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/orders"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                futures[i] = client.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                        .thenAccept(r -> {
                            if (r.statusCode() == 202 || r.statusCode() == 503) {
                                successCount.incrementAndGet();
                            }
                        });
            }
            CompletableFuture.allOf(futures).join();
            Thread.sleep(1000 - 50);
        }

        long elapsed = System.currentTimeMillis() - start;
        System.out.printf("Submitted %d orders in %d ms (~%d orders/min)%n",
                successCount.get(), elapsed, successCount.get() * 60000L / Math.max(1, elapsed));
    }

    @Test
    void orderBookRetrieval() throws Exception {
        HttpClient client = HttpClient.newBuilder().build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/orderbook/AAPL"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("bids") || response.body().contains("asks"));
    }
}
