package com.falkordb.impl.api;

import java.util.List;
import org.jspecify.annotations.Nullable;
import redis.clients.jedis.exceptions.JedisDataException;

/**
 * Carries the exception that decoding a queued graph reply threw through Jedis' batch loops, which never hand it
 * to a caller as it is.
 *
 * <p>{@code syncAndReturnAll()} and {@code exec()} build each reply inside a loop that catches only
 * {@link JedisDataException}, and puts what it catches in place of the reply. Anything else ends the loop part
 * way: the caller loses every reply, and a pipeline keeps the responses it hadn't reached queued although their
 * replies were read off the connection, so its next {@code sync()} waits for replies that never come. A lookup
 * that decoding needs can fail with any exception: a pool that has no connection to lend, or a connection that
 * fails. So a graph reply's builder ({@link QueuedReply}) throws this instead, and the client takes the original
 * back out before a caller sees it: {@link QueuedReply#get()} throws it, and {@link #unwrap(List)} puts it in the
 * returned list.
 */
final class QueuedReplyFailure extends JedisDataException {
    private static final long serialVersionUID = 1L;

    /** What decoding the reply threw. */
    private final RuntimeException failure;

    QueuedReplyFailure(RuntimeException failure) {
        super(failure.getMessage(), failure);
        this.failure = failure;
    }

    /** Returns what decoding the reply threw. */
    RuntimeException failure() {
        return failure;
    }

    /**
     * Replaces each carrier in the replies {@code syncAndReturnAll()} or {@code exec()} returned with the exception
     * it carries.
     *
     * @param replies the replies, changed in place; null after an aborted transaction
     */
    static void unwrap(@Nullable List<Object> replies) {
        if (replies == null) {
            return;
        }
        for (int i = 0; i < replies.size(); i++) {
            Object reply = replies.get(i);
            if (reply instanceof QueuedReplyFailure) {
                replies.set(i, ((QueuedReplyFailure) reply).failure);
            }
        }
    }
}
