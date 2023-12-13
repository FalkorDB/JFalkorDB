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
