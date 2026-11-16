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
 *
 * <p>Any other plain error reply that escapes a {@link com.falkordb.Driver}, {@code Graph} or {@code
 * GraphContext} method is this exception as well: one a {@code Driver} method gets, such as {@code configGet}
 * of an unknown parameter or {@code udfDelete} of a missing library, one {@code watch} or {@code unwatch} gets,
 * and one the server sends while a connection is being opened, such as a rejected {@code AUTH}. Earlier
 * versions threw these as a plain {@link JedisDataException}, which this class extends. Unlike the graph
 * commands above, the exception then has the server's message as its own and the same cause the Jedis
 * exception had, which is none. An error Jedis has a subclass for, such as {@code
 * JedisAccessControlException}, is thrown as that subclass there.
 *
 * <p>Catch this type rather than {@code JedisDataException}: a later release roots the client's exceptions in
 * a type of its own instead of Jedis', and code that catches this one keeps working. A connection that fails
 * is reported as {@link FalkorDBConnectionException} instead.
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
