package com.falkordb;

import java.net.URI;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class InstantiationTest {
    private GraphContextGenerator client;

    /**
     * Creates a default FalkorDB client and executes a simple query to create a node.
     * This test method demonstrates the basic usage of FalkorDB driver to connect
     * to a graph database, execute a query, and verify the result.
     *
     * @throws RuntimeException if there's an error connecting to the database or executing the query
     * @return void
     */
    @Test
    public void createDefaultClient() {
        client = FalkorDB.driver().graph("g");
        ResultSet resultSet = client.query("CREATE ({name:'bsb'})");
        Assert.assertEquals(1, resultSet.getStatistics().nodesCreated());
    }

    /**
    * Creates a FalkorDB client with specified host and port, and performs a test query.
    * 
    * This method initializes a FalkorDB client connected to 'localhost' on port 6379,
    * creates a graph named 'g', and executes a query to create a node with a 'name' property.
    * It then asserts that one node was created as a result of the query.
    * 
    * @return void
    * @throws AssertionError if the number of nodes created is not equal to 1
    */
    @Test
    public void createClientWithHostAndPort() {
        client = FalkorDB.driver("localhost", 6379).graph("g");
        ResultSet resultSet = client.query("CREATE ({name:'bsb'})");
        Assert.assertEquals(1, resultSet.getStatistics().nodesCreated());
    }

    /**
    * Creates a FalkorDB client with host and port, but without user credentials.
    * This method initializes a FalkorDB client, connects to a graph named "g",
    * and executes a query to create a node.
    * 
    * @return void
    * @throws AssertionError if the number of nodes created is not 1
    */
    @Test
    public void createClientWithHostAndPortNoUser() {
        client = FalkorDB.driver("localhost", 6379, null, null).graph("g");
        ResultSet resultSet = client.query("CREATE ({name:'bsb'})");
        Assert.assertEquals(1, resultSet.getStatistics().nodesCreated());
    }

    /**
    * Creates a FalkorDB client with a specified URL and performs a test query.
    * 
    * This method initializes a FalkorDB client using a Redis URL, creates a graph named "g",
    * and executes a query to create a node with a 'name' property. It then verifies that
    * exactly one node was created.
    * 
    * @throws URISyntaxException If the provided URL is invalid
    * @throws FalkorDBException If there's an error connecting to or querying the database
    */
    @Test
    public void createClientWithURL() {
        client = FalkorDB.driver(URI.create("redis://localhost:6379")).graph("g");
        ResultSet resultSet = client.query("CREATE ({name:'bsb'})");
        Assert.assertEquals(1, resultSet.getStatistics().nodesCreated());
    }

    /**
    * Closes the client and deletes the associated graph.
    * This method is annotated with @After, indicating it should be executed after test methods.
    * If the client is not null, it first deletes the graph and then closes the client.
    *
    * @throws RuntimeException if an error occurs while deleting the graph or closing the client
    */
    @After
    public void closeClient() {
        if (client != null) {
            client.deleteGraph();
            client.close();
        }
    }
}
