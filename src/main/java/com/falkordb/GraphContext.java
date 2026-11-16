package com.falkordb;

import java.io.Closeable;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * An interface for a FalkorDB graph with context.
 *
 * <p>A context runs all its commands on one connection, borrowed from the driver's connection pool
 * when the context is created. The connection stays bound to the context until {@link #close()} is
 * called, even if a command fails: after the server rejects a command, such as a query that fails with
 * a {@link com.falkordb.exceptions.GraphException}, the context remains usable. Always close the
 * context, for example with try-with-resources, to return its connection to the pool:
 *
 * <pre>{@code
 * try (GraphContext context = driver.graph("social").getContext()) {
 *     context.query("CREATE (:person {name: 'roi'})");
 * }
 * }</pre>
 *
 * <p>Closing the context also ends what its transactions and pipelines left on the connection, so the next user
 * of the connection gets it clean: a transaction still open is discarded, and the replies a pipeline still awaits
 * are read, which waits for them to arrive. A {@link Reply}, or a deprecated {@code Response}, of a transaction
 * discarded this way can't be read. If more than one transaction or pipeline still awaits replies, or ending one
 * fails, the connection is closed instead of being returned to the pool, and a reply still waiting to be read can't
 * be read either. The same happens when a pipeline's last {@code sync()} or {@code syncAndReturnAll()} failed, and when a
 * transaction or pipeline the caller dropped was garbage-collected before the context closed. The context holds
 * its latest 128 transactions and pipelines, so only an older one can be collected; what it left on the connection
 * is then unknown, and the graph's schema cache is cleared too, in case it deleted the graph. Like every other call
 * on the context, close it on the thread that uses the context.
 *
 * <p>While a {@link #pipeline()} awaits replies, or a {@link #transaction()} is open, the context's connection is
 * theirs: the context's own commands, {@link #watch(String...)} and {@link #unwatch()} included, throw an
 * {@link IllegalStateException} rather than read one of their replies as their own, or be queued inside the
 * transaction. The deprecated {@link #pipelined()} and {@link #multi()} don't do that.
 */
public interface GraphContext extends Graph {

    /**
     * Returns a Redis transactional object, over the connection context, with graph API capabilities
     * @return Redis transactional object, over the connection context, with graph API capabilities
     * @throws IllegalStateException if the context is closed
     * @deprecated Use {@link #transaction()}. Its {@link Transaction} has the same graph methods, returns
     *     JFalkorDB's own {@link Reply} instead of Jedis' {@code Response}, and queues other Redis commands with
     *     {@link Transaction#sendCommand(String, String...)}. This method and {@link GraphTransaction} are removed in
     *     0.18.0.
     */
    @Deprecated
    GraphTransaction multi();

    /**
     * Returns a Redis pipeline object, over the connection context, with graph API capabilities
     * @return Redis pipeline object, over the connection context, with graph API capabilities
     * @throws IllegalStateException if the context is closed
     * @deprecated Use {@link #pipeline()}. Its {@link Pipeline} has the same graph methods, returns JFalkorDB's own
     *     {@link Reply} instead of Jedis' {@code Response}, and queues other Redis commands with
     *     {@link Pipeline#sendCommand(String, String...)}. This method and {@link GraphPipeline} are removed in
     *     0.18.0.
     */
    @Deprecated
    GraphPipeline pipelined();

    /**
     * Starts a transaction on this context's connection, and sends {@code MULTI}. To have it abort if a key changes
     * before it runs, {@link #watch(String...) watch} the key first.
     *
     * <p>The default implementation throws {@link UnsupportedOperationException}; the context the driver creates
     * implements it.
     *
     * @return the transaction
     * @throws IllegalStateException if the context is closed, or a pipeline or transaction on it awaits replies
     * @throws UnsupportedOperationException if this implementation doesn't support it
     */
    default Transaction transaction() {
        throw new UnsupportedOperationException(getClass().getName() + " doesn't implement transaction()");
    }

    /**
     * Starts a pipeline on this context's connection.
     *
     * <p>The default implementation throws {@link UnsupportedOperationException}; the context the driver creates
     * implements it.
     *
     * @return the pipeline
     * @throws IllegalStateException if the context is closed, or a pipeline or transaction on it awaits replies
     * @throws UnsupportedOperationException if this implementation doesn't support it
     */
    default Pipeline pipeline() {
        throw new UnsupportedOperationException(getClass().getName() + " doesn't implement pipeline()");
    }

    /**
     * Perform watch over given Redis keys (Graphs names)
     * @param keys Redis keys (Graphs names)
     * @return "OK"
     */
    String watch(String... keys);

    /**
     * Removes watch from all keys
     * @return "OK"
     */
    String unwatch();

    /**
     * A pipeline on a {@link GraphContext}'s connection: commands are sent as they are queued, and their replies are
     * read together, by {@link #sync()}, {@link #syncAndReturnAll()} or {@link #close()}. Each command returns a
     * {@link Reply}, which can be read once its reply has been read off the connection. Get one with
     * {@link GraphContext#pipeline()}:
     *
     * <pre>{@code
     * try (GraphContext context = graph.getContext()) {
     *     GraphContext.Pipeline pipeline = context.pipeline();
     *     pipeline.sendCommand("SET", "visits", "1");
     *     Reply<ResultSet> created = pipeline.query("CREATE (:Person {name: 'Alice'})");
     *     Reply<Object> visits = pipeline.sendCommand("INCR", "visits");
     *     pipeline.sync();
     *     System.out.println(created.get().getStatistics().nodesCreated() + " node, " + visits.get() + " visits");
     * }
     * }</pre>
     *
     * <p>A graph reply is decoded when it is read, as for the deprecated {@link GraphPipeline}, which describes how
     * a {@link #deleteGraph()} affects the replies queued before it. A reply that fails to decode throws the same
     * exception on every {@link Reply#get()}. The server's error replies are
     * {@link com.falkordb.exceptions.GraphException}s.
     *
     * <p>While the pipeline awaits replies, the context's connection is its own: the context runs none of its own
     * commands, and no other pipeline or transaction can be started or used on it, since what they sent would be
     * read as this pipeline's replies. Each throws an {@link IllegalStateException} instead, as does queuing a
     * command once the pipeline is closed, a read of it failed, or the context is closed.
     *
     * <p>Like its context, a pipeline is meant for one thread. Read what it queued before dropping it: closing the
     * context reads the replies a pipeline still awaits, which then can be read.
     */
    interface Pipeline extends Closeable {

        /**
         * Queues a Cypher query.
         * @param query Cypher query
         * @return the reply: the result set
         */
        Reply<ResultSet> query(String query);

        /**
         * Queues a Cypher read-only query.
         * @param query Cypher query
         * @return the reply: the result set
         */
        Reply<ResultSet> readOnlyQuery(String query);

        /**
         * Queues a Cypher query with a timeout.
         *
         * <p>The timeout is sent with the query and limits how long the server runs it; {@code 0} sets none, so the
         * server's default ({@code TIMEOUT_DEFAULT}) applies. It doesn't change how long the client waits for the
         * replies in {@link #sync()} or {@link #syncAndReturnAll()}, which a socket (read) timeout on the connection can still end: see
         * {@link Graph#query(String, long)}.
         *
         * @param query Cypher query
         * @param timeout timeout in milliseconds; not negative
         * @return the reply: the result set
         * @throws IllegalArgumentException if {@code timeout} is negative; nothing is queued then
         */
        Reply<ResultSet> query(String query, long timeout);

        /**
         * Queues a Cypher read-only query with a timeout, which works as for {@link #query(String, long)}.
         * @param query Cypher query
         * @param timeout timeout in milliseconds; not negative
         * @return the reply: the result set
         * @throws IllegalArgumentException if {@code timeout} is negative; nothing is queued then
         */
        Reply<ResultSet> readOnlyQuery(String query, long timeout);

        /**
         * Queues a Cypher query with parameters, which are encoded as for {@link Graph#query(String, Map)}: always pass
         * untrusted input as a parameter rather than concatenating it into {@code query}.
         * @param query Cypher query
         * @param params parameters map; its values may be null
         * @return the reply: the result set
         * @throws IllegalArgumentException if {@code query}, {@code params} or a parameter is rejected, as by
         *     {@link Graph#query(String, Map)}; nothing is queued then
         */
        Reply<ResultSet> query(String query, Map<String, @Nullable Object> params);

        /**
         * Queues a Cypher read-only query with parameters, which work as for {@link #query(String, Map)}.
         * @param query Cypher query
         * @param params parameters map; its values may be null
         * @return the reply: the result set
         * @throws IllegalArgumentException if {@code query}, {@code params} or a parameter is rejected; nothing is
         *     queued then
         */
        Reply<ResultSet> readOnlyQuery(String query, Map<String, @Nullable Object> params);

        /**
         * Queues a Cypher query with parameters and a timeout, which work as for {@link #query(String, Map)} and
         * {@link #query(String, long)}.
         * @param query Cypher query
         * @param params parameters map; its values may be null
         * @param timeout timeout in milliseconds; not negative
         * @return the reply: the result set
         * @throws IllegalArgumentException if {@code timeout} is negative, or {@code query}, {@code params} or a
         *     parameter is rejected; nothing is queued then
         */
        Reply<ResultSet> query(String query, Map<String, @Nullable Object> params, long timeout);

        /**
         * Queues a Cypher read-only query with parameters and a timeout, which work as for
         * {@link #query(String, Map)} and {@link #query(String, long)}.
         * @param query Cypher query
         * @param params parameters map; its values may be null
         * @param timeout timeout in milliseconds; not negative
         * @return the reply: the result set
         * @throws IllegalArgumentException if {@code timeout} is negative, or {@code query}, {@code params} or a
         *     parameter is rejected; nothing is queued then
         */
        Reply<ResultSet> readOnlyQuery(String query, Map<String, @Nullable Object> params, long timeout);

        /**
         * Queues a call of a stored procedure without arguments.
         * @param procedure procedure name: identifiers ({@code [A-Za-z_][A-Za-z0-9_]*}) joined by dots, such as
         *     {@code db.labels}
         * @return the reply: the result set with the procedure's data
         * @throws IllegalArgumentException if {@code procedure} is null or not a valid procedure name; nothing is
         *     queued then
         */
        Reply<ResultSet> callProcedure(String procedure);

        /**
         * Queues a call of a stored procedure with arguments.
         * @param procedure procedure name: identifiers ({@code [A-Za-z_][A-Za-z0-9_]*}) joined by dots, such as
         *     {@code db.labels}
         * @param args procedure arguments, sent as string literals
         * @return the reply: the result set with the procedure's data
         * @throws IllegalArgumentException if {@code procedure} is null or not a valid procedure name, or
         *     {@code args} or one of its elements is null; nothing is queued then
         */
        Reply<ResultSet> callProcedure(String procedure, List<String> args);

        /**
         * Queues a call of a stored procedure.
         * @param procedure procedure name: identifiers ({@code [A-Za-z_][A-Za-z0-9_]*}) joined by dots, such as
         *     {@code db.labels}
         * @param args procedure arguments, sent as string literals
         * @param kwargs procedure output arguments: the {@code "y"} entry lists the output columns to bind with
         *     Cypher's {@code YIELD} clause, each a Cypher identifier ({@code [A-Za-z_][A-Za-z0-9_]*}, without an
         *     {@code AS} alias)
         * @return the reply: the result set with the procedure's data
         * @throws IllegalArgumentException if {@code procedure} is null or not a valid procedure name, if
         *     {@code args}, {@code kwargs} or an argument is null, or if a {@code YIELD} column is not a Cypher
         *     identifier; nothing is queued then
         */
        Reply<ResultSet> callProcedure(String procedure, List<String> args, Map<String, List<String>> kwargs);

        /**
         * Queues a Cypher query that runs and produces its execution plan, with metrics for each operation.
         * @param query Cypher query
         * @return the reply: the result set with the plan and its metrics
         */
        Reply<ResultSet> profile(String query);

        /**
         * Queues a Cypher query with parameters that runs and produces its execution plan, with metrics for each
         * operation. The parameters work as for {@link #query(String, Map)}.
         * @param query Cypher query
         * @param params parameters map; its values may be null
         * @return the reply: the result set with the plan and its metrics
         * @throws IllegalArgumentException if {@code query}, {@code params} or a parameter is rejected; nothing is
         *     queued then
         */
        Reply<ResultSet> profile(String query, Map<String, @Nullable Object> params);

        /**
         * Queues a request for the execution plan of a query.
         * @param query Cypher query
         * @return the reply: the plan's lines. Reading it throws a {@link com.falkordb.exceptions.GraphException} if
         *     the plan holds an error or the server rejected the command
         */
        Reply<List<String>> explain(String query);

        /**
         * Queues a request for the execution plan of a query with parameters, which work as for
         * {@link #query(String, Map)}.
         * @param query Cypher query
         * @param params parameters map; its values may be null
         * @return the reply: the plan's lines. Reading it throws a {@link com.falkordb.exceptions.GraphException} if
         *     the plan holds an error or the server rejected the command
         * @throws IllegalArgumentException if {@code query}, {@code params} or a parameter is rejected; nothing is
         *     queued then
         */
        Reply<List<String>> explain(String query, Map<String, @Nullable Object> params);

        /**
         * Queues a copy of the graph.
         * @param destinationGraphId the copy's name
         * @return the reply: the copy's running time statistics
         */
        Reply<String> copyGraph(String destinationGraphId);

        /**
         * Queues a delete of the entire graph. The client's schema cache is cleared once the delete has run, when
         * its reply is read; a reply queued before it is decoded with what the cache held when the delete was
         * queued, as for {@link GraphPipeline#deleteGraph()}.
         * @return the reply: the delete's running time statistics
         */
        Reply<String> deleteGraph();

        /**
         * Queues a {@code WAIT}, which blocks until the writes before it are acknowledged by at least {@code
         * replicas} replicas, or the timeout has passed. The pipeline's read waits for it.
         * @param replicas how many replicas must acknowledge the writes
         * @param timeout the most milliseconds to wait; {@code 0} waits for ever, and so does the pipeline's read,
         *     and closing the context, if the replicas never acknowledge
         * @return the reply: how many replicas acknowledged the writes
         */
        Reply<Long> waitReplicas(int replicas, long timeout);

        /**
         * Queues a Redis command that isn't a graph command, such as {@code SET}, {@code GET} or {@code INCR}, to run
         * with the graph commands.
         *
         * <p>The reply is decoded from what the server sent into JDK types: a simple or bulk string as a UTF-8 {@code String},
         * an integer as a {@code Long}, an array as a {@code List} of its elements decoded the same way, with a
         * {@link com.falkordb.exceptions.GraphException} where an element is an error, and a nil reply as {@code null}.
         * The driver speaks RESP2 unless its connection URI asks for {@code protocol=3}; then a double is a
         * {@code Double}, a boolean a {@code Boolean}, a big number a {@code java.math.BigInteger}, a set a
         * {@code List} and a map a {@code LinkedHashMap}, except an empty map, which is an empty {@code List}.
         * An error reply makes reading the reply throw a {@link com.falkordb.exceptions.GraphException}.
         *
         * <p>A command that would break this pipeline, the connection or the client's schema cache is refused with
         * an {@link IllegalArgumentException}, and nothing is sent. Command names are compared ignoring the case of
         * ASCII letters, as Redis does:
         *
         * <ul>
         *   <li>commands this API runs itself: {@code MULTI}, {@code EXEC}, {@code DISCARD}, {@code WATCH} and
         *       {@code UNWATCH} (see {@link GraphContext#transaction()} and {@link GraphContext#watch(String...)}),
         *       {@code WAIT} (see {@link Pipeline#waitReplicas(int, long)}), and every {@code GRAPH.*} command, whose
         *       reply only the graph methods decode, with the client's schema cache, which they keep up to date;
         *   <li>commands that delete or replace the graph's key behind that cache, which would then decode a new
         *       graph's replies with the old graph's names: {@code DEL}, {@code UNLINK}, {@code GETDEL}, {@code RENAME},
         *       {@code RENAMENX}, {@code MOVE}, {@code RESTORE} and {@code MIGRATE} of the graph's key, {@code COPY} to
         *       it, and {@code FLUSHALL}, {@code FLUSHDB} and {@code SWAPDB}; delete the graph with
         *       {@link #deleteGraph()}. Other commands that write the graph's key aren't recognized: see below;
         *   <li>commands that change the connection's state, which outlives the context when the pool lends the
         *       connection on, or how its replies are framed: {@code AUTH}, {@code HELLO}, {@code SELECT},
         *       {@code CLIENT}, {@code RESET}, {@code QUIT}, {@code READONLY}, {@code READWRITE}, {@code ASKING},
         *       {@code SCRIPT DEBUG};
         *   <li>commands that take the connection out of request and reply: {@code SUBSCRIBE}, {@code PSUBSCRIBE},
         *       {@code SSUBSCRIBE}, {@code UNSUBSCRIBE}, {@code PUNSUBSCRIBE}, {@code SUNSUBSCRIBE}, {@code MONITOR},
         *       {@code SYNC}, {@code PSYNC}, {@code REPLCONF};
         *   <li>blocking commands, which hold the connection in a pipeline, so that closing the context waits for
         *       them, and don't block inside {@code MULTI}: {@code BLPOP}, {@code BRPOP}, {@code BRPOPLPUSH},
         *       {@code BLMOVE}, {@code BLMPOP}, {@code BZPOPMIN}, {@code BZPOPMAX}, {@code BZMPOP}, {@code WAITAOF},
         *       and {@code XREAD} or {@code XREADGROUP} with {@code BLOCK}.
         * </ul>
         *
         * <p>Some of these may be allowed in a later release, such as the {@code GRAPH.*} commands that don't touch a
         * graph. Other commands are sent as they are: an overwrite of the graph's key, such as a {@code SET} of it, or
         * its expiry, still leaves the schema cache as it was, as a change by another client would. In a cluster the
         * context's connection is to the node that serves the graph's key, so a command on a key in another hash slot
         * fails with a {@code MOVED} error; put the graph's name in the key as a hash tag to keep it on that node.
         *
         * @param command the command's name, such as {@code "SET"}
         * @param args its arguments
         * @return the reply
         * @throws IllegalArgumentException if the command is refused, {@code command} is null or empty, or
         *     {@code args} or one of its elements is null; nothing is queued then
         */
        Reply<@Nullable Object> sendCommand(String command, String... args);

        /**
         * Queues a Redis command that isn't a graph command, with binary arguments, such as a {@code SET} of a binary
         * value.
         *
         * <p>The reply is decoded from what the server sent into JDK types: a simple or bulk string as a {@code byte[]},
         * an integer as a {@code Long}, an array as a {@code List} of its elements decoded the same way, with a
         * {@link com.falkordb.exceptions.GraphException} where an element is an error, and a nil reply as {@code null}.
         * The driver speaks RESP2 unless its connection URI asks for {@code protocol=3}; then a double is a
         * {@code Double}, a boolean a {@code Boolean}, a big number a {@code java.math.BigInteger}, a set a
         * {@code List} and a map a {@code LinkedHashMap} (whose {@code byte[]} keys compare by identity), except an empty map, which is an empty {@code List}.
         * An error reply makes reading the reply throw a {@link com.falkordb.exceptions.GraphException}.
         *
         * <p>A command that would break this pipeline, the connection or the client's schema cache is refused with
         * an {@link IllegalArgumentException}, and nothing is sent. Command names are compared ignoring the case of
         * ASCII letters, as Redis does:
         *
         * <ul>
         *   <li>commands this API runs itself: {@code MULTI}, {@code EXEC}, {@code DISCARD}, {@code WATCH} and
         *       {@code UNWATCH} (see {@link GraphContext#transaction()} and {@link GraphContext#watch(String...)}),
         *       {@code WAIT} (see {@link Pipeline#waitReplicas(int, long)}), and every {@code GRAPH.*} command, whose
         *       reply only the graph methods decode, with the client's schema cache, which they keep up to date;
         *   <li>commands that delete or replace the graph's key behind that cache, which would then decode a new
         *       graph's replies with the old graph's names: {@code DEL}, {@code UNLINK}, {@code GETDEL}, {@code RENAME},
         *       {@code RENAMENX}, {@code MOVE}, {@code RESTORE} and {@code MIGRATE} of the graph's key, {@code COPY} to
         *       it, and {@code FLUSHALL}, {@code FLUSHDB} and {@code SWAPDB}; delete the graph with
         *       {@link #deleteGraph()}. Other commands that write the graph's key aren't recognized: see below;
         *   <li>commands that change the connection's state, which outlives the context when the pool lends the
         *       connection on, or how its replies are framed: {@code AUTH}, {@code HELLO}, {@code SELECT},
         *       {@code CLIENT}, {@code RESET}, {@code QUIT}, {@code READONLY}, {@code READWRITE}, {@code ASKING},
         *       {@code SCRIPT DEBUG};
         *   <li>commands that take the connection out of request and reply: {@code SUBSCRIBE}, {@code PSUBSCRIBE},
         *       {@code SSUBSCRIBE}, {@code UNSUBSCRIBE}, {@code PUNSUBSCRIBE}, {@code SUNSUBSCRIBE}, {@code MONITOR},
         *       {@code SYNC}, {@code PSYNC}, {@code REPLCONF};
         *   <li>blocking commands, which hold the connection in a pipeline, so that closing the context waits for
         *       them, and don't block inside {@code MULTI}: {@code BLPOP}, {@code BRPOP}, {@code BRPOPLPUSH},
         *       {@code BLMOVE}, {@code BLMPOP}, {@code BZPOPMIN}, {@code BZPOPMAX}, {@code BZMPOP}, {@code WAITAOF},
         *       and {@code XREAD} or {@code XREADGROUP} with {@code BLOCK}.
         * </ul>
         *
         * <p>Some of these may be allowed in a later release, such as the {@code GRAPH.*} commands that don't touch a
         * graph. Other commands are sent as they are: an overwrite of the graph's key, such as a {@code SET} of it, or
         * its expiry, still leaves the schema cache as it was, as a change by another client would. In a cluster the
         * context's connection is to the node that serves the graph's key, so a command on a key in another hash slot
         * fails with a {@code MOVED} error; put the graph's name in the key as a hash tag to keep it on that node.
         *
         * @param command the command's name, such as {@code "SET"}
         * @param args its arguments, as bytes
         * @return the reply
         * @throws IllegalArgumentException if the command is refused, {@code command} is null or empty, or
         *     {@code args} or one of its elements is null; nothing is queued then
         */
        Reply<@Nullable Object> sendBinaryCommand(String command, byte[]... args);

        /**
         * Reads the replies to every command queued so far, without decoding them: each is decoded when its
         * {@link Reply} is read. A {@link #deleteGraph()} that ran clears the schema cache now. The pipeline can
         * be used again afterwards, unless the read failed. Once the context is closed, which read the replies
         * the pipeline awaited, there is nothing to do.
         * @throws IllegalStateException if the pipeline is closed or a read of it failed before, if it awaits
         *     replies while another pipeline or transaction on the context does too, or if the context was closed
         *     before the
         *     replies this one awaits were read
         */
        void sync();

        /**
         * Reads and decodes the replies to every command queued since the last read, in order. The pipeline can
         * be used again afterwards, unless the read failed.
         * @return the decoded replies, as their {@link Reply}s return them: an element is {@code null} where the
         *     reply is nil, and the exception reading the reply throws, a
         *     {@link com.falkordb.exceptions.GraphException} or the failure of a lookup decoding needed, where the
         *     command failed or its reply can't be decoded. Unmodifiable
         * @throws IllegalStateException if the pipeline is closed or a read of it failed before, if it awaits
         *     replies while another pipeline or transaction on the context does too, or if the context was closed
         *     before the
         *     replies this one awaits were read
         */
        List<@Nullable Object> syncAndReturnAll();

        /**
         * Reads the replies the pipeline awaits, as {@link #sync()} does, and closes the pipeline: it can't be
         * used afterwards, and closing it again does nothing. There is nothing to read once the context is closed,
         * which has read them, and after a read of the pipeline failed the context's connection is closed instead
         * of going back to the pool. The context's connection otherwise stays open.
         *
         * <p>Unlike {@link Closeable#close()}, this throws no checked exception. It can still throw an unchecked
         * one: an {@link IllegalStateException} if another pipeline or transaction on the context awaits replies,
         * or, say, a {@code JedisConnectionException} if the connection fails.
         */
        @Override
        void close();
    }

    /**
     * A transaction on a {@link GraphContext}'s connection: {@code MULTI} is sent when it starts, the commands are
     * queued on the server, and {@link #exec()} runs them all at once. Each command returns a {@link Reply}, which
     * can be read once {@code exec()} ran the transaction. Get one with {@link GraphContext#transaction()}, after
     * {@link GraphContext#watch(String...)} if the transaction must not run once a key has changed:
     *
     * <pre>{@code
     * try (GraphContext context = graph.getContext()) {
     *     context.watch("social");
     *     GraphContext.Transaction transaction = context.transaction();
     *     Reply<ResultSet> created = transaction.query("CREATE (:Person {name: 'Alice'})");
     *     transaction.sendCommand("INCR", "people");
     *     if (transaction.exec() == null) {
     *         // aborted: another client changed the graph since watch(); nothing ran
     *     } else {
     *         System.out.println(created.get().getStatistics().nodesCreated());
     *     }
     * }
     * }</pre>
     *
     * <p>A transaction is used once: after {@link #exec()} or {@link #discard()} nothing can be queued on it. The
     * replies are decoded by {@code exec()}, as for the deprecated {@link GraphTransaction}, which describes how a
     * {@link #deleteGraph()} affects the replies queued before it. A reply that fails to decode throws the same
     * exception on every {@link Reply#get()}. The server's error replies are
     * {@link com.falkordb.exceptions.GraphException}s. {@code COPY} of the graph can't be queued in a transaction:
     * FalkorDB doesn't support it there.
     *
     * <p>Until the transaction ends, the context's connection is its own: the context runs none of its own commands,
     * {@link GraphContext#watch(String...) watch} included, and no other pipeline or transaction can be started or
     * used on it, since what they sent would be queued inside this transaction's {@code MULTI}. Each throws an
     * {@link IllegalStateException} instead, as does queuing a command once the context is closed.
     *
     * <p>Like its context, a transaction is meant for one thread. Closing the context discards a transaction still
     * open.
     */
    interface Transaction extends Closeable {

        /**
         * Queues a Cypher query.
         * @param query Cypher query
         * @return the reply: the result set
         */
        Reply<ResultSet> query(String query);

        /**
         * Queues a Cypher read-only query.
         * @param query Cypher query
         * @return the reply: the result set
         */
        Reply<ResultSet> readOnlyQuery(String query);

        /**
         * Queues a Cypher query with a timeout.
         *
         * <p>The timeout is sent with the query and limits how long the server runs it; {@code 0} sets none, so the
         * server's default ({@code TIMEOUT_DEFAULT}) applies. It doesn't change how long the client waits for the
         * replies in {@link #exec()}, which a socket (read) timeout on the connection can still end: see
         * {@link Graph#query(String, long)}.
         *
         * @param query Cypher query
         * @param timeout timeout in milliseconds; not negative
         * @return the reply: the result set
         * @throws IllegalArgumentException if {@code timeout} is negative; nothing is queued then
         */
        Reply<ResultSet> query(String query, long timeout);

        /**
         * Queues a Cypher read-only query with a timeout, which works as for {@link #query(String, long)}.
         * @param query Cypher query
         * @param timeout timeout in milliseconds; not negative
         * @return the reply: the result set
         * @throws IllegalArgumentException if {@code timeout} is negative; nothing is queued then
         */
        Reply<ResultSet> readOnlyQuery(String query, long timeout);

        /**
         * Queues a Cypher query with parameters, which are encoded as for {@link Graph#query(String, Map)}: always pass
         * untrusted input as a parameter rather than concatenating it into {@code query}.
         * @param query Cypher query
         * @param params parameters map; its values may be null
         * @return the reply: the result set
         * @throws IllegalArgumentException if {@code query}, {@code params} or a parameter is rejected, as by
         *     {@link Graph#query(String, Map)}; nothing is queued then
         */
        Reply<ResultSet> query(String query, Map<String, @Nullable Object> params);

        /**
         * Queues a Cypher read-only query with parameters, which work as for {@link #query(String, Map)}.
         * @param query Cypher query
         * @param params parameters map; its values may be null
         * @return the reply: the result set
         * @throws IllegalArgumentException if {@code query}, {@code params} or a parameter is rejected; nothing is
         *     queued then
         */
        Reply<ResultSet> readOnlyQuery(String query, Map<String, @Nullable Object> params);

        /**
         * Queues a Cypher query with parameters and a timeout, which work as for {@link #query(String, Map)} and
         * {@link #query(String, long)}.
         * @param query Cypher query
         * @param params parameters map; its values may be null
         * @param timeout timeout in milliseconds; not negative
         * @return the reply: the result set
         * @throws IllegalArgumentException if {@code timeout} is negative, or {@code query}, {@code params} or a
         *     parameter is rejected; nothing is queued then
         */
        Reply<ResultSet> query(String query, Map<String, @Nullable Object> params, long timeout);

        /**
         * Queues a Cypher read-only query with parameters and a timeout, which work as for
         * {@link #query(String, Map)} and {@link #query(String, long)}.
         * @param query Cypher query
         * @param params parameters map; its values may be null
         * @param timeout timeout in milliseconds; not negative
         * @return the reply: the result set
         * @throws IllegalArgumentException if {@code timeout} is negative, or {@code query}, {@code params} or a
         *     parameter is rejected; nothing is queued then
         */
        Reply<ResultSet> readOnlyQuery(String query, Map<String, @Nullable Object> params, long timeout);

        /**
         * Queues a call of a stored procedure without arguments.
         * @param procedure procedure name: identifiers ({@code [A-Za-z_][A-Za-z0-9_]*}) joined by dots, such as
         *     {@code db.labels}
         * @return the reply: the result set with the procedure's data
         * @throws IllegalArgumentException if {@code procedure} is null or not a valid procedure name; nothing is
         *     queued then
         */
        Reply<ResultSet> callProcedure(String procedure);

        /**
         * Queues a call of a stored procedure with arguments.
         * @param procedure procedure name: identifiers ({@code [A-Za-z_][A-Za-z0-9_]*}) joined by dots, such as
         *     {@code db.labels}
         * @param args procedure arguments, sent as string literals
         * @return the reply: the result set with the procedure's data
         * @throws IllegalArgumentException if {@code procedure} is null or not a valid procedure name, or
         *     {@code args} or one of its elements is null; nothing is queued then
         */
        Reply<ResultSet> callProcedure(String procedure, List<String> args);

        /**
         * Queues a call of a stored procedure.
         * @param procedure procedure name: identifiers ({@code [A-Za-z_][A-Za-z0-9_]*}) joined by dots, such as
         *     {@code db.labels}
         * @param args procedure arguments, sent as string literals
         * @param kwargs procedure output arguments: the {@code "y"} entry lists the output columns to bind with
         *     Cypher's {@code YIELD} clause, each a Cypher identifier ({@code [A-Za-z_][A-Za-z0-9_]*}, without an
         *     {@code AS} alias)
         * @return the reply: the result set with the procedure's data
         * @throws IllegalArgumentException if {@code procedure} is null or not a valid procedure name, if
         *     {@code args}, {@code kwargs} or an argument is null, or if a {@code YIELD} column is not a Cypher
         *     identifier; nothing is queued then
         */
        Reply<ResultSet> callProcedure(String procedure, List<String> args, Map<String, List<String>> kwargs);

        /**
         * Queues a Cypher query that runs and produces its execution plan, with metrics for each operation.
         * @param query Cypher query
         * @return the reply: the result set with the plan and its metrics
         */
        Reply<ResultSet> profile(String query);

        /**
         * Queues a Cypher query with parameters that runs and produces its execution plan, with metrics for each
         * operation. The parameters work as for {@link #query(String, Map)}.
         * @param query Cypher query
         * @param params parameters map; its values may be null
         * @return the reply: the result set with the plan and its metrics
         * @throws IllegalArgumentException if {@code query}, {@code params} or a parameter is rejected; nothing is
         *     queued then
         */
        Reply<ResultSet> profile(String query, Map<String, @Nullable Object> params);

        /**
         * Queues a request for the execution plan of a query.
         * @param query Cypher query
         * @return the reply: the plan's lines. Reading it throws a {@link com.falkordb.exceptions.GraphException} if
         *     the plan holds an error or the server rejected the command
         */
        Reply<List<String>> explain(String query);

        /**
         * Queues a request for the execution plan of a query with parameters, which work as for
         * {@link #query(String, Map)}.
         * @param query Cypher query
         * @param params parameters map; its values may be null
         * @return the reply: the plan's lines. Reading it throws a {@link com.falkordb.exceptions.GraphException} if
         *     the plan holds an error or the server rejected the command
         * @throws IllegalArgumentException if {@code query}, {@code params} or a parameter is rejected; nothing is
         *     queued then
         */
        Reply<List<String>> explain(String query, Map<String, @Nullable Object> params);

        /**
         * Queues a delete of the entire graph. The client's schema cache is cleared when {@link #exec()} has run
         * the delete: not when it is queued, and not at all if the transaction is discarded or aborted. A reply
         * queued before it is decoded with what the cache held when the delete was queued, as for
         * {@link GraphTransaction#deleteGraph()}.
         * @return the reply: the delete's running time statistics
         */
        Reply<String> deleteGraph();

        /**
         * Queues a Redis command that isn't a graph command, such as {@code SET}, {@code GET} or {@code INCR}, to run
         * with the graph commands.
         *
         * <p>The reply is decoded from what the server sent into JDK types: a simple or bulk string as a UTF-8 {@code String},
         * an integer as a {@code Long}, an array as a {@code List} of its elements decoded the same way, with a
         * {@link com.falkordb.exceptions.GraphException} where an element is an error, and a nil reply as {@code null}.
         * The driver speaks RESP2 unless its connection URI asks for {@code protocol=3}; then a double is a
         * {@code Double}, a boolean a {@code Boolean}, a big number a {@code java.math.BigInteger}, a set a
         * {@code List} and a map a {@code LinkedHashMap}, except an empty map, which is an empty {@code List}.
         * An error reply makes reading the reply throw a {@link com.falkordb.exceptions.GraphException}.
         *
         * <p>A command that would break this transaction, the connection or the client's schema cache is refused with
         * an {@link IllegalArgumentException}, and nothing is sent. Command names are compared ignoring the case of
         * ASCII letters, as Redis does:
         *
         * <ul>
         *   <li>commands this API runs itself: {@code MULTI}, {@code EXEC}, {@code DISCARD}, {@code WATCH} and
         *       {@code UNWATCH} (see {@link GraphContext#transaction()} and {@link GraphContext#watch(String...)}),
         *       and every {@code GRAPH.*} command, whose
         *       reply only the graph methods decode, with the client's schema cache, which they keep up to date;
         *   <li>commands that delete or replace the graph's key behind that cache, which would then decode a new
         *       graph's replies with the old graph's names: {@code DEL}, {@code UNLINK}, {@code GETDEL}, {@code RENAME},
         *       {@code RENAMENX}, {@code MOVE}, {@code RESTORE} and {@code MIGRATE} of the graph's key, {@code COPY} to
         *       it, and {@code FLUSHALL}, {@code FLUSHDB} and {@code SWAPDB}; delete the graph with
         *       {@link #deleteGraph()}. Other commands that write the graph's key aren't recognized: see below;
         *   <li>commands that change the connection's state, which outlives the context when the pool lends the
         *       connection on, or how its replies are framed: {@code AUTH}, {@code HELLO}, {@code SELECT},
         *       {@code CLIENT}, {@code RESET}, {@code QUIT}, {@code READONLY}, {@code READWRITE}, {@code ASKING},
         *       {@code SCRIPT DEBUG};
         *   <li>commands that take the connection out of request and reply: {@code SUBSCRIBE}, {@code PSUBSCRIBE},
         *       {@code SSUBSCRIBE}, {@code UNSUBSCRIBE}, {@code PUNSUBSCRIBE}, {@code SUNSUBSCRIBE}, {@code MONITOR},
         *       {@code SYNC}, {@code PSYNC}, {@code REPLCONF};
         *   <li>blocking commands, which hold the connection in a pipeline, so that closing the context waits for
         *       them, and don't block inside {@code MULTI}: {@code BLPOP}, {@code BRPOP}, {@code BRPOPLPUSH},
         *       {@code BLMOVE}, {@code BLMPOP}, {@code BZPOPMIN}, {@code BZPOPMAX}, {@code BZMPOP}, {@code WAITAOF},
         *       {@code WAIT}, and {@code XREAD} or {@code XREADGROUP} with {@code BLOCK}.
         * </ul>
         *
         * <p>Some of these may be allowed in a later release, such as the {@code GRAPH.*} commands that don't touch a
         * graph. Other commands are sent as they are: an overwrite of the graph's key, such as a {@code SET} of it, or
         * its expiry, still leaves the schema cache as it was, as a change by another client would. In a cluster the
         * context's connection is to the node that serves the graph's key, so a command on a key in another hash slot
         * fails with a {@code MOVED} error; put the graph's name in the key as a hash tag to keep it on that node.
         *
         * @param command the command's name, such as {@code "SET"}
         * @param args its arguments
         * @return the reply
         * @throws IllegalArgumentException if the command is refused, {@code command} is null or empty, or
         *     {@code args} or one of its elements is null; nothing is queued then
         */
        Reply<@Nullable Object> sendCommand(String command, String... args);

        /**
         * Queues a Redis command that isn't a graph command, with binary arguments, such as a {@code SET} of a binary
         * value.
         *
         * <p>The reply is decoded from what the server sent into JDK types: a simple or bulk string as a {@code byte[]},
         * an integer as a {@code Long}, an array as a {@code List} of its elements decoded the same way, with a
         * {@link com.falkordb.exceptions.GraphException} where an element is an error, and a nil reply as {@code null}.
         * The driver speaks RESP2 unless its connection URI asks for {@code protocol=3}; then a double is a
         * {@code Double}, a boolean a {@code Boolean}, a big number a {@code java.math.BigInteger}, a set a
         * {@code List} and a map a {@code LinkedHashMap} (whose {@code byte[]} keys compare by identity), except an empty map, which is an empty {@code List}.
         * An error reply makes reading the reply throw a {@link com.falkordb.exceptions.GraphException}.
         *
         * <p>A command that would break this transaction, the connection or the client's schema cache is refused with
         * an {@link IllegalArgumentException}, and nothing is sent. Command names are compared ignoring the case of
         * ASCII letters, as Redis does:
         *
         * <ul>
         *   <li>commands this API runs itself: {@code MULTI}, {@code EXEC}, {@code DISCARD}, {@code WATCH} and
         *       {@code UNWATCH} (see {@link GraphContext#transaction()} and {@link GraphContext#watch(String...)}),
         *       and every {@code GRAPH.*} command, whose
         *       reply only the graph methods decode, with the client's schema cache, which they keep up to date;
         *   <li>commands that delete or replace the graph's key behind that cache, which would then decode a new
         *       graph's replies with the old graph's names: {@code DEL}, {@code UNLINK}, {@code GETDEL}, {@code RENAME},
         *       {@code RENAMENX}, {@code MOVE}, {@code RESTORE} and {@code MIGRATE} of the graph's key, {@code COPY} to
         *       it, and {@code FLUSHALL}, {@code FLUSHDB} and {@code SWAPDB}; delete the graph with
         *       {@link #deleteGraph()}. Other commands that write the graph's key aren't recognized: see below;
         *   <li>commands that change the connection's state, which outlives the context when the pool lends the
         *       connection on, or how its replies are framed: {@code AUTH}, {@code HELLO}, {@code SELECT},
         *       {@code CLIENT}, {@code RESET}, {@code QUIT}, {@code READONLY}, {@code READWRITE}, {@code ASKING},
         *       {@code SCRIPT DEBUG};
         *   <li>commands that take the connection out of request and reply: {@code SUBSCRIBE}, {@code PSUBSCRIBE},
         *       {@code SSUBSCRIBE}, {@code UNSUBSCRIBE}, {@code PUNSUBSCRIBE}, {@code SUNSUBSCRIBE}, {@code MONITOR},
         *       {@code SYNC}, {@code PSYNC}, {@code REPLCONF};
         *   <li>blocking commands, which hold the connection in a pipeline, so that closing the context waits for
         *       them, and don't block inside {@code MULTI}: {@code BLPOP}, {@code BRPOP}, {@code BRPOPLPUSH},
         *       {@code BLMOVE}, {@code BLMPOP}, {@code BZPOPMIN}, {@code BZPOPMAX}, {@code BZMPOP}, {@code WAITAOF},
         *       {@code WAIT}, and {@code XREAD} or {@code XREADGROUP} with {@code BLOCK}.
         * </ul>
         *
         * <p>Some of these may be allowed in a later release, such as the {@code GRAPH.*} commands that don't touch a
         * graph. Other commands are sent as they are: an overwrite of the graph's key, such as a {@code SET} of it, or
         * its expiry, still leaves the schema cache as it was, as a change by another client would. In a cluster the
         * context's connection is to the node that serves the graph's key, so a command on a key in another hash slot
         * fails with a {@code MOVED} error; put the graph's name in the key as a hash tag to keep it on that node.
         *
         * @param command the command's name, such as {@code "SET"}
         * @param args its arguments, as bytes
         * @return the reply
         * @throws IllegalArgumentException if the command is refused, {@code command} is null or empty, or
         *     {@code args} or one of its elements is null; nothing is queued then
         */
        Reply<@Nullable Object> sendBinaryCommand(String command, byte[]... args);

        /**
         * Runs the transaction: reads and decodes the reply to every command queued, in order. The transaction
         * ends, and can't be used again.
         * @return the decoded replies, as their {@link Reply}s return them: an element is {@code null} where the
         *     reply is nil, and the exception reading the reply throws, a
         *     {@link com.falkordb.exceptions.GraphException} or the failure of a lookup decoding needed, where the
         *     command failed or its reply can't be decoded. Unmodifiable. {@code null} if the transaction was
         *     aborted, because a key the context {@linkplain GraphContext#watch(String...) watched} changed: then no
         *     command ran, and reading a {@link Reply} of it throws an {@link IllegalStateException} saying so
         * @throws com.falkordb.exceptions.GraphException if the server refused the transaction, as with
         *     {@code EXECABORT} when a command failed to queue: then no command ran
         * @throws IllegalStateException if the transaction has ended already, the context is closed, or another
         *     pipeline or transaction on the context awaits replies
         */
        @Nullable
        List<@Nullable Object> exec();

        /**
         * Drops the queued commands without running them, and ends the transaction; a queued
         * {@link #deleteGraph()} then leaves the schema cache alone. Keys the context watched are unwatched.
         * @throws IllegalStateException if the transaction has ended already, the context is closed, or another
         *     pipeline or transaction on the context awaits replies
         * @throws com.falkordb.exceptions.GraphException if the server rejects the {@code DISCARD}
         */
        void discard();

        /**
         * Discards the transaction unless it has ended; there is nothing to do once the context is closed, which
         * discards it. The context's connection stays open.
         *
         * <p>Unlike {@link Closeable#close()}, this throws no checked exception.
         */
        @Override
        void close();
    }
}
