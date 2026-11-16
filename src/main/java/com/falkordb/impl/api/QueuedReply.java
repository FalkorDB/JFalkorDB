package com.falkordb.impl.api;

import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import redis.clients.jedis.Builder;
import redis.clients.jedis.Response;

/**
 * The caller's view of a graph reply queued in a pipeline or a transaction: Jedis' response, except that a reply
 * that fails to decode fails every {@link #get()} with the same exception.
 *
 * <p>Jedis builds a response once, and forgets an exception its builder threw: every later {@code get()} returns
 * {@code null}. {@code syncAndReturnAll()} and {@code exec()} make that first {@code get()} themselves, so the
 * caller would never see the error. This response's builder keeps what decoding threw, and throws it on to Jedis
 * inside a {@link QueuedReplyFailure}, the only kind of exception Jedis' batch loops keep in place of a reply.
 *
 * <p>Only a {@code RuntimeException} is kept; an {@code Error} propagates as before. A Jedis {@code Response} given
 * this one with {@code setDependency} doesn't decode it first: Jedis reads the dependency's private state, and only
 * the response Jedis queued is set. Jedis itself never sets a dependency, and the reply is still decoded, and its
 * failure thrown, by this response's own {@link #get()}.
 *
 * @param <T> the decoded reply
 */
final class QueuedReply<T> extends Response<T> {

    private final Response<T> queued;
    private final Decoder<T> builder;

    private QueuedReply(Response<T> queued, Decoder<T> builder) {
        super(builder);
        this.queued = queued;
        this.builder = builder;
    }

    /**
     * Queues a graph command and returns the response to hand to the caller.
     *
     * @param decode decodes the raw reply; whatever it throws is kept for every {@link #get()}
     * @param append queues the command with the given builder and returns Jedis' response to it
     * @param <T> the decoded reply
     * @return the response to hand to the caller
     */
    static <T> QueuedReply<T> queue(Function<Object, T> decode, Function<Builder<T>, Response<T>> append) {
        Decoder<T> builder = new Decoder<>(decode);
        return new QueuedReply<>(append.apply(builder), builder);
    }

    /**
     * Returns Jedis' own response, which throws a {@link QueuedReplyFailure} the first time it is read if the reply
     * can't be decoded, and a {@link redis.clients.jedis.exceptions.JedisDataException} on every read if the
     * server rejected the command.
     */
    Response<T> queued() {
        return queued;
    }

    /**
     * Returns the decoded reply, or throws why there is none, on every call.
     *
     * @return the decoded reply; null for a nil reply
     * @throws RuntimeException what decoding the reply threw, such as a
     *     {@link com.falkordb.exceptions.GraphException}, or the failure of a lookup decoding needed
     * @throws redis.clients.jedis.exceptions.JedisDataException if the server rejected the command
     * @throws IllegalStateException if the pipeline or transaction has not been synced or executed yet
     */
    @Override
    public T get() {
        T reply;
        try {
            // throws the carrier the first time it builds a reply that can't be decoded, and returns null after that
            reply = queued.get();
        } catch (QueuedReplyFailure carried) {
            throw carried.failure();
        }
        RuntimeException failure = builder.failure;
        if (failure != null) {
            throw failure;
        }
        return reply;
    }

    @Override
    public void set(Object data) {
        queued.set(data);
    }

    @Override
    public void setDependency(Response<?> dependency) {
        queued.setDependency(dependency);
    }

    /** Decodes a reply, and keeps what decoding threw. */
    private static final class Decoder<T> extends Builder<T> {

        private final Function<Object, T> decode;
        private @Nullable RuntimeException failure;

        Decoder(Function<Object, T> decode) {
            this.decode = decode;
        }

        /**
         * Decodes the reply.
         *
         * @param reply the raw reply
         * @return the decoded reply
         * @throws QueuedReplyFailure carrying what decoding threw, which is also kept for {@link QueuedReply#get()}
         */
        @Override
        public T build(Object reply) {
            try {
                return decode.apply(reply);
            } catch (RuntimeException e) {
                failure = e;
                throw new QueuedReplyFailure(e);
            }
        }
    }
}
