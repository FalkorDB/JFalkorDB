package com.falkordb.impl.api;
import redis.clients.jedis.util.SafeEncoder;
import redis.clients.jedis.commands.ProtocolCommand;

/**
 * An enum which aligned to FalkorDB Graph commands
 */
public enum GraphCommand implements ProtocolCommand {
    /**
     * Represents the graph.QUERY command.
     */
    QUERY("graph.QUERY"),
    /**
     * Represents the graph.RO_QUERY command.
     */
    RO_QUERY("graph.RO_QUERY"),
    /**
     * Represents the graph.COPY command.
     */
    COPY("graph.COPY"),
    /**
     * Represents the graph.DELETE command.
     */
    DELETE("graph.DELETE"),
  
    /**
     * Represents the graph.EXPLAIN command.
     */
    EXPLAIN("graph.EXPLAIN"),
    /**
     * Represents the graph.LIST command.
     */
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