package com.falkordb.impl.api;
import redis.clients.jedis.util.SafeEncoder;
import redis.clients.jedis.commands.ProtocolCommand;

/**
 * An enum which aligned to FalkorDB Graph commands
 */
public enum GraphCommand implements ProtocolCommand {
    QUERY("graph.QUERY"),
    RO_QUERY("graph.RO_QUERY"),
    PROFILE("graph.PROFILE"),
    COPY("graph.COPY"),
    DELETE("graph.DELETE"),
    EXPLAIN("graph.EXPLAIN"),
    LIST("graph.LIST");

    private final byte[] raw;

    /**
     * Generates a new instance with a specific command
     * @param alt command
     */
    GraphCommand(String alt) {
        raw = SafeEncoder.encode(alt);
    }

    /**
     * Returns the raw command
     */
    @Override
    public byte[] getRaw() {
        return raw;
    }
}
