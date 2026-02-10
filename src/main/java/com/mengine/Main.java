package com.mengine;

import com.mengine.api.Server;
import com.mengine.config.EngineConfig;

/**
 * Application entry point for the Matching Engine.
 */
public class Main {

    public static void main(String[] args) {
        EngineConfig config = EngineConfig.load();
        Server server = new Server(config);
        server.start();
    }
}
