package com.falkordb.impl.api;

import com.falkordb.Reply;
import com.falkordb.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import redis.clients.jedis.Response;
import redis.clients.jedis.commands.ProtocolCommand;
import redis.clients.jedis.util.SafeEncoder;

/**
 * What {@link ContextPipeline} and {@link ContextTransaction} share: the graph commands and {@code sendCommand},
 * each returning a {@link Reply} over the Jedis response of the {@link GraphBatch} underneath. That batch is the
 * one implementation of pipelines and transactions: the deprecated {@code GraphPipeline} and
 * {@code GraphTransaction} are the same classes, and the context that created it ends it when it closes.
 *
 * <p>Only this class queues commands on its batch, so the replies it queued since the batch last read them are, in
 * order, the ones the batch reads next: {@link #pending} holds them, and the list a read returns is built from them,
 * each element what its reply's {@link Reply#get()} returns or throws.
 *
 * <p>A batch is meant for the thread that uses its context, like the context itself.
 *
 * @param <B> the batch underneath
 */
abstract class ContextBatch<B extends GraphBatch> {

    /** The batch underneath, which queues the commands and reads their replies. */
    final B batch;
    /** How the batch decodes its replies, through which the context sees whether it awaits any. */
    private final QueuedReplies replies;
    /** The context that created the batch. */
    private final GraphContextImpl context;
    /** The replies queued since the batch last read them, oldest first. */
    private final List<ReplyImpl<?>> pending = new ArrayList<>();

    ContextBatch(B batch, QueuedReplies replies, GraphContextImpl context) {
        this.batch = batch;
        this.replies = replies;
        this.context = context;
    }

    /** Whether the context that created this batch is closed. */
    final boolean contextClosed() {
        return context.isClosed();
    }

    /**
     * Throws unless the batch can use the context's connection now: the context must be open, and no other pipeline
     * or transaction on it may await replies, which a command sent now would be taken for.
     *
     * @throws IllegalStateException if the context is closed, so the connection may be another borrower's already,
     *     or another batch awaits replies
     */
    final void checkConnection() {
        if (contextClosed()) {
            throw new IllegalStateException("the context is closed");
        }
        context.checkConnectionFree(replies);
    }

    /**
     * Throws unless a command can be queued now: {@link #checkConnection()}, and whatever else the kind of batch
     * requires.
     *
     * @throws IllegalStateException if a command can't be queued now
     */
    abstract void checkQueuing();

    /** Why a reply that hasn't been read can't be read now. */
    abstract String unread();

    /** The exception that made the batch fail, which is why a reply can't be read; null if none did. */
    final @Nullable Throwable unreadCause() {
        return batch.unreadCause();
    }

    /** How many of the commands queued have had their replies read. */
    final long read() {
        return batch.read();
    }

    /**
     * Builds the list a read returns from the replies it read, then forgets them: they are no longer pending.
     *
     * @return what each reply's {@link Reply#get()} returns, or the exception it throws, in order; unmodifiable
     */
    final List<@Nullable Object> readPending() {
        List<@Nullable Object> results = new ArrayList<>(pending.size());
        for (ReplyImpl<?> reply : pending) {
            results.add(reply.element());
        }
        pending.clear();
        return Collections.unmodifiableList(results);
    }

    /** Forgets the pending replies, which the batch will never read. */
    final void dropPending() {
        pending.clear();
    }

    /** Whether replies have been queued since the batch last read them. */
    final boolean hasPending() {
        return !pending.isEmpty();
    }

    /** Queues a command with the batch, after checking it can be, and returns the reply to it. */
    final <T extends @Nullable Object> Reply<T> queue(Supplier<Response<T>> append, boolean nullable) {
        checkQueuing();
        long position = batch.appended();
        Response<T> response = append.get();
        ReplyImpl<T> reply = new ReplyImpl<>(this, response, position, nullable);
        pending.add(reply);
        return reply;
    }

