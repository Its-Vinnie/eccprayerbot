package com.mapharitechnologies.eccprayerbot.bridge;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class BridgeProperties {

    private static final String SOURCE_BRIDGE = "bridge";
    private static final int DEFAULT_PORT = 5045;
    private static final String DEFAULT_HOST = "127.0.0.1";

    private final boolean bridgeEnabled;
    private final String bridgeHost;
    private final int bridgePort;
    private final String nativeLibrary;
    private final String ntgcallsParams;
    private final long chatId;
    private final boolean presentationMode;
    private final int retrySeconds;

    public BridgeProperties(Environment env) {
        String source = env.getProperty("LISTENER_AUDIO_SOURCE", "tgcaller").toLowerCase();
        this.bridgeEnabled = SOURCE_BRIDGE.equals(source);
        this.bridgeHost = env.getProperty("LISTENER_AUDIO_HOST", DEFAULT_HOST);
        this.bridgePort = parseInt(env.getProperty("LISTENER_AUDIO_PORT"), DEFAULT_PORT);
        this.nativeLibrary = env.getProperty("NTGCALLS_NATIVE_LIBRARY", "ntgcalls");
        this.ntgcallsParams = env.getProperty("NTGCALLS_PARAMS", "");
        this.chatId = parseLong(env.getProperty("LISTENER_CHAT_ID"), -1L);
        this.presentationMode = Boolean.parseBoolean(env.getProperty("NTGCALLS_PRESENTATION", "false"));
        this.retrySeconds = parseInt(env.getProperty("NTGCALLS_RETRY_SECONDS"), 5);
    }

    public boolean isBridgeEnabled() {
        return bridgeEnabled;
    }

    public String getBridgeHost() {
        return bridgeHost;
    }

    public int getBridgePort() {
        return bridgePort;
    }

    public String getNativeLibrary() {
        return nativeLibrary;
    }

    public String getNtgCallsParams() {
        return ntgcallsParams;
    }

    public long getChatId() {
        return chatId;
    }

    public boolean isPresentationMode() {
        return presentationMode;
    }

    public int getRetrySeconds() {
        return retrySeconds;
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static long parseLong(String value, long fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
