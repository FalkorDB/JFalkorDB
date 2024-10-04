package com.falkordb.test.utils;

import com.falkordb.graph_entities.Edge;
import com.falkordb.graph_entities.Node;
import com.falkordb.graph_entities.Path;

import java.util.ArrayList;
import java.util.List;

public final class PathBuilder{
    private final List<Node> nodes;
    private final List<Edge> edges;
    private Class<?> currentAppendClass = Node.class;

    public PathBuilder() {
        this(0);
    }

    public PathBuilder(int nodesCount){
        this.nodes = new ArrayList<>(nodesCount);
        this.edges = new ArrayList<>(nodesCount > 0 ? nodesCount - 1 : 0);
    }

    /**
     * Appends an object to the path builder.
     * 
     * @param object The object to append, must be of the expected class type (Node or Edge)
     * @return The updated PathBuilder instance
     * @throws IllegalArgumentException if the object's class does not match the expected class
     */
    public PathBuilder append(Object object){
        Class<? extends Object> c = object.getClass();
        if(!currentAppendClass.equals(c)){
             throw new IllegalArgumentException("Path Builder expected " + currentAppendClass.getSimpleName() + " but was " + c.getSimpleName());
        }
        if(c.equals(Node.class)) {
            return appendNode((Node)object);
        }
        return appendEdge((Edge)object);
    }

    /**
     * Appends an edge to the path and updates the current append class.
     *
     * @param edge The Edge object to be added to the path
     * @return The current PathBuilder instance for method chaining
     */
    /**
     * Appends a node to the path and updates the current append class.
     * 
     * @param node The Node object to be added to the path
     * @return The current PathBuilder instance for method chaining
     */
    /**
    * Builds and returns a Path object based on the collected nodes and edges.
    *
    * @return A new Path object constructed from the collected nodes and edges
    * @throws IllegalArgumentException if the number of nodes is not equal to the number of edges plus one
    */
    private PathBuilder appendEdge(Edge edge) {
        edges.add(edge);
        currentAppendClass = Node.class;
        return this;
    }

    private PathBuilder appendNode(Node node){
        nodes.add(node);
        currentAppendClass = Edge.class;
        return this;
    }

    public Path build(){
        if(nodes.size() != edges.size() + 1){
             throw new IllegalArgumentException("Path builder nodes count should be edge count + 1");
        }
        return new Path(nodes, edges);
    }
}
