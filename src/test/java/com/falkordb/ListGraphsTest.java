package com.falkordb;

import com.falkordb.test.BaseTestContainerTestIT;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

public class ListGraphsTest extends BaseTestContainerTestIT {

    private Driver driver;

    @BeforeEach
    public void setUp() {
        driver = FalkorDB.driver(getFalkordbHost(), getFalkordbPort());
    }

    @Test
    public void testListGraphsEmpty() {
        // Initially there might be no graphs or some existing ones
        List<String> graphs = driver.listGraphs();
        Assertions.assertNotNull(graphs);
        // We can't assert the exact size as there might be existing graphs
        // but we can test that the method returns a valid list
    }

    @Test
    public void testListGraphsWithCreatedGraph() {
        // Create a test graph
        GraphContextGenerator testGraph = driver.graph("test-list-graph");
        testGraph.query("CREATE (:TestNode {name:'test'})");

        try {
            // List graphs and verify our graph is included
            List<String> graphs = driver.listGraphs();
            Assertions.assertNotNull(graphs);
            Assertions.assertTrue(graphs.contains("test-list-graph"),
                    "Graph list should contain the created graph: " + graphs);
        } finally {
            // Clean up the test graph
            testGraph.deleteGraph();
            testGraph.close();
        }
    }

    @Test
    public void testListGraphsAfterDeletion() {
        // Create a test graph
        GraphContextGenerator testGraph = driver.graph("test-delete-graph");
        testGraph.query("CREATE (:TestNode {name:'test'})");

        // Verify graph exists
        List<String> graphsBefore = driver.listGraphs();
        Assertions.assertTrue(graphsBefore.contains("test-delete-graph"));

        // Delete the graph
        testGraph.deleteGraph();
        testGraph.close();

        // Verify graph is no longer in the list
        List<String> graphsAfter = driver.listGraphs();
        Assertions.assertFalse(graphsAfter.contains("test-delete-graph"),
                "Graph list should not contain the deleted graph: " + graphsAfter);
    }

    @Test
    public void testListGraphsMultiple() {
        GraphContextGenerator graph1 = driver.graph("test-multi-1");
        GraphContextGenerator graph2 = driver.graph("test-multi-2");

        try {
            // Create nodes in both graphs
            graph1.query("CREATE (:TestNode {name:'test1'})");
            graph2.query("CREATE (:TestNode {name:'test2'})");

            // List graphs and verify both are included
            List<String> graphs = driver.listGraphs();
            Assertions.assertNotNull(graphs);
            Assertions.assertTrue(graphs.contains("test-multi-1"),
                    "Graph list should contain test-multi-1: " + graphs);
            Assertions.assertTrue(graphs.contains("test-multi-2"),
                    "Graph list should contain test-multi-2: " + graphs);
        } finally {
            // Clean up both test graphs
            graph1.deleteGraph();
            graph1.close();
            graph2.deleteGraph();
            graph2.close();
        }
    }
}