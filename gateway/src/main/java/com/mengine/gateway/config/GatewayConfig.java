package com.mengine.gateway.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class GatewayConfig {

    private static final int DEFAULT_HTTP_PORT = 8080;
    private static final String DEFAULT_AERON_CHANNEL = "aeron:ipc";
    private static final int DEFAULT_AERON_STREAM_ID = 10;
    private static final String DEFAULT_ME_CORE_URL = "http://localhost:8081";
    private static final String DEFAULT_DB_URL = "";
    private static final String DEFAULT_AERON_DIR = "";
    private static final String DEFAULT_GW_SYMBOLS = "AAPL,MSFT";

    private final int httpPort;
    private final String aeronChannel;
    private final int aeronStreamId;
    private final String aeronDir;
    private final String meCoreUrl;
    private final List<String> meCoreUrls;
    private final List<Integer> aeronStreamIds;
    private final List<String> symbols;
    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;

    public GatewayConfig(int httpPort, String aeronChannel, int aeronStreamId, String aeronDir,
                         String meCoreUrl, List<String> meCoreUrls, List<Integer> aeronStreamIds,
                         List<String> symbols, String dbUrl, String dbUser, String dbPassword) {
        this.httpPort = httpPort;
        this.aeronChannel = aeronChannel;
        this.aeronStreamId = aeronStreamId;
        this.aeronDir = aeronDir;
        this.meCoreUrl = meCoreUrl == null ? DEFAULT_ME_CORE_URL : (meCoreUrl.endsWith("/") ? meCoreUrl.substring(0, meCoreUrl.length() - 1) : meCoreUrl);
        this.meCoreUrls = meCoreUrls != null && !meCoreUrls.isEmpty() ? meCoreUrls : List.of(this.meCoreUrl);
        this.aeronStreamIds = aeronStreamIds != null && !aeronStreamIds.isEmpty() ? aeronStreamIds : List.of(aeronStreamId);
        this.symbols = symbols != null ? symbols : List.of();
        this.dbUrl = dbUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
    }

    public static GatewayConfig load() {
        Properties props = loadConfigFile();
        String urlsStr = getString(props, "GW_ME_CORE_URLS", "");
        String streamIdsStr = getString(props, "GW_AERON_STREAM_IDS", "");
        String symbolsStr = getString(props, "GW_SYMBOLS", DEFAULT_GW_SYMBOLS);
        List<String> meCoreUrls = parseCommaSeparated(urlsStr);
        List<Integer> aeronStreamIds = parseCommaSeparatedInt(streamIdsStr);
        List<String> symbols = parseCommaSeparatedSymbols(symbolsStr);
        return new GatewayConfig(
                getInt(props, "GW_HTTP_PORT", DEFAULT_HTTP_PORT),
                getString(props, "GW_AERON_CHANNEL", DEFAULT_AERON_CHANNEL),
                getInt(props, "GW_AERON_STREAM_ID", DEFAULT_AERON_STREAM_ID),
                getString(props, "GW_AERON_DIR", DEFAULT_AERON_DIR),
                getString(props, "GW_ME_CORE_URL", DEFAULT_ME_CORE_URL),
                meCoreUrls,
                aeronStreamIds,
                symbols,
                getString(props, "GW_DB_URL", DEFAULT_DB_URL),
                getString(props, "GW_DB_USER", "mengine"),
                getString(props, "GW_DB_PASSWORD", "mengine")
        );
    }

    private static List<String> parseCommaSeparatedSymbols(String s) {
        if (s == null || s.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String part : s.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    private static List<String> parseCommaSeparated(String s) {
        if (s == null || s.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String part : s.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) out.add(t.endsWith("/") ? t.substring(0, t.length() - 1) : t);
        }
        return out;
    }

    private static List<Integer> parseCommaSeparatedInt(String s) {
        if (s == null || s.isBlank()) return List.of();
        List<Integer> out = new ArrayList<>();
        for (String part : s.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                try {
                    out.add(Integer.parseInt(t));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return out;
    }

    private static Properties loadConfigFile() {
        Properties props = new Properties();
        for (Path path : new Path[]{
                Path.of("gateway.properties"),
                Path.of("config/gateway.properties")
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
        String v = System.getenv(envKey);
        if (v != null && !v.isEmpty()) {
            try {
                return Integer.parseInt(v);
            } catch (NumberFormatException ignored) {
            }
        }
        String p = props.getProperty(envKey.toLowerCase().replace("_", "."));
        if (p != null && !p.isEmpty()) {
            try {
                return Integer.parseInt(p);
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private static String getString(Properties props, String envKey, String defaultValue) {
        String v = System.getenv(envKey);
        if (v != null && !v.isEmpty()) return v;
        String p = props.getProperty(envKey.toLowerCase().replace("_", "."));
        return p != null && !p.isEmpty() ? p : defaultValue;
    }

    public int getHttpPort() { return httpPort; }
    public String getAeronChannel() { return aeronChannel; }
    public int getAeronStreamId() { return aeronStreamId; }
    public String getAeronDir() { return aeronDir; }
    public String getMeCoreUrl() { return meCoreUrl; }
    public List<String> getMeCoreUrls() { return meCoreUrls; }
    public List<Integer> getAeronStreamIds() { return aeronStreamIds; }
    public int getPartitionCount() { return Math.min(meCoreUrls.size(), aeronStreamIds.size()); }
    public List<String> getSymbols() { return symbols; }
    public String getDbUrl() { return dbUrl; }
    public String getDbUser() { return dbUser; }
    public String getDbPassword() { return dbPassword; }
}
