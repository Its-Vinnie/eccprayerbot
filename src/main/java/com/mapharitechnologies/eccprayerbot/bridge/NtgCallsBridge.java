package com.mapharitechnologies.eccprayerbot.bridge;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class NtgCallsBridge implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(NtgCallsBridge.class);

    private final BridgeProperties properties;
    private final NtgCallsLibrary library;
    private final Pointer context;
    private final Consumer<byte[]> audioConsumer;
    private FrameCallback activeCallback;

    public NtgCallsBridge(BridgeProperties properties, Consumer<byte[]> audioConsumer) {
        this.properties = properties;
        this.audioConsumer = Objects.requireNonNull(audioConsumer, "Audio consumer is required");
        this.library = Native.load(properties.getNativeLibrary(), NtgCallsLibrary.class);
        this.context = library.ntg_init();
        registerFrameCallback();
    }

    public void connect() {
        if (!properties.isBridgeEnabled()) {
            log.warn("Bridge is disabled, skipping native call connect");
            return;
        }
        if (properties.getChatId() <= 0) {
            log.warn("LISTENER_CHAT_ID is not set. Unable to join Telegram call from Java bridge.");
            return;
        }
        int result = library.ntg_connect(
                context,
                properties.getChatId(),
                properties.getNtgCallsParams(),
                properties.isPresentationMode(),
                Pointer.NULL
        );
        log.info("ntgcalls connect returned {} for chat {}", result, properties.getChatId());
    }

    private void registerFrameCallback() {
        this.activeCallback = new FrameCallback();
        library.ntg_on_frames(context, activeCallback, Pointer.NULL);
    }

    @Override
    public void close() {
        try {
            if (context != null) {
                library.ntg_destroy(context);
            }
        } catch (Throwable ex) {
            log.warn("Failed to destroy ntgcalls context", ex);
        }
    }

    private class FrameCallback implements NtgCallsLibrary.FrameCallback {
        @Override
        public void invoke(Pointer ctx, long chatId, int streamMode, int streamDevice, NtgFrame frame, long timestamp, Pointer userData) {
            if (frame == null || frame.data == null || frame.sizeData <= 0) {
                return;
            }
            if (!shouldForward(streamMode, streamDevice)) {
                return;
            }
            byte[] payload = frame.data.getByteArray(0, frame.sizeData);
            audioConsumer.accept(payload);
        }
    }

    private boolean shouldForward(int streamMode, int streamDevice) {
        // Forward speaker/playback frames only (incoming audio). Adjust if other modes are needed.
        return streamDevice == NtgStreamDevice.SPEAKER;
    }

    private interface NtgStreamDevice {
        int MICROPHONE = 0;
        int SPEAKER = 1;
        int CAMERA = 2;
        int SCREEN = 3;
    }

    private interface NtgCallsLibrary extends Library {
        Pointer ntg_init();

        void ntg_destroy(Pointer context);

        int ntg_connect(Pointer context, long chatId, String params, boolean presentation, Pointer async);

        int ntg_on_frames(Pointer context, FrameCallback callback, Pointer userData);

        interface FrameCallback extends Callback {
            void invoke(Pointer context, long chatId, int streamMode, int streamDevice, NtgFrame frame, long timestamp, Pointer userData);
        }
    }

    @Structure.FieldOrder({"data", "sizeData"})
    public static class NtgFrame extends Structure {
        public Pointer data;
        public int sizeData;

        public NtgFrame() {
            super();
        }
    }
}
