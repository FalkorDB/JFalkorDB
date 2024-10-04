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
     * Builds and initializes a new Node object with the given id.
     *
     * @param id The unique identifier for the Node
     * @return A new Node object with the specified id
     */
    private Node buildNode(int id){
        Node n = new Node();
        n.setId(id);
        return n;
    }

    /**
     * Builds and returns an Edge object with the specified parameters.
     *
     * @param id The unique identifier for the edge
     * @param src The source vertex of the edge
     * @param dst The destination vertex of the edge
     * @return A new Edge object with the given id, source, and destination
     */
    private Edge buildEdge(int id, int src, int dst){
        Edge e = new Edge();
        e.setId(id);
        e.setSource(src);
        e.setDestination(dst);
        return e;
    }

    /**
    * Builds an array of Node objects with sequential indices.
    * 
    * @param size The number of Node objects to create in the array
    * @return A List of Node objects with indices from 0 to size-1
    */
    private List<Node> buildNodeArray(int size) {
        return IntStream.range(0, size).mapToObj(i -> buildNode(i)).collect(Collectors.toList());
    /**
     * Builds and returns a Path object with the specified number of nodes.
     *
     * @param nodeCount The number of nodes to include in the path
     * @return A new Path object containing the constructed node and edge arrays
     */
    }

    /**
    * Builds an array of Edge objects with sequential connections.
    * 
    * @param size The number of edges to create in the array
    * @return A List of Edge objects representing a sequence of connected edges
    */
    private List<Edge> buildEdgeArray(int size){
        return IntStream.range(0, size).mapToObj(i -> buildEdge(i, i, i+1)).collect(Collectors.toList());
    }

    private Path buildPath(int nodeCount){
        return new Path(buildNodeArray(nodeCount), buildEdgeArray(nodeCount-1));
    }

    /**
     * Tests the behavior of an empty path in the graph structure.
     * 
     * @return void This method doesn't return anything
     */
    @Test
    public void testEmptyPath(){
        Path path = buildPath(0);
        assertEquals(0, path.length());
        /**
         * Tests the creation of a random length path.
         *
         * This test method verifies the functionality of building a path with a random number of nodes
         * between 2 and 100 (inclusive). It checks if the created path has the correct number of nodes
         * and edges, and ensures that accessing the first edge does not throw an exception.
         *
         * @return void This method doesn't return anything
         */        assertEquals(0, path.nodeCount());
        assertThrows(IndexOutOfBoundsException.class, ()->path.getNode(0));
        assertThrows(IndexOutOfBoundsException.class, ()->path.getEdge(0));
    }

    /**
    * Tests the behavior of a Path object with a single node.
    * 
    * @param None
    /**
     * Tests the hashCode and equals methods of the Path class using EqualsVerifier.
     * 
     * This test method verifies that the hashCode and equals methods of the Path class
     * are implemented correctly and follow the contract for object equality.
     * 
     * @return void This method doesn't return anything
     */
    * @return None
    */    @Test
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

    @Test
    public void testRandomLengthPath(){
        int nodeCount = ThreadLocalRandom.current().nextInt(2, 100 + 1);
        Path path = buildPath(nodeCount);
        assertEquals(buildNodeArray(nodeCount), path.getNodes());
        assertEquals(buildEdgeArray(nodeCount-1), path.getEdges());
        assertDoesNotThrow(()->path.getEdge(0));
    }

    @Test
    public void hashCodeEqualTest(){
        EqualsVerifier.forClass(Path.class).verify();
    }
}