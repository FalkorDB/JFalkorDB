package com.falkordb.graph_entities;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

public class PathTest {

    /**
    * Builds and returns a new Node object with the specified ID.
    * 
    * @param id The unique identifier to be set for the new Node
    * @return A new Node object with the specified ID set
    */
    private Node buildNode(int id){
        Node n = new Node();
        n.setId(id);
        return n;
    }

    /**
    * Builds and returns an Edge object with the specified id, source, and destination.
    * 
    * @param id The unique identifier for the edge
    * @param src The source vertex of the edge
    * @param dst The destination vertex of the edge
    * @return A new Edge object with the specified properties
    */    private Edge buildEdge(int id, int src, int dst){
        Edge e = new Edge();
        e.setId(id);
        e.setSource(src);
        e.setDestination(dst);
        return e;
    }

    /**
    * Builds an array of Node objects with a specified size.
    * 
    * @param size The number of Node objects to create in the array
    * @return A List of Node objects with the specified size
    */
    private List<Node> buildNodeArray(int size) {
        return IntStream.range(0, size).mapToObj(i -> buildNode(i)).collect(Collectors.toList());
    }

    /**
    * Builds an array of Edge objects with sequential indices.
    * 
    * @param size The number of Edge objects to create in the array
    * @return A List of Edge objects where each Edge connects consecutive indices
    */
    private List<Edge> buildEdgeArray(int size){
        return IntStream.range(0, size).mapToObj(i -> buildEdge(i, i, i+1)).collect(Collectors.toList());
    }

    /**
    * Builds a Path object with a specified number of nodes.
    * 
    * @param nodeCount The number of nodes to include in the path
    * @return A new Path object consisting of nodes and edges based on the given node count
    */    private Path buildPath(int nodeCount){
        return new Path(buildNodeArray(nodeCount), buildEdgeArray(nodeCount-1));
    }

    /**
     * Tests the behavior of an empty path in a graph structure.
     * 
     * This method verifies that:
     * 1. An empty path has zero length
     * 2. An empty path has zero node count
     * 3. Attempting to access a node at index 0 throws an IndexOutOfBoundsException
     * 4. Attempting to access an edge at index 0 throws an IndexOutOfBoundsException
     * 
     * @throws IndexOutOfBoundsException if attempting to access elements in an empty path
     */
    @Test
    public void testEmptyPath(){
        Path path = buildPath(0);
        assertEquals(0, path.length());
        assertEquals(0, path.nodeCount());
        assertThrows(IndexOutOfBoundsException.class, ()->path.getNode(0));
        assertThrows(IndexOutOfBoundsException.class, ()->path.getEdge(0));
    }

    /**
    * Tests a Path object with a single node.
    * 
    * This method verifies the behavior of a Path object containing only one node.
    * It checks the path length, node count, and node retrieval operations.
    * 
    * @return void
    * 
    * @throws AssertionError if any of the assertions fail
    */
    @Test
    public void testSingleNodePath(){
        Path path = buildPath(1);
        assertEquals(0, path.length());
        assertEquals(1, path.nodeCount());
        Node n = new Node();
        n.setId(0);
        assertEquals(n, path.firstNode());
        assertEquals(n, path.lastNode());
        assertEquals(n, path.getNode(0));
    }

    /**
    * Performs a test on a randomly generated path with varying length.
    * This test method creates a path with a random number of nodes (between 2 and 100),
    * builds the path, and then verifies its structure and properties.
    *
    * The test checks the following:
    * 1. The number of nodes in the path matches the expected count.
    * 2. The edges in the path are correctly generated.
    * 3. Accessing the first edge of the path does not throw an exception.
    *
    * @throws AssertionError if any of the assertions fail
    */
    @Test
    public void testRandomLengthPath(){
        int nodeCount = ThreadLocalRandom.current().nextInt(2, 100 + 1);
        Path path = buildPath(nodeCount);
        assertEquals(buildNodeArray(nodeCount), path.getNodes());
        assertEquals(buildEdgeArray(nodeCount-1), path.getEdges());
        assertDoesNotThrow(()->path.getEdge(0));
    }

    /**
    * Verifies the correctness of the hashCode and equals methods for the Path class.
    * 
    * This test method uses EqualsVerifier to automatically test the implementation
    * of equals(Object) and hashCode() methods in the Path class. EqualsVerifier
    * checks for the general contract of equals and hashCode, including reflexivity,
    * symmetry, transitivity, and consistency.
    *
    * @throws AssertionError if the equals and hashCode methods do not meet the
    *                        contract specified by EqualsVerifier
    */
    @Test
    public void hashCodeEqualTest(){
        EqualsVerifier.forClass(Path.class).verify();
    }
}