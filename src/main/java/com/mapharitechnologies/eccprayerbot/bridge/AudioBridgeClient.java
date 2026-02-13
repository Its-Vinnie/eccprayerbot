package com.mapharitechnologies.eccprayerbot.bridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

public class AudioBridgeClient implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(AudioBridgeClient.class);

    private final BridgeProperties properties;
    private final Object lock = new Object();

    private Socket socket;
    private DataOutputStream output;

    public AudioBridgeClient(BridgeProperties properties) {
        this.properties = properties;
    }

    public void sendChunk(byte[] chunk) {
        if (chunk == null || chunk.length == 0) {
            return;
        }
        try {
            if (!ensureConnected()) {
                return;
            }
            output.writeInt(chunk.length);
            output.write(chunk);
            output.flush();
        } catch (IOException ex) {
            log.warn("Failed to send audio chunk, will retry", ex);
            closeSilently();
        }
    }

    private boolean ensureConnected() {
        synchronized (lock) {
            if (socket != null && socket.isConnected() && !socket.isClosed()) {
                return true;
            }
            closeSilently();
            try {
                socket = new Socket();
                InetSocketAddress address = new InetSocketAddress(
                        properties.getBridgeHost(),
                        properties.getBridgePort()
                );
                socket.connect(address, (int) TimeUnit.SECONDS.toMillis(3));
                output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                log.info("Connected to audio bridge at {}:{}", properties.getBridgeHost(), properties.getBridgePort());
                return true;
            } catch (IOException ex) {
                log.warn("Unable to connect to the audio bridge server", ex);
                closeSilently();
                return false;
            }
        }
    }

    private void closeSilently() {
        synchronized (lock) {
            if (output != null) {
                try {
                    output.close();
                } catch (IOException ignored) {
                }
                output = null;
            }
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
                socket = null;
            }
        }
    }

    @Override
    public void close() {
        closeSilently();
    }
}
