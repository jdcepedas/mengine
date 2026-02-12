package com.mengine.core.journal;

import com.mengine.model.Order;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Append-only order journal for replay on startup.
 * Format: one JSON order per line (UTF-8).
 */
public class OrderJournal {

    private final Path journalPath;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();

    public OrderJournal(Path journalDir) throws IOException {
        Files.createDirectories(journalDir);
        this.journalPath = journalDir.resolve("orders.log");
    }

    public synchronized void append(Order order) throws IOException {
        String line = mapper.writeValueAsString(order) + "\n";
        Files.writeString(journalPath, line, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    }

    /**
     * Replay all orders from the journal (for recovery).
     */
    public List<Order> replay() throws IOException {
        if (!Files.exists(journalPath)) {
            return List.of();
        }
        List<Order> orders = new ArrayList<>();
        for (String line : Files.readAllLines(journalPath, StandardCharsets.UTF_8)) {
            line = line.trim();
            if (line.isEmpty()) continue;
            try {
                orders.add(mapper.readValue(line, Order.class));
            } catch (Exception e) {
                // skip malformed lines
            }
        }
        return orders;
    }
}
