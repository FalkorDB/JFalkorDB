package com.falkordb.impl.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A loopback TCP server for tests that must not need FalkorDB: it accepts connections and, depending on the
 * {@link Mode}, closes them at once, never answers, or answers each command ({@code +PONG} to a {@code PING},
 * {@code +OK} to anything else) and records what it received.
 */
final class FakeServer implements AutoCloseable {

    enum Mode {
        /** Accept and close straight away. */
        CLOSE,
        /** Accept and never send a byte. */
        SILENT,
        /** Answer each command, {@code +PONG} to a {@code PING} and {@code +OK} to anything else. */
        ANSWER
    }

    private final ServerSocket server;
    private final Mode mode;
    private final List<Socket> open = new CopyOnWriteArrayList<>();
    private final StringBuffer received = new StringBuffer();
    private final Thread acceptor;

    FakeServer(Mode mode) throws IOException {
        this.server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        this.mode = mode;
        this.acceptor = new Thread(this::acceptLoop, "fake-server-" + mode);
        acceptor.setDaemon(true);
        acceptor.start();
    }

    String host() {
        return server.getInetAddress().getHostAddress();
    }

    int port() {
        return server.getLocalPort();
    }

    String address() {
        return host() + ":" + port();
    }

    private void acceptLoop() {
        while (!server.isClosed()) {
            try {
                Socket socket = server.accept();
                if (mode == Mode.CLOSE) {
                    socket.close();
                    continue;
                }
                open.add(socket);
                if (mode == Mode.ANSWER) {
                    Thread responder = new Thread(() -> answer(socket), "fake-server-answer");
                    responder.setDaemon(true);
                    responder.start();
                }
            } catch (IOException e) {
                return;
            }
        }
    }

    /** @return everything received so far, in {@link Mode#ANSWER} mode */
    String received() {
        return received.toString();
    }

    private void answer(Socket socket) {
        byte[] buffer = new byte[4096];
        try (InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream()) {
            int read;
            while ((read = in.read(buffer)) >= 0) {
                String chunk = new String(buffer, 0, read, StandardCharsets.US_ASCII);
                received.append(chunk);
                // Jedis can send several commands in one write; each is a RESP array starting a line with '*'.
                String reply = chunk.contains("PING") ? "+PONG\r\n" : "+OK\r\n";
                StringBuilder replies = new StringBuilder();
                for (int i = 0; i < read; i++) {
                    if (chunk.charAt(i) == '*' && (i == 0 || chunk.charAt(i - 1) == '\n')) {
                        replies.append(reply);
                    }
                }
                out.write(replies.toString().getBytes(StandardCharsets.US_ASCII));
                out.flush();
            }
        } catch (IOException ignored) {
            // the test closed the connection
        }
    }

    @Override
    public void close() throws IOException {
        server.close();
        for (Socket socket : open) {
            socket.close();
        }
    }
}
