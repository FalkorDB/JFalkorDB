package com.falkordb;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * An asynchronous view over a {@link Graph}: every method mirrors the synchronous {@code Graph}
 * operation of the same name but runs it on a caller-supplied {@link java.util.concurrent.Executor}
 * and returns a {@link CompletableFuture}.
 *
 * <p>Obtain one with {@link AsyncFalkorDB#wrap(GraphContextGenerator, java.util.concurrent.Executor)},
 * backing it with a pool-per-call {@code driver.graph(id)} (safe for concurrent calls). The facade owns
 * nothing — neither the wrapped graph nor the executor — so it is not {@link java.io.Closeable}; the
 * caller drains outstanding futures, shuts the executor down, then closes the graph and driver.
 *
 * <p>On JDK 21+ this pairs with {@code Executors.newVirtualThreadPerTaskExecutor()} to run many
 * concurrent queries cheaply — the executor is supplied by the caller, so this type stays Java-8.
 *
 * <p><strong>Concurrency &amp; backpressure:</strong> real DB concurrency is bounded by the connection
 * pool ({@code poolMaxTotal}), not the executor; there is no built-in admission bound, so pair a
 * virtual-thread executor with a finite {@code poolMaxWait} (or your own semaphore) if you need one.
 * <strong>Cancellation is best-effort:</strong> cancelling a returned future frees the caller and skips
 * a not-yet-started query, but cannot interrupt a query already on the wire — bound in-flight work with
 * a per-query/server timeout or a finite socket timeout (not {@code cancel()}). Failures surface as the
 * future completing exceptionally ({@code join()} throws {@link java.util.concurrent.CompletionException};
 * {@code get()} throws {@link java.util.concurrent.ExecutionException}). The map/list arguments are read
 * later on the worker thread, so do not mutate them until the future completes.
 */
public interface AsyncGraph {

    /**
     * Asynchronously runs {@link Graph#query(String)}.
     *
     * @param query the Cypher query
     * @return a future completing with the result set
     */
    CompletableFuture<ResultSet> query(String query);

    /**
     * Asynchronously runs {@link Graph#readOnlyQuery(String)}.
     *
     * @param query the Cypher query
     * @return a future completing with the result set
     */
    CompletableFuture<ResultSet> readOnlyQuery(String query);

    /**
     * Asynchronously runs {@link Graph#query(String, long)}.
     *
     * @param query   the Cypher query
     * @param timeout timeout in milliseconds
     * @return a future completing with the result set
     */
    CompletableFuture<ResultSet> query(String query, long timeout);

    /**
     * Asynchronously runs {@link Graph#readOnlyQuery(String, long)}.
     *
     * @param query   the Cypher query
     * @param timeout timeout in milliseconds
     * @return a future completing with the result set
     */
    CompletableFuture<ResultSet> readOnlyQuery(String query, long timeout);

    /**
     * Asynchronously runs {@link Graph#query(String, Map)}.
     *
     * @param query  the Cypher query
     * @param params the query parameters
     * @return a future completing with the result set
     */
    CompletableFuture<ResultSet> query(String query, Map<String, Object> params);

    /**
     * Asynchronously runs {@link Graph#readOnlyQuery(String, Map)}.
     *
     * @param query  the Cypher query
     * @param params the query parameters
     * @return a future completing with the result set
     */
    CompletableFuture<ResultSet> readOnlyQuery(String query, Map<String, Object> params);

    /**
     * Asynchronously runs {@link Graph#query(String, Map, long)}.
     *
     * @param query   the Cypher query
     * @param params  the query parameters
     * @param timeout timeout in milliseconds
     * @return a future completing with the result set
     */
    CompletableFuture<ResultSet> query(String query, Map<String, Object> params, long timeout);

    /**
     * Asynchronously runs {@link Graph#readOnlyQuery(String, Map, long)}.
     *
     * @param query   the Cypher query
     * @param params  the query parameters
     * @param timeout timeout in milliseconds
     * @return a future completing with the result set
     */
    CompletableFuture<ResultSet> readOnlyQuery(String query, Map<String, Object> params, long timeout);

    /**
     * Asynchronously runs {@link Graph#callProcedure(String)}.
     *
     * @param procedure the procedure name
     * @return a future completing with the result set
     */
    CompletableFuture<ResultSet> callProcedure(String procedure);

    /**
     * Asynchronously runs {@link Graph#profile(String)}.
     *
     * @param query the Cypher query
     * @return a future completing with the result set (execution plan and metrics)
     */
    CompletableFuture<ResultSet> profile(String query);

    /**
     * Asynchronously runs {@link Graph#profile(String, Map)}.
     *
     * @param query  the Cypher query
     * @param params the query parameters
     * @return a future completing with the result set (execution plan and metrics)
     */
    CompletableFuture<ResultSet> profile(String query, Map<String, Object> params);

    /**
     * Asynchronously runs {@link Graph#callProcedure(String, List)}.
     *
     * @param procedure the procedure name
     * @param args      the procedure arguments
     * @return a future completing with the result set
     */
    CompletableFuture<ResultSet> callProcedure(String procedure, List<String> args);

    /**
     * Asynchronously runs {@link Graph#callProcedure(String, List, Map)}.
     *
     * @param procedure the procedure name
     * @param args      the procedure arguments
     * @param kwargs    the procedure output arguments
     * @return a future completing with the result set
     */
    CompletableFuture<ResultSet> callProcedure(String procedure, List<String> args, Map<String, List<String>> kwargs);

    /**
     * Asynchronously runs {@link Graph#copyGraph(String)}.
     *
     * @param destinationGraphId the destination graph name
     * @return a future completing with the copy running-time statistics
     */
    CompletableFuture<String> copyGraph(String destinationGraphId);

    /**
     * Asynchronously runs {@link Graph#deleteGraph()}.
     *
     * @return a future completing with the delete running-time statistics
     */
    CompletableFuture<String> deleteGraph();

    /**
     * Asynchronously runs {@link Graph#explain(String)}.
     *
     * @param query the Cypher query
     * @return a future completing with the execution plan
     */
    CompletableFuture<List<String>> explain(String query);

    /**
     * Asynchronously runs {@link Graph#explain(String, Map)}.
     *
     * @param query  the Cypher query
     * @param params the query parameters
     * @return a future completing with the execution plan
     */
    CompletableFuture<List<String>> explain(String query, Map<String, Object> params);

    /**
     * The wrapped synchronous graph. The caller owns and closes it; use this to reach synchronous or
     * connection-bound operations not exposed asynchronously.
     *
     * @return the wrapped graph
     */
    GraphContextGenerator graph();
}
