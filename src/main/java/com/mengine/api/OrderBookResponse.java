package com.mengine.api;

import java.math.BigDecimal;
import java.util.List;

/**
 * JSON response for order book state.
 */
public record OrderBookResponse(
        String symbol,
        List<PriceLevelView> bids,
        List<PriceLevelView> asks
) {
    public record PriceLevelView(
            BigDecimal price,
            BigDecimal totalQuantity,
            int orderCount
    ) {}
}
