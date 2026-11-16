package com.falkordb;

import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * The reply to a command queued in a {@link GraphContext.Pipeline} or a {@link GraphContext.Transaction}. It can be
 * read once the batch has read it off the connection: after the pipeline's {@code sync()}, {@code syncAndReturnAll()}
 * or {@code close()}, or after the transaction's {@code exec()}. A pipeline whose context was closed first can be
 * read too, since closing the context reads what the pipeline awaited.
 *
 * <p>A graph reply is decoded the first time it is read, which can look up a label, property key or relationship
 * type the client hasn't seen yet. Until the context is closed, read a reply on the thread that uses the context,
 * since that lookup can run on the context's connection; after that, on one thread at a time. A reply is not safe
 * for concurrent use.
 *
 * @param <T> the decoded reply
 */
public interface Reply<T extends @Nullable Object> extends Supplier<T> {

    /**
     * Returns the decoded reply. Every call returns the same value, or throws the same exception.
     *
     * @return the decoded reply; {@code null} only where the server's reply to a {@code sendCommand} or
     *     {@code sendBinaryCommand} is nil, such as a {@code GET} of a missing key
     * @throws IllegalStateException if the reply can't be read: the pipeline hasn't read it yet, or its last read
     *     failed; the transaction hasn't run yet, or it was discarded, aborted because a key it watched changed,
     *     refused by the server, or {@code exec()} failed; or the context was closed before the reply was read
     * @throws com.falkordb.exceptions.GraphException if the server answered the command with an error, or the
     *     reply can't be decoded
     * @throws RuntimeException what else decoding the reply threw, such as the failure of a lookup it needed
     */
    @Override
    T get();
}
