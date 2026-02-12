package com.mengine.gateway;

import com.mengine.gateway.aeron.OrderPublisher;
import com.mengine.gateway.client.JdbcTradeQuery;
import com.mengine.gateway.client.MeCoreClient;
import com.mengine.gateway.client.TradeQuery;
import com.mengine.gateway.config.GatewayConfig;
import io.aeron.Aeron;
import io.aeron.driver.MediaDriver;
import org.glassfish.grizzly.http.server.HttpServer;

/**
 * Gateway: HTTP -> Aeron (orders); read APIs -> ME Core + DB.
 */
public class GatewayMain {

    public static void main(String[] args) throws Exception {
        GatewayConfig config = GatewayConfig.load();

        MediaDriver driver = null;
        String aeronDir = config.getAeronDir();
        if (aeronDir == null || aeronDir.isBlank()) {
            MediaDriver.Context driverCtx = new MediaDriver.Context();
            driver = MediaDriver.launch(driverCtx);
            aeronDir = driverCtx.aeronDirectoryName();
        }

        try {
            Aeron.Context aeronCtx = new Aeron.Context().aeronDirectoryName(aeronDir);
            Aeron aeron = Aeron.connect(aeronCtx);

            OrderPublisher orderPublisher = new OrderPublisher(aeron, config.getAeronChannel(), config.getAeronStreamId());
            MeCoreClient meCoreClient = new MeCoreClient(config.getMeCoreUrl());
            TradeQuery tradeQuery = createTradeQuery(config);

            GatewayController controller = new GatewayController(orderPublisher, meCoreClient, tradeQuery);
            HttpServer httpServer = HttpServer.createSimpleServer(".", config.getHttpPort());
            httpServer.getServerConfiguration().addHttpHandler(controller, "/");
            httpServer.start();

            System.out.println("Gateway started on port " + config.getHttpPort() + ". ME Core: " + config.getMeCoreUrl());

            Thread.currentThread().join();
        } finally {
            if (driver != null) {
                driver.close();
            }
        }
    }

    private static TradeQuery createTradeQuery(GatewayConfig config) {
        String url = config.getDbUrl();
        if (url != null && !url.isBlank() && url.startsWith("jdbc:")) {
            return new JdbcTradeQuery(url, config.getDbUser(), config.getDbPassword());
        }
        return (symbol, limit) -> List.of();
    }
}
