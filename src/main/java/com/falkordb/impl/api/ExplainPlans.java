package com.falkordb.impl.api;

import com.falkordb.exceptions.GraphException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import redis.clients.jedis.Builder;
import redis.clients.jedis.Response;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.util.SafeEncoder;

/**
 * Reads the reply to {@code GRAPH.EXPLAIN}: an array with one bulk string per line of the execution plan.
 * One parser for every way a graph is explained — directly, in a context, in a pipeline and in a
 * transaction.
 */
final class ExplainPlans {

    private ExplainPlans() {}

    /**
     * Queues an explain in a pipeline or a transaction and returns the response that reads its plan. A plan that
     * holds an error fails every {@code get()} with the same {@link GraphException} (see {@link QueuedReply}).
     *
     * @param append queues the command with the given builder and returns Jedis' response to it
     * @return the response to hand to the caller
     */
    static Response<List<String>> queue(Function<Builder<List<String>>, Response<List<String>>> append) {
        return QueuedReply.queue(ExplainPlans::lines, append);
    }

    /**
     * Returns the plan's lines. The plan is text for a person to read, so an unexpected shape is still
     * returned rather than rejected: a bulk string on its own is one line, a missing reply is no lines, and
     * a line that isn't a bulk string is converted with {@link String#valueOf(Object)}. An error inside the
     * array is not a line, though: it is thrown.
     *
     * @param reply the raw reply
     * @return the lines, in order
     * @throws GraphException if the array holds an error, which becomes the exception's cause
     */
    static List<String> lines(@Nullable Object reply) {
        List<String> lines = new ArrayList<>();
        if (reply instanceof List) {
            for (Object item : (List<?>) reply) {
                if (item instanceof JedisDataException) {
                    throw new GraphException((JedisDataException) item);
                }
                lines.add(line(item));
            }
        } else if (reply != null) {
            lines.add(line(reply));
        }
        return lines;
    }

    /** Decodes a bulk string as UTF-8; anything else is converted with {@link String#valueOf(Object)}. */
    private static String line(@Nullable Object item) {
        return item instanceof byte[] ? SafeEncoder.encode((byte[]) item) : String.valueOf(item);
    }
}
