package com.mengine.core.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Configuration for Matching Engine Core.
 * Environment variables override config file.
 */
public class EngineConfig {

    private static final int DEFAULT_BUFFER_SIZE = 65536;
    private static final int DEFAULT_MATCHING_TIMEOUT_MS = 200;
    private static final int DEFAULT_STANDARD_DELAY_MS = 1000;
    private static final int DEFAULT_QUERY_PORT = 8081;
    private static final String DEFAULT_AERON_CHANNEL = "aeron:ipc";
    private static final int DEFAULT_AERON_STREAM_ID = 10;
    private static final String DEFAULT_AERON_DIR = "";
    private static final String DEFAULT_JOURNAL_DIR = "journal";
    private static final String DEFAULT_DB_URL = "";
    private static final String DEFAULT_DB_USER = "mengine";
    private static final String DEFAULT_DB_PASSWORD = "mengine";

    private final int bufferSize;
    private final int matchingTimeoutMs;
    private final int standardDelayMs;
    private final int queryPort;
    private final String aeronChannel;
    private final int aeronStreamId;
    private final String aeronDir;
    private final String journalDir;
    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;

    public EngineConfig(int bufferSize, int matchingTimeoutMs, int standardDelayMs,
                        int queryPort, String aeronChannel, int aeronStreamId, String aeronDir,
                        String journalDir, String dbUrl, String dbUser, String dbPassword) {
        this.bufferSize = bufferSize;
        this.matchingTimeoutMs = matchingTimeoutMs;
        this.standardDelayMs = standardDelayMs;
        this.queryPort = queryPort;
        this.aeronChannel = aeronChannel;
        this.aeronStreamId = aeronStreamId;
        this.aeronDir = aeronDir;
        this.journalDir = journalDir;
        this.dbUrl = dbUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
    }

    public static EngineConfig load() {
        Properties props = loadConfigFile();
        return new EngineConfig(
                getInt(props, "ME_BUFFER_SIZE", DEFAULT_BUFFER_SIZE),
                getInt(props, "ME_MATCHING_TIMEOUT_MS", DEFAULT_MATCHING_TIMEOUT_MS),
                getInt(props, "ME_STANDARD_DELAY_MS", DEFAULT_STANDARD_DELAY_MS),
                getInt(props, "ME_QUERY_PORT", DEFAULT_QUERY_PORT),
                getString(props, "ME_AERON_CHANNEL", DEFAULT_AERON_CHANNEL),
                getInt(props, "ME_AERON_STREAM_ID", DEFAULT_AERON_STREAM_ID),
                getString(props, "ME_AERON_DIR", DEFAULT_AERON_DIR),
                getString(props, "ME_JOURNAL_DIR", DEFAULT_JOURNAL_DIR),
                getString(props, "ME_DB_URL", DEFAULT_DB_URL),
                getString(props, "ME_DB_USER", DEFAULT_DB_USER),
                getString(props, "ME_DB_PASSWORD", DEFAULT_DB_PASSWORD)
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

    private static String getString(Properties props, String envKey, String defaultValue) {
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isEmpty()) {
            return envValue;
        }
        String propKey = envKey.toLowerCase().replace("_", ".");
        String propValue = props.getProperty(propKey);
        return propValue != null && !propValue.isEmpty() ? propValue : defaultValue;
    }

    public int getBufferSize() { return bufferSize; }
    public int getMatchingTimeoutMs() { return matchingTimeoutMs; }
    public int getStandardDelayMs() { return standardDelayMs; }
    public int getQueryPort() { return queryPort; }
    public String getAeronChannel() { return aeronChannel; }
    public int getAeronStreamId() { return aeronStreamId; }
    public String getAeronDir() { return aeronDir; }
    public String getJournalDir() { return journalDir; }
    public String getDbUrl() { return dbUrl; }
    public String getDbUser() { return dbUser; }
    public String getDbPassword() { return dbPassword; }
}
