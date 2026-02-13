package com.mapharitechnologies.eccprayerbot.bridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
public class CallBridgeService {

    private static final Logger log = LoggerFactory.getLogger(CallBridgeService.class);

    private final BridgeProperties properties;
    private final AudioBridgeClient audioBridge;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ntgcalls-bridge");
        thread.setDaemon(true);
        return thread;
    });

    private NtgCallsBridge ntgCallsBridge;

    public CallBridgeService(BridgeProperties properties) {
        this.properties = properties;
        this.audioBridge = new AudioBridgeClient(properties);
    }

    @PostConstruct
    public void start() {
        if (!properties.isBridgeEnabled()) {
            log.info("Audio bridge mode not enabled (LISTENER_AUDIO_SOURCE != bridge)");
            return;
        }
        executor.submit(this::runBridge);
    }

    @PreDestroy
    public void stop() {
        executor.shutdownNow();
        try {
            executor.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        if (ntgCallsBridge != null) {
            ntgCallsBridge.close();
        }
        audioBridge.close();
    }

    private void runBridge() {
        log.info("Starting NTgCalls bridge to {}:{}", properties.getBridgeHost(), properties.getBridgePort());
        try {
            ntgCallsBridge = new NtgCallsBridge(properties, audioBridge::sendChunk);
            ntgCallsBridge.connect();
        } catch (Throwable ex) {
            log.error("Failed to initialize NTgCalls bridge", ex);
        }
    }
}
