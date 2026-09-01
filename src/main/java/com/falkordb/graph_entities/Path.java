package com.falkordb.graph_entities;

import java.util.List;
import java.util.Objects;

/**
 * This class represents a path in the graph.
 *
 * <p><strong>Node order and edge direction are independent.</strong> {@link #getNodes()} returns the
 * nodes in <em>traversal</em> order — the order the pattern walked them — while each {@link Edge} in
 * {@link #getEdges()} keeps the direction it is <em>stored</em> with in the graph, via
 * {@link Edge#getSource()} and {@link Edge#getDestination()}. For a reverse or undirected match the
 * two deliberately disagree.
 *
 * <p>Given {@code CREATE (a:A)-[:R]->(b:B)}, the undirected query
 * {@code MATCH p = (b:B)-[:R]-(a:A) RETURN p} yields a path whose nodes are {@code [b, a]} while its
 * single edge still reports {@code source = a} and {@code destination = b}. Preserving both is
 * lossless: edges are never reoriented to follow the traversal.
 *
 * <p>Code bridging this class to an API that requires each relationship to be oriented along the
 * traversal (for example the Neo4j driver's {@code Path}/{@code InternalPath}, which rejects a
 * mismatch) must detect the reversed step — the edge whose {@code source} is not the node it is
 * being attached to — and flip its endpoints when converting.
 */
public final class Path {

    private final List<Node> nodes;
    private final List<Edge> edges;

    /**
     * Parametrized constructor
     * @param nodes - List of nodes.
     * @param edges - List of edges.
     */
    public Path(List<Node> nodes, List<Edge> edges) {
        this.nodes = nodes;
        this.edges = edges;
    }

    /**
     * Returns the nodes of the path, in traversal order.
     *
     * <p>This order reflects how the pattern walked the graph and is independent of the direction the
     * path's edges are stored with; see the {@linkplain Path class documentation}.
     *
     * @return List of nodes.
     */
    public List<Node> getNodes() {
        return nodes;
    }

    /**
     * Returns the edges of the path, each retaining its stored direction.
     *
     * <p>An edge's {@link Edge#getSource()} and {@link Edge#getDestination()} are as stored in the
     * graph, so for a reverse or undirected match they need not follow the order of
     * {@link #getNodes()}; see the {@linkplain Path class documentation}.
     *
     * @return List of edges.
     */
    public List<Edge> getEdges() {
        return edges;
    }

    /**
     * Returns the length of the path - number of edges.
     * @return Number of edges.
     */
    public int length() {
        return edges.size();
    }

    /**
     * Return the number of nodes in the path.
     * @return Number of nodes.
     */
    public int nodeCount() {
        return nodes.size();
    }

    /**
     * Returns the first node in the path.
     * @return First nodes in the path.
     * @throws IndexOutOfBoundsException if the path is empty.
     */
    public Node firstNode() {
        return nodes.get(0);
    }

    /**
     * Returns the last node in the path.
     * @return Last nodes in the path.
     * @throws IndexOutOfBoundsException if the path is empty.
     */
    public Node lastNode() {
        return nodes.get(nodes.size() - 1);
    }

    /**
     * Returns a node with specified index in the path.
     * @param index index of the node.
     * @return Node.
     * @throws IndexOutOfBoundsException if the index is out of range
     *         ({@code index < 0 || index >= nodesCount()})
     */
    public Node getNode(int index) {
        return nodes.get(index);
    }

    /**
     * Returns an edge with specified index in the path.
     * @param index index of the edge.
     * @return Edge.
     * @throws IndexOutOfBoundsException if the index is out of range
     *         ({@code index < 0 || index >= length()})
     */
    public Edge getEdge(int index) {
        return edges.get(index);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Path path = (Path) o;
        return Objects.equals(nodes, path.nodes) && Objects.equals(edges, path.edges);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodes, edges);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Path{");
        sb.append("nodes=").append(nodes);
        sb.append(", edges=").append(edges);
        sb.append('}');
        return sb.toString();
    }
}
