package com.falkordb;

import java.util.List;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.util.SafeEncoder;

/**
 * What a FalkorDB server's graph module version says it supports. Unlike {@link TestServer}, it starts nothing, so a
 * test against another server, such as {@link ClusterTestServer}'s, can ask it without starting a container.
 */
final class ServerVersion {

    /** The first graph module version that answers {@code SCHEMA_HASH}: 6.2.0. */
    static final int SCHEMA_HASH = 60200;

    private ServerVersion() {}

    /**
     * The graph module's version on the given server, as {@code MODULE LIST} reports it: {@code 42204} for v4.22.4,
     * {@code 60201} for 6.2.1.
     *
     * @param host the server's host
     * @param port the server's port
     * @return the version
     * @throws IllegalStateException if the server runs no graph module
     */
    static int moduleVersion(String host, int port) {
        try (Jedis jedis = new Jedis(host, port)) {
            for (Object module : (List<?>) jedis.sendCommand(Protocol.Command.MODULE, "LIST")) {
                List<?> fields = (List<?>) module;
                Object name = null;
                Object version = null;
                for (int i = 0; i + 1 < fields.size(); i += 2) {
                    String key = SafeEncoder.encode((byte[]) fields.get(i));
                    if (key.equals("name")) {
                        name = SafeEncoder.encode((byte[]) fields.get(i + 1));
                    } else if (key.equals("ver")) {
                        version = fields.get(i + 1);
                    }
                }
                if ("graph".equals(name) && version instanceof Long) {
                    return ((Long) version).intValue();
                }
            }
        }
        throw new IllegalStateException("the server at " + host + ":" + port + " runs no graph module");
    }

    /**
     * Whether the given server sends the schema with a reply to a query that carries {@code SCHEMA_HASH}.
     *
     * @param host the server's host
     * @param port the server's port
     * @return whether its graph module is 6.2.0 or later
     */
    static boolean sendsSchemaHash(String host, int port) {
        return moduleVersion(host, port) >= SCHEMA_HASH;
    }
}
