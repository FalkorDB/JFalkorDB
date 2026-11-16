package com.falkordb.impl.api;

import com.falkordb.GraphContext;
import com.falkordb.Reply;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;
import redis.clients.jedis.BuilderFactory;
import redis.clients.jedis.Protocol;

/** {@link GraphContext.Pipeline} over a {@link GraphPipelineImpl}, which does the work. */
final class ContextPipeline extends ContextBatch<GraphPipelineImpl> implements GraphContext.Pipeline {

    /** Set by {@link #close()}, after which the pipeline can't be used. */
    private boolean closed;

    ContextPipeline(GraphPipelineImpl pipeline, GraphContextImpl context) {
        super(pipeline, pipeline.replies(), context);
    }

    @Override
    boolean transaction() {
        return false;
    }

    /**
     * Throws unless the pipeline can still be used: it must not be closed, and its last read must not have failed.
     * Jedis may then have read every reply and still hold responses it didn't build, so the replies on the connection
     * are unknown.
     */
    private void checkUsable() {
        if (closed) {
            throw new IllegalStateException("the pipeline is closed");
        }
        if (batch.readFailed()) {
            throw new IllegalStateException(
                    "a read of the pipeline's replies failed, so it can't be used any more", batch.unreadCause());
        }
    }

    @Override
    void checkQueuing() {
        checkUsable();
        checkConnection();
    }

    @Override
    String unread() {
        return contextClosed() && !batch.readFailed()
                ? "the context was closed before this reply was read"
                : batch.unread();
    }

    @Override
    public Reply<String> copyGraph(String destinationGraphId) {
        return queue(() -> batch.copyGraph(destinationGraphId));
    }

    @Override
    public Reply<Long> waitReplicas(int replicas, long timeout) {
        // Jedis' own waitReplicas() wouldn't be counted by appendWithResponse.
        List<Object> args = Arrays.asList(replicas, timeout);
        return queue(() -> QueuedReply.queue(
                BuilderFactory.LONG::build, builder -> batch.appendWithResponse(Protocol.Command.WAIT, args, builder)));
    }

    @Override
    public void sync() {
        if (readable()) {
            readReplies();
        }
        dropPending();
    }

    @Override
    public List<@Nullable Object> syncAndReturnAll() {
        if (readable()) {
            readReplies();
        }
        return readPending();
    }

    /**
     * Whether the replies the pipeline awaits are to be read now. Once the context is closed, its close read them
     * already, or they can't be read any more.
     *
     * @return false if the context is closed and has read them
     * @throws IllegalStateException if the pipeline can't be used, or the context was closed before reading them
     */
    private boolean readable() {
        checkUsable();
        if (!contextClosed()) {
            return true;
        }
        if (batch.hasPipelinedResponse()) {
            throw new IllegalStateException(
                    "the context was closed before the pipeline's replies were read, so they can't be read any more");
        }
        return false;
    }

    /** Reads the replies on the connection, which must not be another batch's turn. */
    private void readReplies() {
        if (batch.hasPipelinedResponse()) {
            checkConnection();
        }
        try {
            batch.sync();
        } catch (RuntimeException | Error e) {
            dropPending(); // they will never be read
            throw e;
        }
    }

    /**
     * Reads the replies the pipeline awaits, as {@link #sync()} does, and closes the pipeline. Nothing to read once
     * the context is closed, which read them, and nothing to do the second time. After a failed read, the connection
     * is marked broken instead, so the pool destroys it.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            if (contextClosed()) {
                return;
            }
            if (!batch.readFailed() && batch.hasPipelinedResponse()) {
                checkConnection();
            }
            batch.close();
        } finally {
            dropPending(); // a reply the caller keeps holds the pipeline, which mustn't hold every other reply
        }
    }
}
