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
     * Queues an explain in a pipeline or a transaction and returns the response that reads its plan.
     *
     * <p>Jedis builds a response once, and forgets an exception its builder threw: every later {@code get()}
     * returns {@code null}. {@code syncAndReturnAll()} and {@code exec()} make that first {@code get()}
     * themselves, so the caller would never see the error. The returned response keeps the
     * {@link GraphException} for a plan that holds an error, and every {@code get()} throws it.
     *
     * @param append queues the command with the given builder and returns Jedis' response to it
     * @return the response to hand to the caller
     */
    static Response<List<String>> queue(Function<Builder<List<String>>, Response<List<String>>> append) {
        FailureKeepingBuilder builder = new FailureKeepingBuilder();
        return new QueuedPlan(append.apply(builder), builder);
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

    /** Parses the plan with {@link #lines(Object)}, and remembers the {@link GraphException} it throws. */
    private static final class FailureKeepingBuilder extends Builder<List<String>> {

        private @Nullable GraphException failure;

        /**
         * Parses the plan.
         *
         * @param reply the raw reply
         * @return the lines, in order
         * @throws GraphException if the array holds an error; it is also kept for {@link QueuedPlan#get()}
         */
        @Override
        public List<String> build(Object reply) {
            try {
                return lines(reply);
            } catch (GraphException e) {
                failure = e;
                throw e;
            }
        }
    }

    /** The caller's view of a queued explain: Jedis' response, but a failed plan fails every {@code get()}. */
    private static final class QueuedPlan extends Response<List<String>> {

        private final Response<List<String>> queued;
        private final FailureKeepingBuilder builder;

        QueuedPlan(Response<List<String>> queued, FailureKeepingBuilder builder) {
            super(builder);
            this.queued = queued;
            this.builder = builder;
        }

        /**
         * Returns the plan, or throws why it could not be read, on every call.
         *
         * @return the plan's lines
         * @throws GraphException if the plan holds an error
         * @throws redis.clients.jedis.exceptions.JedisDataException if the server rejected the command
         * @throws IllegalStateException if the pipeline or transaction has not been synced or executed yet
         */
        @Override
        public List<String> get() {
            // Jedis' response throws the failure the first time it builds the plan, and returns null after that
            List<String> plan = queued.get();
            GraphException failure = builder.failure;
            if (failure != null) {
                throw failure;
            }
            return plan;
        }

        @Override
        public void set(Object data) {
            queued.set(data);
        }

        @Override
        public void setDependency(Response<?> dependency) {
            queued.setDependency(dependency);
        }
    }
}
