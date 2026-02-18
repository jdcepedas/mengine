package com.mengine.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable order entity representing a buy request or sell offer.
 */
public final class Order {

    private final String id;
    private final String symbol;
    private final OrderType type;
    private final BigDecimal price;
    private final BigDecimal quantity;
    private final BigDecimal remainingQuantity;
    private final OrderStatus status;
    private final long timestampNs;
    /** Epoch ms when order was received at the API (for e2e latency). 0 if not set. */
    private final long apiReceivedAtEpochMs;
    /** Epoch nanoseconds when order was received at the API (for e2e latency in ns). 0 if not set. */
    private final long apiReceivedAtEpochNs;

    @JsonCreator
    public Order(
            @JsonProperty("id") String id,
            @JsonProperty("symbol") String symbol,
            @JsonProperty("type") OrderType type,
            @JsonProperty("price") BigDecimal price,
            @JsonProperty("quantity") BigDecimal quantity,
            @JsonProperty("remainingQuantity") BigDecimal remainingQuantity,
            @JsonProperty("status") OrderStatus status,
            @JsonProperty("timestampNs") long timestampNs,
            @JsonProperty("apiReceivedAtEpochMs") Long apiReceivedAtEpochMs,
            @JsonProperty("apiReceivedAtEpochNs") Long apiReceivedAtEpochNs) {
        this.id = id;
        this.symbol = symbol;
        this.type = type;
        this.price = price;
        this.quantity = quantity;
        this.remainingQuantity = remainingQuantity;
        this.status = status;
        this.timestampNs = timestampNs;
        long ns = apiReceivedAtEpochNs != null && apiReceivedAtEpochNs > 0 ? apiReceivedAtEpochNs : 0L;
        this.apiReceivedAtEpochNs = ns;
        this.apiReceivedAtEpochMs = ns > 0 ? ns / 1_000_000 : (apiReceivedAtEpochMs != null && apiReceivedAtEpochMs > 0 ? apiReceivedAtEpochMs : 0L);
    }

    private Order(Builder builder) {
        this.id = builder.id;
        this.symbol = builder.symbol;
        this.type = builder.type;
        this.price = builder.price;
        this.quantity = builder.quantity;
        this.remainingQuantity = builder.remainingQuantity;
        this.status = builder.status;
        this.timestampNs = builder.timestampNs;
        this.apiReceivedAtEpochMs = builder.apiReceivedAtEpochMs;
        this.apiReceivedAtEpochNs = builder.apiReceivedAtEpochNs;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Order create(String id, String symbol, OrderType type, BigDecimal price, BigDecimal quantity) {
        return builder()
                .id(id)
                .symbol(symbol)
                .type(type)
                .price(price)
                .quantity(quantity)
                .remainingQuantity(quantity)
                .status(OrderStatus.PENDING)
                .timestampNs(System.nanoTime())
                .apiReceivedAtEpochMs(0L)
                .apiReceivedAtEpochNs(0L)
                .build();
    }

    /** Create an order with API receive timestamp (epoch nanoseconds) for end-to-end latency measurement. */
    public static Order createWithApiReceivedAt(String id, String symbol, OrderType type, BigDecimal price, BigDecimal quantity, long apiReceivedAtEpochNs) {
        long ns = apiReceivedAtEpochNs > 0 ? apiReceivedAtEpochNs : 0L;
        return builder()
                .id(id)
                .symbol(symbol)
                .type(type)
                .price(price)
                .quantity(quantity)
                .remainingQuantity(quantity)
                .status(OrderStatus.PENDING)
                .timestampNs(System.nanoTime())
                .apiReceivedAtEpochMs(ns > 0 ? ns / 1_000_000 : 0L)
                .apiReceivedAtEpochNs(ns)
                .build();
    }

    public Order withRemainingQuantity(BigDecimal newRemaining) {
        return builder()
                .id(this.id)
                .symbol(this.symbol)
                .type(this.type)
                .price(this.price)
                .quantity(this.quantity)
                .remainingQuantity(newRemaining)
                .status(newRemaining.compareTo(BigDecimal.ZERO) == 0 ? OrderStatus.MATCHED : OrderStatus.PARTIAL)
                .timestampNs(this.timestampNs)
                .apiReceivedAtEpochMs(this.apiReceivedAtEpochMs)
                .apiReceivedAtEpochNs(this.apiReceivedAtEpochNs)
                .build();
    }

    public Order withStatus(OrderStatus newStatus) {
        return builder()
                .id(this.id)
                .symbol(this.symbol)
                .type(this.type)
                .price(this.price)
                .quantity(this.quantity)
                .remainingQuantity(this.remainingQuantity)
                .status(newStatus)
                .timestampNs(this.timestampNs)
                .apiReceivedAtEpochMs(this.apiReceivedAtEpochMs)
                .apiReceivedAtEpochNs(this.apiReceivedAtEpochNs)
                .build();
    }

    public String getId() { return id; }
    public String getSymbol() { return symbol; }
    public OrderType getType() { return type; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getRemainingQuantity() { return remainingQuantity; }
    public OrderStatus getStatus() { return status; }
    public long getTimestampNs() { return timestampNs; }
    /** Epoch ms when order was received at the API; 0 if not set. */
    public long getApiReceivedAtEpochMs() { return apiReceivedAtEpochMs; }
    /** Epoch nanoseconds when order was received at the API; 0 if not set. */
    public long getApiReceivedAtEpochNs() { return apiReceivedAtEpochNs; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Order order = (Order) o;
        return Objects.equals(id, order.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public static final class Builder {
        private String id;
        private String symbol;
        private OrderType type;
        private BigDecimal price;
        private BigDecimal quantity;
        private BigDecimal remainingQuantity;
        private OrderStatus status;
        private long timestampNs;
        private long apiReceivedAtEpochMs;
        private long apiReceivedAtEpochNs;

        private Builder() {}

        public Builder id(String id) { this.id = id; return this; }
        public Builder symbol(String symbol) { this.symbol = symbol; return this; }
        public Builder type(OrderType type) { this.type = type; return this; }
        public Builder price(BigDecimal price) { this.price = price; return this; }
        public Builder quantity(BigDecimal quantity) { this.quantity = quantity; return this; }
        public Builder remainingQuantity(BigDecimal remainingQuantity) { this.remainingQuantity = remainingQuantity; return this; }
        public Builder status(OrderStatus status) { this.status = status; return this; }
        public Builder timestampNs(long timestampNs) { this.timestampNs = timestampNs; return this; }
        public Builder apiReceivedAtEpochMs(long apiReceivedAtEpochMs) { this.apiReceivedAtEpochMs = apiReceivedAtEpochMs; return this; }
        public Builder apiReceivedAtEpochNs(long apiReceivedAtEpochNs) { this.apiReceivedAtEpochNs = apiReceivedAtEpochNs; return this; }

        public Order build() {
            Objects.requireNonNull(id, "id is required");
            Objects.requireNonNull(symbol, "symbol is required");
            Objects.requireNonNull(type, "type is required");
            Objects.requireNonNull(price, "price is required");
            Objects.requireNonNull(quantity, "quantity is required");
            Objects.requireNonNull(remainingQuantity, "remainingQuantity is required");
            Objects.requireNonNull(status, "status is required");
            return new Order(this);
        }
    }
}
