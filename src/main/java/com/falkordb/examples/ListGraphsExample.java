package com.falkordb.examples;

import java.util.List;

import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import com.falkordb.GraphContextGenerator;

/**
 * Example demonstrating the GRAPH.LIST command functionality
 */
public class ListGraphsExample {
    
    public static void main(String[] args) {
        // Create a driver instance
        Driver driver = FalkorDB.driver();
        
        try {
            // List all graphs initially
            System.out.println("Initial graphs:");
            List<String> initialGraphs = driver.listGraphs();
            printGraphList(initialGraphs);
            
            // Create a test graph
            System.out.println("\nCreating test graph 'example-graph'...");
            GraphContextGenerator testGraph = driver.graph("example-graph");
            testGraph.query("CREATE (:ExampleNode {name:'test'})");
            
            // List graphs after creation
            System.out.println("\nGraphs after creation:");
            List<String> graphsAfterCreation = driver.listGraphs();
            printGraphList(graphsAfterCreation);
            
            // Delete the test graph
            System.out.println("\nDeleting test graph...");
            testGraph.deleteGraph();
            testGraph.close();
            
            // List graphs after deletion
            System.out.println("\nGraphs after deletion:");
            List<String> graphsAfterDeletion = driver.listGraphs();
            printGraphList(graphsAfterDeletion);
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println("Make sure FalkorDB is running on localhost:6379");
        } finally {
            try {
                driver.close();
            } catch (Exception e) {
                System.err.println("Error closing driver: " + e.getMessage());
            }
        }
    }
    
    private static void printGraphList(List<String> graphs) {
        if (graphs.isEmpty()) {
            System.out.println("  No graphs found");
        } else {
            System.out.println("  Found " + graphs.size() + " graph(s):");
            for (String graph : graphs) {
                System.out.println("    - " + graph);
            }
        }
    }
}