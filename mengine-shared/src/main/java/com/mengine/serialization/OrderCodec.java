package com.mengine.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mengine.model.Order;

import java.nio.charset.StandardCharsets;

/**
 * Serialization for Order over Aeron (JSON bytes).
 */
public final class OrderCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public static byte[] toBytes(Order order) {
        try {
            return MAPPER.writeValueAsString(order).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize order", e);
        }
    }

    public static Order fromBytes(byte[] bytes) {
        try {
            return MAPPER.readValue(new String(bytes, StandardCharsets.UTF_8), Order.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize order", e);
        }
    }
}
