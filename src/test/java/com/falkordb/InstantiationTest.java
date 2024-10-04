package com.falkordb;

import java.net.URI;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class InstantiationTest {
    private GraphContextGenerator client;

    ```
    /**
     * Creates a default FalkorDB client and executes a simple graph query.
     * 
     * This test method initializes a FalkorDB client for a graph named "g",
     * creates a new node with a 'name' property, and verifies the creation.
     * 
     * @return void
     * @throws RuntimeException if the FalkorDB operation fails
     */
    ```    @Test
    public void createDefaultClient() {
        client = FalkorDB.driver().graph("g");
        ResultSet resultSet = client.query("CREATE ({name:'bsb'})");
        Assert.assertEquals(1, resultSet.getStatistics().nodesCreated());
    }

    /**
     * Tests the creation of a FalkorDB client with specified host and port.
     * 
     * This test method creates a FalkorDB client, connects to a graph named "g",
     * executes a Cypher query to create a node, and verifies the result.
     * 
     * @return void This method doesn't return anything
     */    @Test
    public void createClientWithHostAndPort() {
        client = FalkorDB.driver("localhost", 6379).graph("g");
        ResultSet resultSet = client.query("CREATE ({name:'bsb'})");
        Assert.assertEquals(1, resultSet.getStatistics().nodesCreated());
    }

    ```
    /**
     * Tests the creation of a FalkorDB client with host and port without user credentials.
     * 
     * This test method creates a FalkorDB client connection to a local instance,
     * executes a simple graph query to create a node, and verifies the result.
     * 
     * @return void This method doesn't return anything
     */
    ```
    @Test
    public void createClientWithHostAndPortNoUser() {
        client = FalkorDB.driver("localhost", 6379, null, null).graph("g");
        ResultSet resultSet = client.query("CREATE ({name:'bsb'})");
        Assert.assertEquals(1, resultSet.getStatistics().nodesCreated());
    }

    /**
    * Creates a FalkorDB client with a specified URL and performs a test query.
    * 
    * @param None
    * @return None (void method)
    */    @Test
    public void createClientWithURL() {
        /**
         * Closes the client and deletes the associated graph after test execution.
         * 
         * This method is annotated with @After, indicating it runs after each test method.
         * It checks if the client is not null, then deletes the graph and closes the client.
         * 
         * @return void
         */
        client = FalkorDB.driver(URI.create("redis://localhost:6379")).graph("g");
        ResultSet resultSet = client.query("CREATE ({name:'bsb'})");
        Assert.assertEquals(1, resultSet.getStatistics().nodesCreated());
    }

    @After
    public void closeClient() {
        if (client != null) {
            client.deleteGraph();
            client.close();
        }
    }
}
