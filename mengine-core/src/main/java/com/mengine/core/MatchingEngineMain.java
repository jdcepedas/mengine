package com.mengine.core;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;
import com.mengine.core.aeron.AeronOrderSubscriber;
import com.mengine.core.buffer.OrderEvent;
import com.mengine.core.buffer.TradeEvent;
import com.mengine.core.config.EngineConfig;
import com.mengine.core.journal.Journaler;
import com.mengine.core.matching.MatchResult;
import com.mengine.core.matching.MatchingEngine;
import com.mengine.core.model.OrderRegistry;
import com.mengine.core.notification.NotificationService;
import com.mengine.core.persistence.JdbcTradeRepository;
import com.mengine.core.persistence.InMemoryTradeRepository;
import com.mengine.core.persistence.TradeRepository;
import com.mengine.core.query.QueryApi;
import com.mengine.model.Order;
import com.mengine.model.Trade;
import io.aeron.driver.MediaDriver;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadFactory;

/**
 * Matching Engine Core: Aeron subscriber -> Input Disruptor (Matcher + Journaler)
 * -> Output Disruptor (DB writer + Notification) + Query API.
 */
public class MatchingEngineMain {

    public static void main(String[] args) throws Exception {
        EngineConfig config = EngineConfig.load();

        String aeronDirConfig = config.getAeronDir();
        MediaDriver driver;
        String aeronDir;
        if (aeronDirConfig != null && !aeronDirConfig.isBlank()) {
            // Connect to existing driver (e.g. Gateway's) - do not launch our own
            driver = null;
            aeronDir = aeronDirConfig;
            System.out.println("Aeron Media Driver directory (connect only): " + aeronDir);
        } else {
            MediaDriver.Context driverCtx = new MediaDriver.Context();
            driverCtx.dirDeleteOnStart(true);
            driver = MediaDriver.launch(driverCtx);
            aeronDir = driverCtx.aeronDirectoryName();
            System.out.println("Aeron Media Driver directory: " + aeronDir);
        }
        try {
            OrderRegistry orderRegistry = new OrderRegistry();
            MatchingEngine matchingEngine = new MatchingEngine(config.getMatchingTimeoutMs());

            // Optional journal replay

            Journaler journaler = new Journaler(Path.of(config.getJournalDir()));
            /*
            for (Order order : journaler.getJournal().replay()) {
                orderRegistry.put(order);
                matchingEngine.match(order);
            }
            */

            TradeRepository tradeRepository = createTradeRepository(config);
            NotificationService notificationService = new NotificationService(config.getStandardDelayMs());

            int bufferSize = config.getBufferSize();
            ThreadFactory threadFactory = DaemonThreadFactory.INSTANCE;

            // Output Disruptor: trades -> DB writer + Notification
            Disruptor<TradeEvent> outputDisruptor = new Disruptor<>(TradeEvent::new, bufferSize, threadFactory);
            List<Trade> batch = new ArrayList<>();
            int batchSize = 50;
            outputDisruptor.handleEventsWith(
                    (event, sequence, endOfBatch) -> {
                        Trade t = event.getTrade();
                        if (t != null) {
                            batch.add(t);
                            if (batch.size() >= batchSize || endOfBatch) {
                                tradeRepository.saveBatch(new ArrayList<>(batch));
                                batch.clear();
                            }
                        }
                        event.clear();
                    },
                    (event, sequence, endOfBatch) -> {
                        Trade t = event.getTrade();
                        if (t != null) {
                            notificationService.deliverTrade(t);
                        }
                        event.clear();
                    }
            );
            RingBuffer<TradeEvent> outputRing = outputDisruptor.getRingBuffer();
            outputDisruptor.start();

            // Matcher: publishes to Output Disruptor
            Disruptor<OrderEvent> inputDisruptor = new Disruptor<>(OrderEvent::new, bufferSize, threadFactory);
            inputDisruptor.handleEventsWith(
                    (event, sequence, endOfBatch) -> {
                        Order order = event.getOrder();
                        if (order != null) {
                            orderRegistry.put(order);
                            MatchResult result = matchingEngine.match(order);
                            orderRegistry.put(result.getOrder());
                            for (Trade trade : result.getTrades()) {
                                long seq = outputRing.next();
                                try {
                                    outputRing.get(seq).setTrade(trade);
                                } finally {
                                    outputRing.publish(seq);
                                }
                            }
                        }
                        event.clear();
                    },
                    journaler
            );
            RingBuffer<OrderEvent> inputRing = inputDisruptor.getRingBuffer();
            inputDisruptor.start();

            // Aeron subscriber thread: publishes to Input Disruptor
            AeronOrderSubscriber subscriber = new AeronOrderSubscriber(
                    config.getAeronChannel(),
                    config.getAeronStreamId(),
                    aeronDir,
                    order -> {
                        try {
                            long seq = inputRing.tryNext();
                            try {
                                inputRing.get(seq).setOrder(order);
                            } finally {
                                inputRing.publish(seq);
                            }
                        } catch (com.lmax.disruptor.InsufficientCapacityException ignored) {
                            System.out.println("Insufficient capacity exception: " + ignored.getMessage());
                        }
                    }
            );
            subscriber.start();
            Thread aeronThread = new Thread(subscriber::run, "aeron-subscriber");
            aeronThread.setDaemon(true);
            aeronThread.start();

            // Query API for Gateway
            QueryApi queryApi = new QueryApi(matchingEngine, orderRegistry);
            queryApi.start(config.getQueryPort());
            System.out.println("Matching Engine Core started. Query API on port " + config.getQueryPort());

            Thread.currentThread().join();
        } finally {
            if (driver != null) {
                driver.close();
            }
        }
    }

    private static TradeRepository createTradeRepository(EngineConfig config) {
        String url = config.getDbUrl();
        if (url != null && !url.isBlank() && url.startsWith("jdbc:")) {
            return new JdbcTradeRepository(url, config.getDbUser(), config.getDbPassword());
        }
        return new InMemoryTradeRepository();
    }
}
