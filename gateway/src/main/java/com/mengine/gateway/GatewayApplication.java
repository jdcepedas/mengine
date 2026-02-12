package com.mengine.gateway;

import com.mengine.gateway.config.GatewayConfig;
import io.aeron.driver.MediaDriver;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Gateway: HTTP (WebFlux) -> Aeron (orders); read APIs -> ME Core + DB.
 * Option A bootstrap: start embedded MediaDriver in main() if GW_AERON_DIR not set.
 */
@SpringBootApplication
public class GatewayApplication {

    /** Set by main() when we start an embedded driver; closed by GatewayAeronConfig on shutdown. */
    static volatile MediaDriver embeddedMediaDriver;

    public static void main(String[] args) {
        GatewayConfig config = GatewayConfig.load();
        String aeronDir = config.getAeronDir();
        if (aeronDir == null || aeronDir.isBlank()) {
            MediaDriver.Context driverCtx = new MediaDriver.Context();
            embeddedMediaDriver = MediaDriver.launch(driverCtx);
            aeronDir = driverCtx.aeronDirectoryName();
            System.setProperty("gateway.aeron.dir", aeronDir);
        }
        SpringApplication.run(GatewayApplication.class, args);
    }

    static MediaDriver getEmbeddedMediaDriver() {
        return embeddedMediaDriver;
    }
}
