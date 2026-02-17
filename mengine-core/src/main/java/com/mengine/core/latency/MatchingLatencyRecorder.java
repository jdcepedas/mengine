package com.mengine.core.latency;

import com.mengine.core.matching.MatchResult;
import com.mengine.model.Order;
import com.mengine.model.OrderType;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
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
    private static final long POLL_TIMEOUT_MS = 500;

    private final boolean enabled;
    private final Path logPath;
    private final BlockingQueue<LatencyRecord> queue;
    private final AtomicBoolean running;
    private volatile Thread writerThread;
    private static final AtomicBoolean firstEnqueueLogged = new AtomicBoolean(false);
    private static final AtomicBoolean firstWriteLogged = new AtomicBoolean(false);

    public MatchingLatencyRecorder(boolean enabled, String logPath) {
        this.enabled = enabled;
        this.logPath = logPath != null && !logPath.isEmpty() ? Path.of(logPath) : null;
        this.queue = enabled ? new LinkedBlockingQueue<>(QUEUE_CAPACITY) : null;
        this.running = new AtomicBoolean(false);
    }

    /**
     * Record latency for one order. No-op if disabled or queue full (never blocks).
     * e2eLatencyNs = time from API receive to match in nanoseconds (when apiReceivedAtEpochNs set).
     * e2eLatencyMs = same in milliseconds (for backward compatibility).
     */
    public void record(Order order, MatchResult result) {
        if (!enabled || queue == null) return;
        long timestampMs = System.currentTimeMillis();
        Instant nowInstant = Instant.now();
        long nowEpochNs = nowInstant.getEpochSecond() * 1_000_000_000L + nowInstant.getNano();
        long e2eLatencyNs = -1L;
        long e2eLatencyMs = -1L;
        if (order.getApiReceivedAtEpochNs() > 0) {
            e2eLatencyNs = nowEpochNs - order.getApiReceivedAtEpochNs();
            if (e2eLatencyNs < 0) e2eLatencyNs = -1L;
            e2eLatencyMs = e2eLatencyNs >= 0 ? e2eLatencyNs / 1_000_000 : -1L;
        } else if (order.getApiReceivedAtEpochMs() > 0) {
            e2eLatencyMs = timestampMs - order.getApiReceivedAtEpochMs();
            if (e2eLatencyMs < 0) e2eLatencyMs = -1L;
        }
        LatencyRecord rec = new LatencyRecord(
                order.getId(),
                order.getSymbol(),
                order.getType(),
                result.isMatched(),
                result.isPartial(),
                result.getTrades().size(),
                result.getMatchingTimeNs(),
                timestampMs,
                e2eLatencyMs,
                e2eLatencyNs
        );
        boolean offered = queue.offer(rec);
        if (!offered) {
            System.err.println("[ME Core] Latency queue full, record dropped for orderId=" + order.getId());
        }
    }

    /**
     * Start the background writer thread. No-op if disabled.
     */
    public void start() {
        if (!enabled || logPath == null) {
            if (enabled && logPath == null) {
                System.err.println("[ME Core] Latency log enabled but path is null; latency log disabled.");
            }
            return;
        }
        if (running.compareAndSet(false, true)) {
            System.out.println("[ME Core] Latency log enabled, path=" + logPath.toAbsolutePath());
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
                out.write("orderId,symbol,type,matched,partial,tradeCount,latencyNs,timestampMs,e2eLatencyMs,e2eLatencyNs");
                out.newLine();
                out.flush();
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
                    out.flush();
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
        out.write(',');
        out.write(rec.e2eLatencyMs >= 0 ? String.valueOf(rec.e2eLatencyMs) : "");
        out.write(',');
        out.write(rec.e2eLatencyNs >= 0 ? String.valueOf(rec.e2eLatencyNs) : "");
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
        /** End-to-end latency ms (API receive to match); -1 if not available. */
        final long e2eLatencyMs;
        /** End-to-end latency ns (API receive to match); -1 if not available. */
        final long e2eLatencyNs;

        LatencyRecord(String orderId, String symbol, OrderType type, boolean matched, boolean partial,
                      int tradeCount, long latencyNs, long timestampMs, long e2eLatencyMs, long e2eLatencyNs) {
            this.orderId = orderId;
            this.symbol = symbol;
            this.type = type;
            this.matched = matched;
            this.partial = partial;
            this.tradeCount = tradeCount;
            this.latencyNs = latencyNs;
            this.timestampMs = timestampMs;
            this.e2eLatencyMs = e2eLatencyMs;
            this.e2eLatencyNs = e2eLatencyNs;
        }
    }
}
