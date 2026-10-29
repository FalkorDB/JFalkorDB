package com.falkordb.exceptions;

import org.jspecify.annotations.Nullable;
import redis.clients.jedis.exceptions.JedisDataException;

/**
 * Graph query evaluation exception. An instance of GraphException is
 * thrown when Graph encounters an error during query evaluation.
 *
 * <p>A {@link com.falkordb.Graph} or {@link com.falkordb.GraphContext} command that the server rejects —
 * a query, a procedure call, {@code profile}, {@code explain}, {@code copyGraph} or {@code deleteGraph} —
 * throws this exception, with the {@link JedisDataException} Jedis raised for the error reply as its cause.
 * Jedis raises a subclass for some errors, such as {@code JedisAccessControlException} for {@code NOPERM},
 * so look at {@link #getCause()} to tell them apart. An error the server reports inside a reply, such as a
 * query that fails while it runs, and some replies the client can't decode, such as a malformed vector, are
 * reported as this exception too.
 */
public class GraphException extends JedisDataException {
    private static final long serialVersionUID = -476099681322055468L;

    /**
     * Creates a GraphException with the given error message.
     *
     * @param message the error message
     */
    public GraphException(@Nullable String message) {
        super(message);
    }

    /**
     * Creates a GraphException with the given cause.
     *
     * @param cause the cause of the exception
     */
    public GraphException(@Nullable Throwable cause) {
        super(cause);
    }

    /**
     * Creates a GraphException with the given error message and cause.
     *
     * @param message the error message
     * @param cause the cause of the exception
     */
    public GraphException(@Nullable String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
