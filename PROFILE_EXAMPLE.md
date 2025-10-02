# GRAPH.PROFILE Example Usage

This document shows how to use the newly implemented `GRAPH.PROFILE` command in JFalkorDB.

## Basic Usage

```java
// Create a graph client
GraphContextGenerator client = FalkorDB.driver().graph("myGraph");

// Create some sample data
client.query("CREATE (:Person {name: 'Alice', age: 30})-[:KNOWS]->(:Person {name: 'Bob', age: 25})");

// Execute a profile query
ResultSet profileResult = client.profile("MATCH (p:Person) WHERE p.age > 20 RETURN p.name");

// The result contains execution plan with performance metrics
// Process the profiling information as needed
```

## With Parameters

```java
Map<String, Object> params = new HashMap<>();
params.put("minAge", 25);

ResultSet profileResult = client.profile("MATCH (p:Person) WHERE p.age > $minAge RETURN p.name", params);
```

## Using GraphContext (connection-specific)

```java
try (GraphContext context = client.getContext()) {
    ResultSet profileResult = context.profile("MATCH (p:Person) RETURN p");
}
```

## Using in Pipelines

```java
try (GraphContext context = client.getContext()) {
    GraphPipeline pipeline = context.pipelined();
    pipeline.query("CREATE (:Person {name: 'Charlie'})");
    Response<ResultSet> profileResponse = pipeline.profile("MATCH (p:Person) RETURN count(p)");
    
    List<Object> results = pipeline.syncAndReturnAll();
    ResultSet profileResult = profileResponse.get();
}
```

## Using in Transactions

```java
try (GraphContext context = client.getContext()) {
    GraphTransaction transaction = context.multi();
    transaction.query("CREATE (:Person {name: 'Diana'})");
    Response<ResultSet> profileResponse = transaction.profile("MATCH (p:Person) RETURN count(p)");
    
    List<Object> results = transaction.exec();
    ResultSet profileResult = (ResultSet) results.get(1);
}
```

## Purpose

The `GRAPH.PROFILE` command executes a Cypher query and returns execution plan information augmented with performance metrics for each operation. This is useful for:

- Query optimization and performance analysis
- Understanding query execution paths
- Identifying bottlenecks in complex queries
- Debugging query performance issues

The profiling information is returned as a `ResultSet` containing the execution plan with timing and operation details.