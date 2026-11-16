package com.falkordb.impl.api;

import com.falkordb.exceptions.FalkorDBConnectionException;
import com.falkordb.exceptions.GraphException;
import java.util.function.Supplier;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisDataException;

/**
 * Turns the Jedis exceptions that escape a public {@code Driver}, {@code Graph} or {@code GraphContext} method
 * into the client's own types, at that boundary and nowhere below it: the cluster code in {@link
 * ClusterConnections} sees Jedis' exceptions as they are.
 *
 * <ul>
 *   <li>a {@link JedisConnectionException} becomes a {@link FalkorDBConnectionException};
 *   <li>a {@link JedisDataException}, an error reply the server sent, becomes a {@link GraphException}.
 * </ul>
 *
 * <p>Only those exact classes are mapped. Each new exception is a subclass of the one it replaces, so every
 * {@code catch} that matched before still matches. A subclass, such as {@code JedisAccessControlException} for
 * {@code NOPERM}, is left alone, since a {@code catch} for it would stop matching. So is anything else, a
 * {@link GraphException} or a {@code JedisClusterOperationException} among them.
 *
 * <p>The new exception has the original's message, cause, suppressed exceptions and stack trace, so it differs
 * from it in class alone. The original is dropped: being of the exact class, it carries nothing else.
 */
final class ExceptionMapping {

    private ExceptionMapping() {}

    /**
     * The exception to throw in place of {@code failure}.
     *
     * @param failure what a command threw
     * @return the mapped exception, or {@code failure} itself when it isn't mapped
     */
    static RuntimeException map(RuntimeException failure) {
        Class<?> type = failure.getClass();
        if (type == JedisConnectionException.class) {
            Throwable cause = failure.getCause();
            return copyInto(
                    failure,
                    cause == null
                            ? new FalkorDBConnectionException(failure.getMessage())
                            : new FalkorDBConnectionException(failure.getMessage(), cause));
        }
        if (type == JedisDataException.class) {
            Throwable cause = failure.getCause();
            return copyInto(
                    failure,
                    cause == null
                            ? new GraphException(failure.getMessage())
                            : new GraphException(failure.getMessage(), cause));
        }
        return failure;
    }

    /**
     * Runs {@code command}, mapping what it throws with {@link #map}.
     *
     * @param command the work of one public method
     * @param <T> the result type
     * @return the command's result
     */
    static <T> T mapped(Supplier<T> command) {
        try {
            return command.get();
        } catch (RuntimeException e) {
            throw map(e);
        }
    }

    /**
     * Whether {@code failure} means the connection failed: a Jedis connection exception or the client's own. Code
     * that reacts to a connection failure asks this rather than testing for Jedis' type, so it keeps working
     * whatever the client's own type extends.
     *
     * @param failure what a command threw
     * @return {@code true} for a connection failure
     */
    static boolean isConnectionFailure(Throwable failure) {
        return failure instanceof JedisConnectionException || failure instanceof FalkorDBConnectionException;
    }

    private static <E extends RuntimeException> E copyInto(RuntimeException original, E copy) {
        copy.setStackTrace(original.getStackTrace());
        for (Throwable suppressed : original.getSuppressed()) {
            copy.addSuppressed(suppressed);
        }
        return copy;
    }
}
