package com.falkordb.impl.api;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import redis.clients.jedis.HostAndPort;

/**
 * Resolved Redis Cluster settings for a driver, as gathered by {@code com.falkordb.FalkorDB}.
 *
 * <p>Internal wiring, public only because the {@code com.falkordb} factories live in another
 * package. Users configure these through {@code FalkorDB.builder()}.
 *
 * <p>A value is in exactly one of three states:
 *
 * <ul>
 *   <li>{@linkplain #explicit explicit} — the startup nodes are named up front, so no probing
 *       happens and several nodes can be listed.
 *   <li>{@linkplain #autoDetect() auto-detect} — the default: connect to whatever the caller gave,
 *       and switch to cluster mode only if that endpoint turns out to be a cluster node.
 *   <li>{@linkplain #disabled() disabled} — always connect directly, never probe.
 * </ul>
 */
public final class ClusterOptions {

    private final boolean autoDetect;
    private final Set<HostAndPort> startupNodes;

    private ClusterOptions(boolean autoDetect, Set<HostAndPort> startupNodes) {
        this.autoDetect = autoDetect;
        this.startupNodes = startupNodes;
    }

    /**
     * Probe the endpoint the caller asked for and use cluster mode only if it turns out to be a
     * cluster node. This is the default, and matches the Python client.
     *
     * @return auto-detecting options
     */
    public static ClusterOptions autoDetect() {
        return new ClusterOptions(true, Collections.<HostAndPort>emptySet());
    }

    /**
     * Never probe: connect directly to the endpoint the caller asked for.
     *
     * @return options with cluster support switched off
     */
    public static ClusterOptions disabled() {
        return new ClusterOptions(false, Collections.<HostAndPort>emptySet());
    }

    /**
     * Connect to the cluster through the given startup nodes, without probing.
     *
     * <p>The addresses are parsed here so a typo is reported by the call that contains it instead of
     * by the first query. Only some of the nodes need to be reachable: the full topology is learned
     * from the first one that answers.
     *
     * @param startupNodes the cluster nodes in {@code host:port} form; must not be empty
     * @return explicit cluster options
     * @throws IllegalArgumentException if {@code startupNodes} is null, empty, or contains an entry
     *     that is not a valid {@code host:port}
     */
    public static ClusterOptions explicit(@Nullable Collection<String> startupNodes) {
        return new ClusterOptions(false, Collections.unmodifiableSet(parseNodes(startupNodes)));
    }

    /**
     * Returns whether an unrecognised endpoint should be probed for cluster mode.
     *
     * @return whether an unrecognised endpoint should be probed for cluster mode
     */
    boolean isAutoDetect() {
        return autoDetect;
    }

    /**
     * Returns whether the startup nodes were configured explicitly.
     *
     * @return whether the startup nodes were configured explicitly
     */
    boolean isExplicit() {
        return !startupNodes.isEmpty();
    }

    /**
     * Returns the parsed startup nodes.
     *
     * @return the startup nodes, empty when not {@linkplain #isExplicit explicit}
     */
    Set<HostAndPort> startupNodes() {
        return startupNodes;
    }

    /**
     * Parses {@code host:port} addresses, preserving order and dropping duplicates.
     *
     * @param addresses the addresses to parse
     * @return the parsed, de-duplicated nodes
     * @throws IllegalArgumentException if {@code addresses} is null or empty, or an entry is not a valid
     *     {@code host:port}
     */
    static Set<HostAndPort> parseNodes(@Nullable Collection<String> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            throw new IllegalArgumentException("at least one cluster node must be given");
        }
        Set<HostAndPort> nodes = new LinkedHashSet<>();
        for (String address : addresses) {
            nodes.add(Clusters.parseNode(address));
        }
        return nodes;
    }
}
