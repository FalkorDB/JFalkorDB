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
     * Appends an object to the path builder, ensuring type consistency.
     * 
     * @param object The object to append, must be of the expected class type (Node or Edge)
     * @return The PathBuilder instance for method chaining
     * @throws IllegalArgumentException if the provided object is not of the expected class type
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
    * Appends an edge to the current path and updates the current append class.
    * 
    * @param edge The Edge object to be appended to the path
    * @return The current PathBuilder instance for method chaining
    */
    private PathBuilder appendEdge(Edge edge) {
        edges.add(edge);
        currentAppendClass = Node.class;
        return this;
    }

    /**
    * Appends a node to the list of nodes and updates the current append class.
    * 
    * @param node The Node object to be appended to the list of nodes
    * @return The current PathBuilder instance for method chaining
    */    private PathBuilder appendNode(Node node){
        nodes.add(node);
        currentAppendClass = Edge.class;
        return this;
    }

    /**
    * Builds and returns a Path object based on the nodes and edges set in the builder.
    * 
    * @throws IllegalArgumentException if the number of nodes is not equal to the number of edges plus one
    * @return a new Path object constructed from the builder's nodes and edges
    */
    public Path build(){
        if(nodes.size() != edges.size() + 1){
             throw new IllegalArgumentException("Path builder nodes count should be edge count + 1");
        }
        return new Path(nodes, edges);
    }
}
