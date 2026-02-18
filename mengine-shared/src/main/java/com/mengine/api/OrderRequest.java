package com.mengine.api;

import com.mengine.model.OrderType;

import java.math.BigDecimal;

/**
 * JSON request body for order submission.
 */
public record OrderRequest(
        String symbol,
        OrderType type,
        BigDecimal price,
        BigDecimal quantity
) {
    public boolean isValid() {
        return symbol != null && !symbol.isBlank()
                && type != null
                && price != null && price.compareTo(BigDecimal.ZERO) > 0
                && quantity != null && quantity.compareTo(BigDecimal.ZERO) > 0;
    }
}
