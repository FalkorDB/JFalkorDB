package com.falkordb.impl.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.jspecify.annotations.Nullable;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisSentinelPool;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.jedis.util.Pool;

/**
 * Redis Sentinel support: recognising a Sentinel endpoint, resolving the master it monitors, and
 * building the failover-aware pool that {@link DriverImpl} then holds like any other.
 *
 * <p>The auto-detection in {@link #detect} mirrors what the other FalkorDB clients do (falkordb-py,
 * falkordb-go and falkordb-ts all probe {@code INFO server} for {@code redis_mode:sentinel}, then
 * take the single master reported by {@code SENTINEL MASTERS}), so pointing any FalkorDB client at a
 * Sentinel behaves the same way in every language.
 *
 * <p>Detection is deliberately best-effort. Driver creation performs no I/O — the pool, and so this
 * probe, is resolved on first use — so a probe that cannot reach the endpoint, or that is refused
 * {@code INFO} by an ACL, falls back to the ordinary direct pool and lets the failure surface exactly
 * as it always did. Only once the endpoint has positively identified itself as a Sentinel do problems
 * become fatal: at that point a direct connection is certain to fail on every graph command, so
 * failing immediately with a clear error beats a baffling one later.
 */
final class Sentinels {

    /** The {@code INFO server} field identifying the server's mode, and the value Sentinel reports. */
    private static final String REDIS_MODE_FIELD = "redis_mode:";

    private static final String SENTINEL_MODE = "sentinel";

    /** The {@code SENTINEL MASTERS} reply field holding a monitored master's name. */
    private static final String MASTER_NAME_FIELD = "name";

    private Sentinels() {}

    /**
     * Detects whether {@code seed} is a Sentinel and, if so, builds a failover-aware pool for the
     * master it monitors.
     *
     * @param seed           the endpoint the caller asked to connect to, which may be a Sentinel or an
     *                       ordinary FalkorDB server
     * @param probeConfig    client config for the one-shot probe; unlike {@code sentinelConfig} this
     *                       must carry a bounded read timeout, so an endpoint that accepts the
     *                       connection but never answers cannot hang the first query
     * @param poolConfig     commons-pool2 sizing for the resulting pool
     * @param dataConfig     client config used for connections to the master (credentials, TLS, timeouts)
     * @param sentinelConfig client config used for connections to the Sentinels themselves
     * @return a {@link JedisSentinelPool} for the monitored master, or {@code null} when {@code seed}
     *     is not a Sentinel or could not be probed — in which case the caller should connect directly
     * @throws IllegalStateException if {@code seed} is a Sentinel but monitors anything other than
     *     exactly one master, so no master name can be inferred
     */
    static @Nullable Pool<Jedis> detect(
            HostAndPort seed,
            JedisClientConfig probeConfig,
            GenericObjectPoolConfig<Jedis> poolConfig,
            JedisClientConfig dataConfig,
            JedisClientConfig sentinelConfig) {
        Jedis probe;
        try {
            // Opened outside the guarded region below because Jedis authenticates while constructing,
            // so this can fail with an ACL error rather than a connection error. Failing to open the
            // probe tells us nothing about the endpoint and so must fall back like any other failed
            // probe -- in particular when Sentinel credentials were supplied for an endpoint that
            // turns out to be an ordinary server and rejects them.
            probe = new Jedis(seed, probeConfig);
        } catch (JedisException ignored) {
            return null;
        }
        List<Map<String, String>> masters;
        try {
            String info;
            try {
                info = probe.info("server");
            } catch (JedisException ignored) {
                // Unreachable, or INFO refused by an ACL: we cannot tell what this endpoint is, so
                // leave the caller on the direct path it would have taken before Sentinel support.
                return null;
            }
            if (!isSentinelInfo(info)) {
                return null;
            }
            // Past this point the endpoint has named itself a Sentinel, so a failure is worth
            // reporting: a direct connection would fail on every graph command anyway. Only losing the
            // connection outright still falls back.
            masters = probe.sentinelMasters();
        } catch (JedisConnectionException ignored) {
            return null;
        } finally {
            probe.close();
        }
        return pool(soleMasterName(masters), Collections.singleton(seed), poolConfig, dataConfig, sentinelConfig);
    }

    /**
     * Builds a failover-aware pool for an explicitly configured Sentinel deployment.
     *
     * @param masterName     name of the monitored master, as given to {@code sentinel monitor}
     * @param sentinels      the Sentinel endpoints to discover the master through; must not be empty
     * @param poolConfig     commons-pool2 sizing for the resulting pool
     * @param dataConfig     client config used for connections to the master
     * @param sentinelConfig client config used for connections to the Sentinels themselves
     * @return a pool that tracks the current master and follows failovers
     */
    static Pool<Jedis> pool(
            String masterName,
            Set<HostAndPort> sentinels,
            GenericObjectPoolConfig<Jedis> poolConfig,
            JedisClientConfig dataConfig,
            JedisClientConfig sentinelConfig) {
        return new JedisSentinelPool(masterName, sentinels, poolConfig, dataConfig, sentinelConfig);
    }

