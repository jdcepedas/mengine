package com.mengine.gateway.aeron;

import com.mengine.model.Order;

import java.util.List;

/**
 * Routes orders to the correct partition by symbol.
 * When GW_SYMBOLS is set (e.g. "AAPL,MSFT"), symbol at index i goes to partition (i % P).
 * Otherwise falls back to hash(symbol) % P.
 */
public class OrderPublisherRouter implements AutoCloseable {

    private final List<OrderPublisher> publishers;
    private final List<String> symbols;

    public OrderPublisherRouter(List<OrderPublisher> publishers) {
        this(publishers, List.of());
    }

    public OrderPublisherRouter(List<OrderPublisher> publishers, List<String> symbols) {
        this.publishers = publishers;
        this.symbols = symbols != null ? symbols : List.of();
    }

    /**
     * Partition for a symbol. If symbols list is set and symbol is in it: partition = index % partitionCount.
     * Otherwise: partition = Math.abs(symbol.hashCode()) % partitionCount.
     */
    public static int partition(String symbol, int partitionCount, List<String> symbols) {
        if (partitionCount <= 0) return 0;
        if (symbols != null && !symbols.isEmpty() && symbol != null) {
            int idx = symbols.indexOf(symbol);
            if (idx >= 0) return idx % partitionCount;
        }
        int h = symbol != null ? symbol.hashCode() : 0;
        return Math.abs(h) % partitionCount;
    }

    /** @deprecated Use {@link #partition(String, int, List)} with symbol list for explicit mapping. */
    public static int partition(String symbol, int partitionCount) {
        return partition(symbol, partitionCount, List.of());
    }

    public int getPartitionCount() {
        return publishers.size();
    }

    public List<String> getSymbols() {
        return symbols;
    }

    /**
     * Publish order to the partition that owns the order's symbol.
     * @return true if published successfully
     */
    public boolean publish(Order order) {
        int p = partition(order.getSymbol(), publishers.size(), symbols);
        if (publishers.size() > 1) {
            System.out.println("[Gateway] Routing order " + order.getId() + " " + order.getSymbol() + " -> partition " + p);
        }
        return publishers.get(p).publish(order);
    }

    @Override
    public void close() {
        for (OrderPublisher p : publishers) {
            p.close();
        }
    }
}
