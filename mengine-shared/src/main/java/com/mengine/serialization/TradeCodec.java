package com.mengine.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mengine.model.Trade;

import java.nio.charset.StandardCharsets;

/**
 * Serialization for Trade over wire (JSON bytes).
 */
public final class TradeCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public static byte[] toBytes(Trade trade) {
        try {
            return MAPPER.writeValueAsString(trade).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize trade", e);
        }
    }

    public static Trade fromBytes(byte[] bytes) {
        try {
            return MAPPER.readValue(new String(bytes, StandardCharsets.UTF_8), Trade.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize trade", e);
        }
    }
}
