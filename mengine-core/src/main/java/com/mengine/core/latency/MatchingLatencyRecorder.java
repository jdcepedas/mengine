package com.mengine.core.latency;

import com.mengine.core.matching.MatchResult;
import com.mengine.model.Order;
import com.mengine.model.OrderType;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Records per-order matching latency and writes to a CSV file on a background thread.
 * Hot path only offers to a bounded queue; no I/O on the matching thread.
 */
public class MatchingLatencyRecorder {

    private static final int QUEUE_CAPACITY = 65536;
    private static final int FLUSH_EVERY_RECORDS = 100;
    private static final long POLL_TIMEOUT_MS = 500;

    private final boolean enabled;
    private final Path logPath;
    private final BlockingQueue<LatencyRecord> queue;
    private final AtomicBoolean running;
    private Thread writerThread;

    public MatchingLatencyRecorder(boolean enabled, String logPath) {
        this.enabled = enabled;
        this.logPath = logPath != null && !logPath.isEmpty() ? Path.of(logPath) : null;
        this.queue = enabled ? new LinkedBlockingQueue<>(QUEUE_CAPACITY) : null;
        this.running = new AtomicBoolean(false);
    }

    /**
     * Record latency for one order. No-op if disabled or queue full (never blocks).
     */
    public void record(Order order, MatchResult result) {
        if (!enabled || queue == null) return;
        LatencyRecord rec = new LatencyRecord(
                order.getId(),
                order.getSymbol(),
                order.getType(),
                result.isMatched(),
                result.isPartial(),
                result.getTrades().size(),
                result.getMatchingTimeNs(),
                System.currentTimeMillis()
        );
        queue.offer(rec);
    }

    /**
     * Start the background writer thread. No-op if disabled.
     */
    public void start() {
        if (!enabled || logPath == null) return;
        if (running.compareAndSet(false, true)) {
            writerThread = new Thread(this::runWriter, "matching-latency-writer");
            writerThread.setDaemon(true);
            writerThread.start();
        }
    }

    /**
     * Stop the writer, drain remaining records, flush and close the file.
     */
    public void stop() {
        if (!enabled || !running.get()) return;
        running.set(false);
        if (writerThread != null) {
            try {
                writerThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void runWriter() {
        try {
            Path parent = logPath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            try (BufferedWriter out = Files.newBufferedWriter(logPath, StandardCharsets.UTF_8)) {
                out.write("orderId,symbol,type,matched,partial,tradeCount,latencyNs,timestampMs");
                out.newLine();
                out.flush();
                int count = 0;
                while (running.get() || !queue.isEmpty()) {
                    LatencyRecord rec;
                    try {
                        rec = queue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    if (rec == null) continue;
                    writeLine(out, rec);
                    count++;
                    if (count >= FLUSH_EVERY_RECORDS) {
                        out.flush();
                        count = 0;
                    }
                }
                out.flush();
            }
        } catch (IOException e) {
            System.err.println("[ME Core] Latency log write error: " + e.getMessage());
        }
    }

    private static void writeLine(BufferedWriter out, LatencyRecord rec) throws IOException {
        out.write(escape(rec.orderId));
        out.write(',');
        out.write(escape(rec.symbol));
        out.write(',');
        out.write(rec.type == OrderType.BUY ? "BUY" : "SELL");
        out.write(',');
        out.write(rec.matched ? "true" : "false");
        out.write(',');
        out.write(rec.partial ? "true" : "false");
        out.write(',');
        out.write(String.valueOf(rec.tradeCount));
        out.write(',');
        out.write(String.valueOf(rec.latencyNs));
        out.write(',');
        out.write(String.valueOf(rec.timestampMs));
        out.newLine();
    }

    private static String escape(String s) {
        if (s == null) return "";
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static final class LatencyRecord {
        final String orderId;
        final String symbol;
        final OrderType type;
        final boolean matched;
        final boolean partial;
        final int tradeCount;
        final long latencyNs;
        final long timestampMs;

        LatencyRecord(String orderId, String symbol, OrderType type, boolean matched, boolean partial,
                      int tradeCount, long latencyNs, long timestampMs) {
            this.orderId = orderId;
            this.symbol = symbol;
            this.type = type;
            this.matched = matched;
            this.partial = partial;
            this.tradeCount = tradeCount;
            this.latencyNs = latencyNs;
            this.timestampMs = timestampMs;
        }
    }
}
