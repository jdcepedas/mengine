package com.mengine.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Configuration for the Matching Engine.
 * Priority: environment variables > config file > defaults.
 * Defaults are tuned for M1 Pro (8/10 cores, 16GB RAM).
 */
public class EngineConfig {

    private static final int DEFAULT_BUFFER_SIZE = 65536;
    private static final int DEFAULT_MATCHING_TIMEOUT_MS = 200;
    private static final int DEFAULT_STANDARD_DELAY_MS = 1000;
    private static final int DEFAULT_SERVER_PORT = 8080;
    private static final int DEFAULT_MATCHING_THREADS = 4;
    private static final int DEFAULT_TRADE_STORE_SIZE = 1000;

    private final int bufferSize;
    private final int matchingTimeoutMs;
    private final int standardDelayMs;
    private final int serverPort;
    private final int matchingThreads;
    private final int tradeStoreSize;

    public EngineConfig(int bufferSize, int matchingTimeoutMs, int standardDelayMs,
                        int serverPort, int matchingThreads, int tradeStoreSize) {
        this.bufferSize = bufferSize;
        this.matchingTimeoutMs = matchingTimeoutMs;
        this.standardDelayMs = standardDelayMs;
        this.serverPort = serverPort;
        this.matchingThreads = matchingThreads;
        this.tradeStoreSize = tradeStoreSize;
    }

    public static EngineConfig load() {
        Properties props = loadConfigFile();
        return new EngineConfig(
                getInt(props, "ME_BUFFER_SIZE", DEFAULT_BUFFER_SIZE),
                getInt(props, "ME_MATCHING_TIMEOUT_MS", DEFAULT_MATCHING_TIMEOUT_MS),
                getInt(props, "ME_STANDARD_DELAY_MS", DEFAULT_STANDARD_DELAY_MS),
                getInt(props, "ME_SERVER_PORT", DEFAULT_SERVER_PORT),
                getInt(props, "ME_MATCHING_THREADS", DEFAULT_MATCHING_THREADS),
                getInt(props, "ME_TRADE_STORE_SIZE", DEFAULT_TRADE_STORE_SIZE)
        );
    }

    private static Properties loadConfigFile() {
        Properties props = new Properties();
        for (Path path : new Path[]{
                Path.of("mengine.properties"),
                Path.of("config/mengine.properties"),
                Path.of(System.getProperty("user.home") + "/.mengine.properties")
        }) {
            if (Files.exists(path)) {
                try {
                    props.load(Files.newInputStream(path));
                    break;
                } catch (IOException ignored) {
                }
            }
        }
        return props;
    }

    private static int getInt(Properties props, String envKey, int defaultValue) {
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isEmpty()) {
            try {
                return Integer.parseInt(envValue);
            } catch (NumberFormatException ignored) {
            }
        }
        String propKey = envKey.toLowerCase().replace("_", ".");
        String propValue = props.getProperty(propKey);
        if (propValue != null && !propValue.isEmpty()) {
            try {
                return Integer.parseInt(propValue);
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public int getMatchingTimeoutMs() {
        return matchingTimeoutMs;
    }

    public int getStandardDelayMs() {
        return standardDelayMs;
    }

    public int getServerPort() {
        return serverPort;
    }

    public int getMatchingThreads() {
        return matchingThreads;
    }

    public int getTradeStoreSize() {
        return tradeStoreSize;
    }
}
