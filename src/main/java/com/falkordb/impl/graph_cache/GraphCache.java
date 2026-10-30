package com.falkordb.impl.graph_cache;

import com.falkordb.Graph;

/**
 * A class to store a local cache in the client, for a specific graph.
 * Holds the labels, property names and relationship types
 */
public class GraphCache {

    private final GraphCacheList labels;
    private final GraphCacheList propertyNames;
    private final GraphCacheList relationshipTypes;

    /**
     * Default constructor
     */
    public GraphCache() {
        this.labels = new GraphCacheList("db.labels");
        this.propertyNames = new GraphCacheList("db.propertyKeys");
        this.relationshipTypes = new GraphCacheList("db.relationshipTypes");
    }

    /**
     * Returns the label for the given index, fetching it from the graph if it is not cached yet.
     *
     * @param index index of label
     * @param graph source graph
     * @return requested label
     */
    public String getLabel(int index, Graph graph) {
        return labels.getCachedData(index, graph);
    }

    /**
     * Returns the relationship type for the given index, fetching it from the graph if it is not
     * cached yet.
     *
     * @param index index of the relationship type
     * @param graph source graph
     * @return requested relationship type
     */
    public String getRelationshipType(int index, Graph graph) {
        return relationshipTypes.getCachedData(index, graph);
    }

    /**
     * Returns the property name for the given index, fetching it from the graph if it is not cached
     * yet.
     *
     * @param index index of property name
     * @param graph source graph
     * @return requested property
     */
    public String getPropertyName(int index, Graph graph) {
        return propertyNames.getCachedData(index, graph);
    }

    /**
     * Returns a copy of what this cache holds now, which never asks the server: an id the copy lacks is
     * reported as a {@link com.falkordb.exceptions.GraphException} instead of being looked up. A pipeline or
     * transaction decodes the replies it queued before {@code deleteGraph()} with such a copy, because by the
     * time they are read the graph is gone, or is a new graph with other ids. A copy of an empty cache knows
     * no ids at all.
     *
     * @return the copy
     */
    public GraphCache snapshot() {
        return new FrozenGraphCache(labels.snapshot(), propertyNames.snapshot(), relationshipTypes.snapshot());
    }

    /**
     * Clears the cache
     */
    public void clear() {
        labels.clear();
        propertyNames.clear();
        relationshipTypes.clear();
    }
}
