package com.mengine.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable record of an executed trade between a buy order and a sell order.
 * Thread-safe - all fields are final.
 */
public final class Trade {

    private final String id;
    private final String symbol;
    private final String buyOrderId;
    private final String sellOrderId;
    private final BigDecimal price;
    private final BigDecimal quantity;
    private final long timestampNs;

    private Trade(Builder builder) {
        this.id = builder.id;
        this.symbol = builder.symbol;
        this.buyOrderId = builder.buyOrderId;
        this.sellOrderId = builder.sellOrderId;
        this.price = builder.price;
        this.quantity = builder.quantity;
        this.timestampNs = builder.timestampNs;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getBuyOrderId() {
        return buyOrderId;
    }

    public String getSellOrderId() {
        return sellOrderId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public long getTimestampNs() {
        return timestampNs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Trade trade = (Trade) o;
        return Objects.equals(id, trade.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public static final class Builder {
        private String id;
        private String symbol;
        private String buyOrderId;
        private String sellOrderId;
        private BigDecimal price;
        private BigDecimal quantity;
        private long timestampNs;

        private Builder() {}

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder symbol(String symbol) {
            this.symbol = symbol;
            return this;
        }

        public Builder buyOrderId(String buyOrderId) {
            this.buyOrderId = buyOrderId;
            return this;
        }

        public Builder sellOrderId(String sellOrderId) {
            this.sellOrderId = sellOrderId;
            return this;
        }

        public Builder price(BigDecimal price) {
            this.price = price;
            return this;
        }

        public Builder quantity(BigDecimal quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder timestampNs(long timestampNs) {
            this.timestampNs = timestampNs;
            return this;
        }

        public Trade build() {
            Objects.requireNonNull(id, "id is required");
            Objects.requireNonNull(symbol, "symbol is required");
            Objects.requireNonNull(buyOrderId, "buyOrderId is required");
            Objects.requireNonNull(sellOrderId, "sellOrderId is required");
            Objects.requireNonNull(price, "price is required");
            Objects.requireNonNull(quantity, "quantity is required");
            return new Trade(this);
        }
    }
}
