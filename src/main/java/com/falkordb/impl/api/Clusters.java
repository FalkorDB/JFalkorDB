package com.falkordb.impl.api;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.jedis.util.SafeEncoder;

/**
 * Redis Cluster support helpers: telling whether an endpoint is a cluster node, and reading the
 * slot-to-node table out of a {@code CLUSTER SLOTS} reply. Pure functions plus one probe, so the
 * interesting cases can be unit-tested against synthetic replies.
 */
final class Clusters {

    /** Number of hash slots in a Redis Cluster. */
    static final int SLOT_COUNT = 16384;

    private static final String REDIS_MODE_FIELD = "redis_mode:";
    private static final String CLUSTER_MODE = "cluster";

    /** {@code CLUSTER SLOTS} reports this instead of an address when the endpoint is not known. */
    private static final String UNKNOWN_ENDPOINT = "?";

    private Clusters() {}

    /**
     * Probes {@code seed} and reports whether it is a Redis Cluster node ({@code redis_mode:cluster}).
     *
     * <p>Anything less than a positive identification — the endpoint is down, refuses the credentials
     * or {@code INFO}, or reports another mode — answers {@code false}, so the caller stays on the
     * direct connection every earlier release would have used.
     *
     * @param seed        the endpoint to probe
     * @param probeConfig client config for the probe; must carry a bounded read timeout, so an endpoint
     *                    that accepts the connection but never answers cannot hang the first query
     * @return {@code true} if {@code seed} is a cluster node
     */
    static boolean detect(HostAndPort seed, JedisClientConfig probeConfig) {
        try (Jedis probe = new Jedis(seed, probeConfig)) {
            return isClusterInfo(probe.info("server"));
        } catch (JedisException ignored) {
            return false;
        }
    }

    /**
     * Reads the {@code redis_mode} field of an {@code INFO server} reply. Parsed line-wise, like the
     * Sentinel check, so a value merely appearing somewhere else in the reply — a {@code config_file}
     * path containing the word, say — cannot be mistaken for the mode.
     *
     * @param info an {@code INFO server} reply, or {@code null}
     * @return {@code true} if the reply reports {@code redis_mode:cluster}
     */
    static boolean isClusterInfo(@Nullable String info) {
        if (info == null) {
            return false;
        }
        for (String line : info.split("\r\n|\n|\r", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith(REDIS_MODE_FIELD)) {
                return CLUSTER_MODE.equalsIgnoreCase(
                        trimmed.substring(REDIS_MODE_FIELD.length()).trim());
            }
        }
        return false;
    }

    /**
     * Parses a single {@code host:port} cluster node address, delegating the split to Jedis' {@link
     * HostAndPort#from} so bracketed IPv6 literals work, but reporting failures as {@link
     * IllegalArgumentException} naming the offending value, since this is user-supplied configuration.
     *
     * @param address the address to parse
     * @return the parsed endpoint
     * @throws IllegalArgumentException if the address is null, blank, or not a valid {@code host:port}
     */
    static HostAndPort parseNode(@Nullable String address) {
        if (address == null || address.trim().isEmpty()) {
            throw new IllegalArgumentException("cluster node address must not be null or blank");
        }
        String trimmed = address.trim();
        HostAndPort parsed;
        try {
            parsed = HostAndPort.from(trimmed);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                    "cluster node address must be in host:port form, but was \"" + trimmed + "\"", e);
        }
        if (parsed.getHost() == null || parsed.getHost().trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "cluster node address must be in host:port form, but was \"" + trimmed + "\"");
        }
        if (parsed.getPort() < 1 || parsed.getPort() > 65535) {
            throw new IllegalArgumentException(
                    "cluster node port must be in [1, 65535], but was " + parsed.getPort() + " in \"" + trimmed + "\"");
        }
        return parsed;
    }

    /**
     * Turns a {@code CLUSTER SLOTS} reply into a table from slot to the primary that serves it.
     *
     * <p>Each entry of the reply is {@code [firstSlot, lastSlot, primary, replica...]} and each node is
     * {@code [host, port, ...]}. Replicas are ignored: reads and writes both go to the primary, which
     * is what FalkorDB's other clients do by default. A node reported without a usable host (empty or
     * {@code ?}) is reached at the address of the node that answered, as cluster clients conventionally
     * do. Malformed entries are skipped rather than failing the whole refresh.
     *
     * @param reply    the raw {@code CLUSTER SLOTS} reply
     * @param answerer the node the reply came from
     * @return a {@link #SLOT_COUNT}-long table; slots no entry covers are {@code null}
     */
    static HostAndPort[] parseSlots(@Nullable List<Object> reply, HostAndPort answerer) {
        HostAndPort[] table = new HostAndPort[SLOT_COUNT];
        if (reply == null) {
            return table;
        }
        for (Object entry : reply) {
            if (!(entry instanceof List<?>)) {
                continue;
            }
            List<?> range = (List<?>) entry;
            if (range.size() < 3 || !(range.get(0) instanceof Long) || !(range.get(1) instanceof Long)) {
                continue;
            }
            HostAndPort primary = parsePrimary(range.get(2), answerer);
            if (primary == null) {
                continue;
            }
            long first = Math.max(0, (Long) range.get(0));
            long last = Math.min(SLOT_COUNT - 1, (Long) range.get(1));
            for (long slot = first; slot <= last; slot++) {
                table[(int) slot] = primary;
            }
        }
        return table;
    }

    /**
     * Lists the distinct primaries of a slot table, in slot order.
     *
     * @param table a table from {@link #parseSlots}
     * @return each primary once
     */
    static List<HostAndPort> primaries(HostAndPort[] table) {
        List<HostAndPort> primaries = new ArrayList<>();
        for (HostAndPort node : table) {
            if (node != null && !primaries.contains(node)) {
                primaries.add(node);
            }
        }
        return primaries;
    }

    private static @Nullable HostAndPort parsePrimary(Object node, HostAndPort answerer) {
        if (!(node instanceof List<?>)) {
            return null;
        }
        List<?> fields = (List<?>) node;
        if (fields.size() < 2 || !(fields.get(1) instanceof Long)) {
            return null;
        }
        long port = (Long) fields.get(1);
        if (port < 1 || port > 65535) {
            return null;
        }
        return new HostAndPort(hostOf(fields.get(0), answerer), (int) port);
    }

    private static String hostOf(@Nullable Object field, HostAndPort answerer) {
        String host = null;
        if (field instanceof byte[]) {
            host = SafeEncoder.encode((byte[]) field);
        } else if (field instanceof String) {
            host = (String) field;
        }
        if (host == null || host.isEmpty() || UNKNOWN_ENDPOINT.equals(host)) {
            return answerer.getHost();
        }
        return host;
    }
}
