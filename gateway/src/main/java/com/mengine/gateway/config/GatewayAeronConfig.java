package com.mengine.gateway.config;

import com.mengine.gateway.GatewayApplication;
import com.mengine.gateway.aeron.OrderPublisher;
import com.mengine.gateway.aeron.OrderPublisherRouter;
import com.mengine.gateway.client.JdbcTradeQuery;
import com.mengine.gateway.client.MeCoreClient;
import com.mengine.gateway.client.MeCoreClientRouter;
import com.mengine.gateway.client.TradeQuery;
import io.aeron.Aeron;
import io.aeron.driver.MediaDriver;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Configuration
public class GatewayAeronConfig {

    @Bean
    public GatewayConfig gatewayConfig() {
        return GatewayConfig.load();
    }

    @Bean(destroyMethod = "")
    public Optional<MediaDriver> embeddedMediaDriver() {
        return Optional.ofNullable(GatewayApplication.getEmbeddedMediaDriver());
    }

    @Bean(destroyMethod = "")
    public Aeron aeron(GatewayConfig config, Optional<MediaDriver> embeddedDriver) {
        String dir = config.getAeronDir();
        if (dir == null || dir.isBlank()) {
            dir = System.getProperty("gateway.aeron.dir");
        }
        System.out.println("[Gateway] Aeron client connecting to directory: " + dir
            + " (ME Core must use the SAME path in ME_AERON_DIR)");
        return Aeron.connect(new Aeron.Context().aeronDirectoryName(dir));
    }

    @Bean
    public OrderPublisherRouter orderPublisherRouter(Aeron aeron, GatewayConfig config) {
        int partitionCount = config.getPartitionCount();
        List<Integer> streamIds = config.getAeronStreamIds();
        List<OrderPublisher> publishers = new ArrayList<>();
        for (int i = 0; i < partitionCount; i++) {
            int streamId = i < streamIds.size() ? streamIds.get(i) : config.getAeronStreamId() + i;
            publishers.add(new OrderPublisher(aeron, config.getAeronChannel(), streamId));
        }
        List<String> symbols = config.getSymbols();
        if (partitionCount > 1) {
            System.out.println("[Gateway] Partitioned mode: " + partitionCount + " OrderPublishers, stream IDs: " + streamIds + ", GW_SYMBOLS: " + symbols);
        } else {
            System.out.println("[Gateway] Single partition: 1 OrderPublisher (stream " + (streamIds.isEmpty() ? config.getAeronStreamId() : streamIds.get(0)) + "). Set GW_ME_CORE_URLS and GW_AERON_STREAM_IDS for symbol routing.");
        }
        return new OrderPublisherRouter(publishers, symbols);
    }

    @Bean
    public MeCoreClientRouter meCoreClientRouter(GatewayConfig config, OrderSymbolCache orderSymbolCache) {
        List<String> urls = config.getMeCoreUrls();
        if (urls.isEmpty()) {
            urls = List.of(config.getMeCoreUrl());
        }
        return new MeCoreClientRouter(urls, orderSymbolCache, config.getSymbols());
    }

    @Bean
    public MeCoreClient meCoreClient(MeCoreClientRouter router) {
        return router;
    }

    @Bean
    public TradeQuery tradeQuery(GatewayConfig config) {
        String url = config.getDbUrl();
        if (url != null && !url.isBlank() && url.startsWith("jdbc:")) {
            return new JdbcTradeQuery(url, config.getDbUser(), config.getDbPassword());
        }
        return (symbol, limit) -> List.of();
    }

    @Bean
    public DisposableBean aeronShutdown(OrderPublisherRouter orderPublisherRouter, Aeron aeron, Optional<MediaDriver> embeddedDriver) {
        return () -> {
            try {
                orderPublisherRouter.close();
            } finally {
                try {
                    aeron.close();
                } finally {
                    embeddedDriver.ifPresent(MediaDriver::close);
                }
            }
        };
    }
}
