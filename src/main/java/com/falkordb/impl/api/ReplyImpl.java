package com.falkordb.impl.api;

import com.falkordb.Reply;
import com.falkordb.exceptions.GraphException;
import org.jspecify.annotations.Nullable;
import redis.clients.jedis.Response;
import redis.clients.jedis.exceptions.JedisDataException;

/**
 * A {@link Reply} over the Jedis {@link Response} of a command a {@link ContextBatch} queued, which is a
 * {@link QueuedReply}: a reply that fails to decode throws the same exception on every read.
 *
 * @param <T> the decoded reply
 */
final class ReplyImpl<T extends @Nullable Object> implements Reply<T> {

    private final ContextBatch<?> batch;
    private final Response<T> response;
    /** How many commands the batch had queued before this one: its position among them. */
    private final long position;
    /** Whether the reply may be nil, as a raw command's may; a graph reply never is. */
    private final boolean nullable;
    /** What the server's error reply became, the first time it was read, so every read throws the same one. */
    private @Nullable GraphException serverError;

    ReplyImpl(ContextBatch<?> batch, Response<T> response, long position, boolean nullable) {
        this.batch = batch;
        this.response = response;
        this.position = position;
        this.nullable = nullable;
    }

    /**
     * Returns the decoded reply once the batch has read it. A server error becomes the same {@link GraphException}
     * on every call; what decoding threw is thrown again on every call.
     */
    @Override
    public T get() {
        if (position >= batch.read()) {
            throw new IllegalStateException(batch.unread(), batch.unreadCause());
        }
        T reply;
        try {
            reply = response.get();
        } catch (JedisDataException e) {
            throw serverErrorOr(e);
        }
        if (reply == null && !nullable) {
            // Jedis builds a reply once: decoding threw an Error the first time, and the reply is lost.
            throw new IllegalStateException("the reply couldn't be decoded");
        }
        return reply;
    }

    /**
     * Returns what {@link #get()} returns, or the exception it throws, as the element of the list a read of the batch
     * returns.
     */
    @Nullable
    Object element() {
        try {
            return get();
        } catch (RuntimeException e) {
            return e;
        }
    }

    /**
     * Returns the {@link GraphException} a server error reply becomes, the same one every time. What decoding threw is
     * returned as it is, even if it is a {@link JedisDataException}.
     */
    private RuntimeException serverErrorOr(JedisDataException e) {
        if (e instanceof GraphException
                || (response instanceof QueuedReply && ((QueuedReply<?>) response).failedToDecode())) {
            return e;
        }
        GraphException error = serverError;
        if (error == null) {
            error = new GraphException(e.getMessage(), e);
            serverError = error;
        }
        return error;
    }
}