    final <T> Reply<T> queue(Supplier<Response<T>> append) {
        return queue(append, false);
    }

    public Reply<ResultSet> query(String query) {
        return queue(() -> batch.query(query));
    }

    public Reply<ResultSet> query(String query, long timeout) {
        return queue(() -> batch.query(query, timeout));
    }

    public Reply<ResultSet> query(String query, Map<String, @Nullable Object> params) {
        return queue(() -> batch.query(query, params));
    }

    public Reply<ResultSet> query(String query, Map<String, @Nullable Object> params, long timeout) {
        return queue(() -> batch.query(query, params, timeout));
    }

    public Reply<ResultSet> readOnlyQuery(String query) {
        return queue(() -> batch.readOnlyQuery(query));
    }

    public Reply<ResultSet> readOnlyQuery(String query, long timeout) {
        return queue(() -> batch.readOnlyQuery(query, timeout));
    }

    public Reply<ResultSet> readOnlyQuery(String query, Map<String, @Nullable Object> params) {
        return queue(() -> batch.readOnlyQuery(query, params));
    }

    public Reply<ResultSet> readOnlyQuery(String query, Map<String, @Nullable Object> params, long timeout) {
        return queue(() -> batch.readOnlyQuery(query, params, timeout));
    }

    public Reply<ResultSet> callProcedure(String procedure) {
        return queue(() -> batch.callProcedure(procedure));
    }

    public Reply<ResultSet> callProcedure(String procedure, List<String> args) {
        return queue(() -> batch.callProcedure(procedure, args));
    }

    public Reply<ResultSet> callProcedure(String procedure, List<String> args, Map<String, List<String>> kwargs) {
        return queue(() -> batch.callProcedure(procedure, args, kwargs));
    }

    public Reply<ResultSet> profile(String query) {
        return queue(() -> batch.profile(query));
    }

    public Reply<ResultSet> profile(String query, Map<String, @Nullable Object> params) {
        return queue(() -> batch.profile(query, params));
    }

    public Reply<List<String>> explain(String query) {
        return queue(() -> batch.explain(query));
    }

    public Reply<List<String>> explain(String query, Map<String, @Nullable Object> params) {
        return queue(() -> batch.explain(query, params));
    }

    public Reply<String> deleteGraph() {
        return queue(batch::deleteGraph);
    }

    public Reply<@Nullable Object> sendCommand(String command, String... args) {
        return sendRaw(command, args, RawReplies::text);
    }

    public Reply<@Nullable Object> sendBinaryCommand(String command, byte[]... args) {
        return sendRaw(command, args, RawReplies::binary);
    }

    /** Whether this is a transaction, which words some refusals differently. */
    abstract boolean transaction();

    /**
     * Queues a command that isn't a graph command, after {@link CommandGuard} has let it through.
     *
     * @throws IllegalArgumentException if the command is refused, its name is null or empty, or an argument is null
     */
    private Reply<@Nullable Object> sendRaw(
            String command, Object @Nullable [] args, Function<@Nullable Object, @Nullable Object> decode) {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("command must not be null or empty");
        }
        if (args == null) {
            throw new IllegalArgumentException("args must not be null");
        }
        List<Object> arguments = Arrays.asList(args);
        for (Object argument : arguments) {
            if (argument == null) {
                throw new IllegalArgumentException("an argument of " + command + " is null");
            }
        }
        CommandGuard.check(command, arguments, batch.graphId(), transaction());
        ProtocolCommand protocolCommand = new RawCommand(command);
        return queue(
                () -> QueuedReply.queue(
                        decode, builder -> batch.appendWithResponse(protocolCommand, arguments, builder)),
                true);
    }

    /** A command named by the caller. */
    private static final class RawCommand implements ProtocolCommand {
        private final byte[] raw;

        RawCommand(String name) {
            this.raw = SafeEncoder.encode(name);
        }

        @Override
        public byte[] getRaw() {
            return raw;
        }
    }
}
