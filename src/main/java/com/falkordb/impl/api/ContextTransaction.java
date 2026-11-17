package com.falkordb.impl.api;

import com.falkordb.GraphContext;
import com.falkordb.exceptions.GraphException;
import java.util.List;
import org.jspecify.annotations.Nullable;
import redis.clients.jedis.exceptions.JedisDataException;

/** {@link GraphContext.Transaction} over a {@link GraphTransactionImpl}, which does the work. */
final class ContextTransaction extends ContextBatch<GraphTransactionImpl> implements GraphContext.Transaction {

    ContextTransaction(GraphTransactionImpl transaction, GraphContextImpl context) {
        super(transaction, transaction.replies(), context);
    }

    @Override
    boolean transaction() {
        return true;
    }

    /**
     * Throws unless a command can be queued: the context's connection must be free, and the transaction must not
     * have ended. Once it has, Jedis would run a command at once, outside the transaction.
     */
    @Override
    void checkQueuing() {
        checkConnection();
        checkOpen();
    }

    private void checkOpen() {
        if (!batch.queuing()) {
            throw new IllegalStateException("the transaction has ended: " + batch.unread(), unreadCause());
        }
    }

    @Override
    String unread() {
        return contextClosed() && batch.queuing()
                ? "the context was closed before the transaction ran"
                : batch.unread();
    }

    @Override
    public @Nullable List<@Nullable Object> exec() {
        checkQueuing();
        List<Object> results;
        try {
            results = batch.exec();
        } catch (RuntimeException e) {
            dropPending();
            throw asGraphException(failed(e));
        } catch (Error e) {
            dropPending();
            throw e;
        }
        if (results == null) {
            dropPending();
            return null;
        }
        return readPending();
    }

    @Override
    public void discard() {
        checkQueuing();
        try {
            batch.discard();
        } catch (RuntimeException e) {
            throw asGraphException(failed(e));
        } finally {
            dropPending();
        }
    }

    /**
     * Returns a server error as a {@link GraphException}: {@link ExceptionMapping} makes a plain
     * {@code JedisDataException} one, and leaves a subclass, such as a redirect, which becomes the cause of one here.
     */
    private static RuntimeException asGraphException(RuntimeException e) {
        return e instanceof JedisDataException && !(e instanceof GraphException)
                ? new GraphException(e.getMessage(), e)
                : e;
    }

    /** Discards the transaction unless it has ended; nothing to do once the context is closed, which discarded it. */
    @Override
    public void close() {
        if (!contextClosed() && batch.queuing()) {
            discard();
        }
    }
}
