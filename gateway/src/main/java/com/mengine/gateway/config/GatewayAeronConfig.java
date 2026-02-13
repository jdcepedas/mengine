package com.mengine.gateway.config;

import com.mengine.gateway.GatewayApplication;
import com.mengine.gateway.aeron.OrderPublisher;
import com.mengine.gateway.client.JdbcTradeQuery;
import com.mengine.gateway.client.MeCoreClient;
import com.mengine.gateway.client.TradeQuery;
import io.aeron.Aeron;
import io.aeron.driver.MediaDriver;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    public OrderPublisher orderPublisher(Aeron aeron, GatewayConfig config) {
        return new OrderPublisher(aeron, config.getAeronChannel(), config.getAeronStreamId());
    }

    @Bean
    public MeCoreClient meCoreClient(GatewayConfig config) {
        return new MeCoreClient(config.getMeCoreUrl());
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
    public DisposableBean aeronShutdown(OrderPublisher orderPublisher, Aeron aeron, Optional<MediaDriver> embeddedDriver) {
        return () -> {
            try {
                orderPublisher.close();
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
