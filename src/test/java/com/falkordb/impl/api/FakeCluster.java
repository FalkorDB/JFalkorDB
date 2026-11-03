package com.falkordb.impl.api;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import redis.clients.jedis.util.JedisClusterCRC16;

/**
 * A cluster of {@link FakeRedisServer}s: each node answers {@code CLUSTER SLOTS} with the current slot
 * layout, {@code INFO} with {@code redis_mode:cluster} and {@code ECHO} with its own index, which is how
 * the tests tell which node a connection landed on. Everything else goes to {@code extra}, which
 * returns {@code null} to fall back to {@code +OK}.
 */
final class FakeCluster implements AutoCloseable {

    private final List<FakeRedisServer> nodes = new ArrayList<>();

    /** Slot owner per range start; replaced by {@link #splitEvenly} / {@link #assign}. */
    private volatile int[] ownerOfHalf;

    private volatile boolean reportEmptyHosts;

    private volatile boolean refuseSlotQueries;

    FakeCluster(int size, BiFunction<Integer, List<String>, String> extra) {
        ownerOfHalf = new int[] {0, Math.min(1, size - 1)};
        for (int i = 0; i < size; i++) {
            int index = i;
            nodes.add(new FakeRedisServer(command -> answer(index, command, extra)));
        }
    }

    private String answer(int index, List<String> command, BiFunction<Integer, List<String>, String> extra) {
        if (FakeRedisServer.startsWith(command, "CLUSTER", "SLOTS")) {
            return refuseSlotQueries ? FakeRedisServer.error("ERR not now") : slotsReply();
        }
        if (FakeRedisServer.startsWith(command, "INFO")) {
            return FakeRedisServer.bulk("# Server\r\nredis_mode:cluster\r\n");
        }
        if (FakeRedisServer.startsWith(command, "PING")) {
            return "+PONG\r\n";
        }
        if (FakeRedisServer.startsWith(command, "ECHO")) {
            return FakeRedisServer.bulk(String.valueOf(index));
        }
        String reply = extra == null ? null : extra.apply(index, command);
        return reply != null ? reply : FakeRedisServer.ok();
    }

    /** Slots 0-8191 belong to {@code lowOwner}, the rest to {@code highOwner}. */
    void assign(int lowOwner, int highOwner) {
        ownerOfHalf = new int[] {lowOwner, highOwner};
    }

    /** Answer {@code CLUSTER SLOTS} with an error, so the driver cannot relearn the layout. */
    void refuseSlotQueries(boolean value) {
        refuseSlotQueries = value;
    }

    /** Report nodes with an empty host, as a cluster behind a proxy does. */
    void reportEmptyHosts(boolean value) {
        reportEmptyHosts = value;
    }

    private String slotsReply() {
        String host = reportEmptyHosts ? "" : "127.0.0.1";
        int[] owners = ownerOfHalf;
        return FakeRedisServer.array(
                FakeRedisServer.slotRange(0, 8191, host, nodes.get(owners[0]).port()),
                FakeRedisServer.slotRange(
                        8192, 16383, host, nodes.get(owners[1]).port()));
    }

    FakeRedisServer node(int index) {
        return nodes.get(index);
    }

    int size() {
        return nodes.size();
    }

    List<String> addresses() {
        List<String> addresses = new ArrayList<>();
        for (FakeRedisServer node : nodes) {
            addresses.add(node.address());
        }
        return addresses;
    }

    /** A graph name whose slot falls in the lower half (0-8191) of the slot space. */
    static String lowGraph() {
        return graphInHalf(false);
    }

    /** A graph name whose slot falls in the upper half (8192-16383) of the slot space. */
    static String highGraph() {
        return graphInHalf(true);
    }

    private static String graphInHalf(boolean high) {
        for (int i = 0; ; i++) {
            String name = "graph-" + i;
            if ((JedisClusterCRC16.getSlot(name) >= 8192) == high) {
                return name;
            }
        }
    }

    @Override
    public void close() {
        for (FakeRedisServer node : nodes) {
            node.close();
        }
    }
}
