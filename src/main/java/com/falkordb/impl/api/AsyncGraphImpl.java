package com.falkordb.impl.api;

import com.falkordb.AsyncGraph;
import com.falkordb.GraphContextGenerator;
import com.falkordb.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

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

    @Override
    public CompletableFuture<ResultSet> query(String query) {
        return CompletableFuture.supplyAsync(() -> graph.query(query), executor);
    }

    @Override
    public CompletableFuture<ResultSet> readOnlyQuery(String query) {
        return CompletableFuture.supplyAsync(() -> graph.readOnlyQuery(query), executor);
    }

    @Override
    public CompletableFuture<ResultSet> query(String query, long timeout) {
        return CompletableFuture.supplyAsync(() -> graph.query(query, timeout), executor);
    }

    @Override
    public CompletableFuture<ResultSet> readOnlyQuery(String query, long timeout) {
        return CompletableFuture.supplyAsync(() -> graph.readOnlyQuery(query, timeout), executor);
    }

    @Override
    public CompletableFuture<ResultSet> query(String query, Map<String, Object> params) {
        return CompletableFuture.supplyAsync(() -> graph.query(query, params), executor);
    }

    @Override
    public CompletableFuture<ResultSet> readOnlyQuery(String query, Map<String, Object> params) {
        return CompletableFuture.supplyAsync(() -> graph.readOnlyQuery(query, params), executor);
    }

    @Override
    public CompletableFuture<ResultSet> query(String query, Map<String, Object> params, long timeout) {
        return CompletableFuture.supplyAsync(() -> graph.query(query, params, timeout), executor);
    }

    @Override
    public CompletableFuture<ResultSet> readOnlyQuery(String query, Map<String, Object> params, long timeout) {
        return CompletableFuture.supplyAsync(() -> graph.readOnlyQuery(query, params, timeout), executor);
    }

    @Override
    public CompletableFuture<ResultSet> callProcedure(String procedure) {
        return CompletableFuture.supplyAsync(() -> graph.callProcedure(procedure), executor);
    }

    @Override
    public CompletableFuture<ResultSet> profile(String query) {
        return CompletableFuture.supplyAsync(() -> graph.profile(query), executor);
    }

    @Override
    public CompletableFuture<ResultSet> profile(String query, Map<String, Object> params) {
        return CompletableFuture.supplyAsync(() -> graph.profile(query, params), executor);
    }

    @Override
    public CompletableFuture<ResultSet> callProcedure(String procedure, List<String> args) {
        return CompletableFuture.supplyAsync(() -> graph.callProcedure(procedure, args), executor);
    }

    @Override
    public CompletableFuture<ResultSet> callProcedure(
            String procedure, List<String> args, Map<String, List<String>> kwargs) {
        return CompletableFuture.supplyAsync(() -> graph.callProcedure(procedure, args, kwargs), executor);
    }

    @Override
    public CompletableFuture<String> copyGraph(String destinationGraphId) {
        return CompletableFuture.supplyAsync(() -> graph.copyGraph(destinationGraphId), executor);
    }

    @Override
    public CompletableFuture<String> deleteGraph() {
        return CompletableFuture.supplyAsync(graph::deleteGraph, executor);
    }

    @Override
    public CompletableFuture<List<String>> explain(String query) {
        return CompletableFuture.supplyAsync(() -> graph.explain(query), executor);
    }

    @Override
    public CompletableFuture<List<String>> explain(String query, Map<String, Object> params) {
        return CompletableFuture.supplyAsync(() -> graph.explain(query, params), executor);
    }

    @Override
    public GraphContextGenerator graph() {
        return graph;
    }
}
