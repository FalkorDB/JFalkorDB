package com.falkordb.exceptions;

import org.jspecify.annotations.Nullable;
import redis.clients.jedis.exceptions.JedisConnectionException;

/**
 * The client could not talk to the server: a connection could not be opened, or it broke or timed out while
 * a command was sent or its reply read. With an explicitly configured Redis Sentinel, it is also what you get
 * when no Sentinel can be reached.
 *
 * <p>A {@link com.falkordb.Driver}, {@link com.falkordb.Graph} or {@link com.falkordb.GraphContext} method
 * throws this exception where earlier versions threw Jedis' {@link JedisConnectionException}, which this
 * class extends, so {@code catch (JedisConnectionException e)} still catches it. Its message, cause,
 * suppressed exceptions and stack trace are those of the exception Jedis raised: a read timeout, say, still
 * has the {@link java.net.SocketTimeoutException} as its cause.
 *
 * <p>Some failures to get a connection are still reported as Jedis raises them: a pool that stays exhausted
 * for the configured {@code poolMaxWait} throws a plain {@code JedisException} with the pool's {@link
 * java.util.NoSuchElementException} as its cause, and a Redis Cluster none of whose nodes can be reached
 * throws a {@code JedisClusterOperationException}.
 *
 * <p>When the connection broke after a command was sent, whether the command ran is unknown, so retrying a
 * write may repeat it.
 *
 * <p>Catch this type rather than {@code JedisConnectionException}: a later release roots the client's
 * exceptions in a type of its own instead of Jedis', and code that catches this one keeps working.
 */
public class FalkorDBConnectionException extends JedisConnectionException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates a FalkorDBConnectionException with the given error message.
     *
     * @param message the error message
     */
    public FalkorDBConnectionException(@Nullable String message) {
        super(message);
    }

    /**
     * Creates a FalkorDBConnectionException with the given cause.
     *
     * @param cause the cause of the exception
     */
    public FalkorDBConnectionException(@Nullable Throwable cause) {
        super(cause);
    }

    /**
     * Creates a FalkorDBConnectionException with the given error message and cause.
     *
     * @param message the error message
     * @param cause the cause of the exception
     */
    public FalkorDBConnectionException(@Nullable String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
