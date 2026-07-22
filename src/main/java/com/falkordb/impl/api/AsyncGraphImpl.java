package com.falkordb.impl.api;

import com.falkordb.AsyncGraph;
import com.falkordb.GraphContextGenerator;
import com.falkordb.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

/**
 * Default {@link AsyncGraph} implementation: submits each synchronous {@link com.falkordb.Graph}
 * operation to the caller-supplied {@link Executor} via {@link CompletableFuture#supplyAsync}.
 *
 * <p>Internal — construct through
 * {@link com.falkordb.AsyncFalkorDB#wrap(GraphContextGenerator, Executor)}.
 */
public final class AsyncGraphImpl implements AsyncGraph {

    private final GraphContextGenerator graph;
    private final Executor executor;

    /**
     * @param graph    the synchronous graph to delegate to (must be safe for concurrent calls)
     * @param executor the executor each operation runs on
     */
    public AsyncGraphImpl(GraphContextGenerator graph, Executor executor) {
        this.graph = Objects.requireNonNull(graph, "graph must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    /**
     * Submits {@code op} on the executor, always returning a future. If the executor rejects the task
     * (it is saturated or already shut down), the returned future completes exceptionally with the
     * {@link RejectedExecutionException} instead of throwing synchronously, so every {@link AsyncGraph}
     * method honors the "failures surface as exceptional completion" contract.
     */
    private <T> CompletableFuture<T> submit(Supplier<T> op) {
        try {
            return CompletableFuture.supplyAsync(op, executor);
        } catch (RejectedExecutionException rejected) {
            CompletableFuture<T> failed = new CompletableFuture<>();
            failed.completeExceptionally(rejected);
            return failed;
        }
    }

    @Override
    public CompletableFuture<ResultSet> query(String query) {
        return submit(() -> graph.query(query));
    }

    @Override
    public CompletableFuture<ResultSet> readOnlyQuery(String query) {
        return submit(() -> graph.readOnlyQuery(query));
    }

    @Override
    public CompletableFuture<ResultSet> query(String query, long timeout) {
        return submit(() -> graph.query(query, timeout));
    }

    @Override
    public CompletableFuture<ResultSet> readOnlyQuery(String query, long timeout) {
        return submit(() -> graph.readOnlyQuery(query, timeout));
    }

    @Override
    public CompletableFuture<ResultSet> query(String query, Map<String, Object> params) {
        return submit(() -> graph.query(query, params));
    }

    @Override
    public CompletableFuture<ResultSet> readOnlyQuery(String query, Map<String, Object> params) {
        return submit(() -> graph.readOnlyQuery(query, params));
    }

    @Override
    public CompletableFuture<ResultSet> query(String query, Map<String, Object> params, long timeout) {
        return submit(() -> graph.query(query, params, timeout));
    }

    @Override
    public CompletableFuture<ResultSet> readOnlyQuery(String query, Map<String, Object> params, long timeout) {
        return submit(() -> graph.readOnlyQuery(query, params, timeout));
    }

    @Override
    public CompletableFuture<ResultSet> callProcedure(String procedure) {
        return submit(() -> graph.callProcedure(procedure));
    }

    @Override
    public CompletableFuture<ResultSet> profile(String query) {
        return submit(() -> graph.profile(query));
    }

    @Override
    public CompletableFuture<ResultSet> profile(String query, Map<String, Object> params) {
        return submit(() -> graph.profile(query, params));
    }

    @Override
    public CompletableFuture<ResultSet> callProcedure(String procedure, List<String> args) {
        return submit(() -> graph.callProcedure(procedure, args));
    }

    @Override
    public CompletableFuture<ResultSet> callProcedure(
            String procedure, List<String> args, Map<String, List<String>> kwargs) {
        return submit(() -> graph.callProcedure(procedure, args, kwargs));
    }

    @Override
    public CompletableFuture<String> copyGraph(String destinationGraphId) {
        return submit(() -> graph.copyGraph(destinationGraphId));
    }

    @Override
    public CompletableFuture<String> deleteGraph() {
        return submit(graph::deleteGraph);
    }

    @Override
    public CompletableFuture<List<String>> explain(String query) {
        return submit(() -> graph.explain(query));
    }

    @Override
    public CompletableFuture<List<String>> explain(String query, Map<String, Object> params) {
        return submit(() -> graph.explain(query, params));
    }

    @Override
    public GraphContextGenerator graph() {
        return graph;
    }
}
