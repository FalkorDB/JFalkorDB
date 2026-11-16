package com.falkordb.impl.api;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * A loopback TCP server for tests that must not need FalkorDB, for the two behaviours a RESP server can't
 * show: it accepts connections and either closes them at once or never sends a byte. To answer commands, use
 * {@link FakeRedisServer}.
 */
final class FakeServer implements AutoCloseable {

    enum Mode {
        /** Accept and close straight away. */
        CLOSE,
        /** Accept and never send a byte. */
        SILENT
    }

    private final ServerSocket server;
    private final Mode mode;
    private final List<Socket> open = new CopyOnWriteArrayList<>();
    private final Thread acceptor;
    private volatile boolean closed;

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
        while (!closed) {
            try {
                Socket socket = server.accept();
                if (closed || mode == Mode.CLOSE) {
                    socket.close();
                    continue;
                }
                open.add(socket);
            } catch (IOException e) {
                return;
            }
        }
    }

    @Override
    public void close() throws IOException {
        closed = true;
        server.close();
        // A connection the acceptor took just before the server socket closed must be closed too: wait for the
        // acceptor to finish, so none is left open after the test.
        try {
            acceptor.join(TimeUnit.SECONDS.toMillis(5));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        for (Socket socket : open) {
            socket.close();
        }
    }
}
