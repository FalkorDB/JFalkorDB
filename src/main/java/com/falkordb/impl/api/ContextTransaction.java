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
            throw new IllegalStateException("the transaction has ended: " + batch.unread(), batch.unreadCause());
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
        } catch (JedisDataException e) {
            dropPending();
            throw e instanceof GraphException ? e : new GraphException(e.getMessage(), e);
        } catch (RuntimeException | Error e) {
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
        } catch (JedisDataException e) {
            throw e instanceof GraphException ? e : new GraphException(e.getMessage(), e);
        } finally {
            dropPending();
        }
    }

    /** Discards the transaction unless it has ended; nothing to do once the context is closed, which discarded it. */
    @Override
    public void close() {
        if (!contextClosed() && batch.queuing()) {
            discard();
        }
    }
}
