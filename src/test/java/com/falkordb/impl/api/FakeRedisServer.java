package com.falkordb.impl.api;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * A minimal in-process RESP server for unit tests: it parses each command into its words, hands them
 * to a handler, and writes back whatever raw RESP the handler returns. Unrecognised commands should be
 * answered {@code +OK} by the handler, which is what Jedis' connection setup needs.
 *
 * <p>This lets the cluster routing be tested without Docker: a "cluster" is just several of these,
 * one of which answers {@code CLUSTER SLOTS} with the others' addresses.
 */
final class FakeRedisServer implements AutoCloseable {

    private final ServerSocket server;
    private final Function<List<String>, String> handler;
    private final List<List<String>> received = new CopyOnWriteArrayList<>();
    private final List<Socket> clients = new CopyOnWriteArrayList<>();
    private final Thread acceptor;
    private volatile boolean closed;

    FakeRedisServer(Function<List<String>, String> handler) {
        this.handler = handler;
        try {
            this.server = new ServerSocket(0, 50, InetAddress.getByAddress(new byte[] {127, 0, 0, 1}));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        this.acceptor = new Thread(this::acceptLoop, "fake-redis-acceptor");
        this.acceptor.setDaemon(true);
        this.acceptor.start();
    }

    int port() {
        return server.getLocalPort();
    }

    String address() {
        return "127.0.0.1:" + port();
    }

    /** Every command this server has received, oldest first, upper-casing nothing. */
    List<List<String>> received() {
        return new ArrayList<>(received);
    }

    /** How many received commands start with the given words (compared case-insensitively). */
    long count(String... prefix) {
        return received.stream().filter(c -> startsWith(c, prefix)).count();
    }

    static boolean startsWith(List<String> command, String... prefix) {
        if (command.size() < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (!command.get(i).equalsIgnoreCase(prefix[i])) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void close() {
        closed = true;
        try {
            server.close();
        } catch (IOException ignored) {
            // shutting down
        }
        // The socket may finish closing only once the acceptor wakes up, and a connection it accepts meanwhile
        // must be closed too: wait for it, so no client is left being served.
        try {
            acceptor.join(TimeUnit.SECONDS.toMillis(5));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        for (Socket client : clients) {
            try {
                client.close();
            } catch (IOException ignored) {
                // shutting down
            }
        }
    }

    private void acceptLoop() {
        while (!closed) {
            try {
                Socket client = server.accept();
                if (closed) {
                    client.close();
                    return;
                }
                clients.add(client);
                Thread worker = new Thread(() -> serve(client), "fake-redis-client");
                worker.setDaemon(true);
                worker.start();
            } catch (IOException e) {
                return;
            }
        }
    }

    private void serve(Socket client) {
        try (InputStream in = new BufferedInputStream(client.getInputStream());
                OutputStream out = client.getOutputStream()) {
            List<String> command;
            while ((command = readCommand(in)) != null) {
                received.add(command);
                out.write(handler.apply(command).getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        } catch (IOException ignored) {
            // the client went away
        }
    }

    private static List<String> readCommand(InputStream in) throws IOException {
        String header = readLine(in);
        if (header == null || !header.startsWith("*")) {
            return null;
        }
        int words;
        try {
            words = Integer.parseInt(header.substring(1));
        } catch (NumberFormatException e) {
            return null;
        }
        List<String> command = new ArrayList<>(words);
        for (int i = 0; i < words; i++) {
            String length = readLine(in);
            if (length == null || !length.startsWith("$")) {
                return null;
            }
            int size;
            try {
                size = Integer.parseInt(length.substring(1));
            } catch (NumberFormatException e) {
                return null;
            }
            byte[] bytes = new byte[size];
            for (int read = 0; read < bytes.length; ) {
                int n = in.read(bytes, read, bytes.length - read);
                if (n < 0) {
                    return null;
                }
                read += n;
            }
            readLine(in); // trailing CRLF
            command.add(new String(bytes, StandardCharsets.UTF_8));
        }
        return command;
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                line.write(b);
            }
        }
        return b == -1 && line.size() == 0 ? null : line.toString("UTF-8");
    }

    // --- RESP builders -------------------------------------------------------------------------

    static String ok() {
        return "+OK\r\n";
    }

    static String bulk(String value) {
        return "$" + value.getBytes(StandardCharsets.UTF_8).length + "\r\n" + value + "\r\n";
    }

    static String integer(long value) {
        return ":" + value + "\r\n";
    }

    static String error(String message) {
        return "-" + message + "\r\n";
    }

    static String array(String... elements) {
        StringBuilder reply = new StringBuilder("*").append(elements.length).append("\r\n");
        for (String element : elements) {
            reply.append(element);
        }
        return reply.toString();
    }

    /** A {@code CLUSTER SLOTS} entry for one slot range served by one primary. */
    static String slotRange(int first, int last, String host, int port) {
        return array(integer(first), integer(last), array(bulk(host), integer(port), bulk("node-" + port)));
    }
}
