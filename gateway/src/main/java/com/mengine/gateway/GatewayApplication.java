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
        MediaDriver.Context driverCtx = new MediaDriver.Context();
        if (aeronDir != null && !aeronDir.isBlank()) {
            // Launch driver in this dir (e.g. /dev/shm/aeron in Docker shared volume)
            driverCtx.aeronDirectoryName(aeronDir);
            embeddedMediaDriver = MediaDriver.launch(driverCtx);
            System.setProperty("gateway.aeron.dir", aeronDir);
            System.out.println("Aeron Media Driver directory: " + aeronDir);
        } else {
            driverCtx.dirDeleteOnStart(true);
            embeddedMediaDriver = MediaDriver.launch(driverCtx);
            aeronDir = driverCtx.aeronDirectoryName();
            System.setProperty("gateway.aeron.dir", aeronDir);
            System.out.println("Aeron Media Driver directory: " + aeronDir);
        }
        SpringApplication.run(GatewayApplication.class, args);
    }

    public static MediaDriver getEmbeddedMediaDriver() {
        return embeddedMediaDriver;
    }
}