    /**
     * Reads the {@code redis_mode} field of an {@code INFO server} reply. Parsed line-wise rather than
     * with {@code contains("redis_mode:sentinel")} so that a value merely appearing somewhere else in
     * the reply — a {@code config_file} path containing the word, say — cannot be mistaken for the
     * mode. Redis separates INFO lines with CRLF; LF is accepted too so the parsing does not depend on
     * it.
     *
     * @param info an {@code INFO server} reply, or {@code null}
     * @return {@code true} if the reply reports {@code redis_mode:sentinel}
     */
    static boolean isSentinelInfo(@Nullable String info) {
        if (info == null) {
            return false;
        }
        for (String line : info.split("\r\n|\n|\r", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith(REDIS_MODE_FIELD)) {
                return SENTINEL_MODE.equalsIgnoreCase(
                        trimmed.substring(REDIS_MODE_FIELD.length()).trim());
            }
        }
        return false;
    }

    /**
     * Extracts the name of the one master a Sentinel monitors.
     *
     * <p>A Sentinel can monitor several masters, and then the endpoint alone does not say which one
     * the caller meant — the other FalkorDB clients reject this case for the same reason. JFalkorDB
     * can be told explicitly instead, so the message points at that escape hatch.
     *
     * @param masters the {@code SENTINEL MASTERS} reply
     * @return the sole master's name
     * @throws IllegalStateException if the Sentinel monitors zero or several masters, or if the reply
     *     carries no usable name
     */
    static String soleMasterName(@Nullable List<Map<String, String>> masters) {
        if (masters == null || masters.isEmpty()) {
            throw new IllegalStateException("The Sentinel monitors no master, so there is nothing to connect to. "
                    + "Configure the Sentinel with `sentinel monitor`, or name the master explicitly with "
                    + "FalkorDB.builder().sentinel(masterName, sentinels...).");
        }
        if (masters.size() > 1) {
            throw new IllegalStateException("The Sentinel monitors " + masters.size()
                    + " masters " + names(masters) + ", so the master to connect to is ambiguous. "
                    + "Name it explicitly with FalkorDB.builder().sentinel(masterName, sentinels...).");
        }
        String name = masters.get(0).get(MASTER_NAME_FIELD);
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalStateException("The Sentinel reported a master without a name. "
                    + "Name it explicitly with FalkorDB.builder().sentinel(masterName, sentinels...).");
        }
        return name.trim();
    }

    /** The monitored master names, for the ambiguity message above. */
    private static List<String> names(List<Map<String, String>> masters) {
        List<String> names = new ArrayList<>(masters.size());
        for (Map<String, String> master : masters) {
            names.add(master.get(MASTER_NAME_FIELD));
        }
        return names;
    }

    /**
     * Parses {@code host:port} Sentinel addresses, preserving their order so the resulting set is
     * tried in the order the caller listed them.
     *
     * @param addresses the addresses to parse; must not be empty and must not contain a blank entry
     * @return the parsed endpoints
     * @throws IllegalArgumentException if the collection is null/empty, or any entry is null, blank or
     *     not a valid {@code host:port}
     */
    static Set<HostAndPort> parseAddresses(@Nullable Collection<String> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            throw new IllegalArgumentException("at least one sentinel address is required");
        }
        Set<HostAndPort> parsed = new LinkedHashSet<>();
        for (String address : addresses) {
            parsed.add(parseAddress(address));
        }
        return parsed;
    }

    /**
     * Parses a single {@code host:port} Sentinel address. Delegates the split to Jedis' own {@link
     * HostAndPort#from} so bracketed IPv6 literals such as {@code [::1]:26379} are handled the same
     * way Jedis handles them everywhere else, but reports failures as {@link IllegalArgumentException}
     * naming the offending value, since this is user-supplied configuration.
     *
     * @param address the address to parse
     * @return the parsed endpoint
     * @throws IllegalArgumentException if the address is null, blank, or not a valid {@code host:port}
     */
    static HostAndPort parseAddress(@Nullable String address) {
        if (address == null || address.trim().isEmpty()) {
            throw new IllegalArgumentException("sentinel address must not be null or blank");
        }
        String trimmed = address.trim();
        HostAndPort parsed;
        try {
            parsed = HostAndPort.from(trimmed);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                    "sentinel address must be in host:port form, but was \"" + trimmed + "\"", e);
        }
        if (parsed.getHost() == null || parsed.getHost().trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "sentinel address must be in host:port form, but was \"" + trimmed + "\"");
        }
        if (parsed.getPort() < 1 || parsed.getPort() > 65535) {
            throw new IllegalArgumentException(
                    "sentinel port must be in [1, 65535], but was " + parsed.getPort() + " in \"" + trimmed + "\"");
        }
        return parsed;
    }
}
