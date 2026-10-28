package com.falkordb.impl.graph_cache;

import com.falkordb.Graph;
import com.falkordb.exceptions.GraphException;

/**
 * A class to store a local cache in the client, for a specific graph.
 * Holds the labels, property names and relationship types
 *
 * <p>Compact replies name labels, property keys and relationship types by their position in the lists
 * that {@code db.labels()}, {@code db.propertyKeys()} and {@code db.relationshipTypes()} return. When a
 * reply names a position the cache doesn't hold yet, the whole list is fetched again and replaces the
 * cached one, so entries left over from a graph that was deleted and recreated are replaced too. A
 * cached position is not checked against the server, so until the next refresh or {@link #clear()} it
 * can still give an old name. A position the fresh list doesn't reach either is reported as a
 * {@link GraphException}.
 */
public class GraphCache {

    private final GraphCacheList labels;
    private final GraphCacheList propertyNames;
    private final GraphCacheList relationshipTypes;

    /**
     * Default constructor
     */
    public GraphCache() {
        this.labels = new GraphCacheList("db.labels", "label");
        this.propertyNames = new GraphCacheList("db.propertyKeys", "property key");
        this.relationshipTypes = new GraphCacheList("db.relationshipTypes", "relationship type");
    }

    /**
     * Returns the label for the given index, fetching it from the graph if it is not cached yet.
     *
     * @param index index of label
     * @param graph source graph
     * @return requested label
     * @throws GraphException if the index is negative, or the graph has no label at it even after the
     *     cache is refreshed
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
     * @throws GraphException if the index is negative, or the graph has no relationship type at it even
     *     after the cache is refreshed
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
     * @throws GraphException if the index is negative, or the graph has no property key at it even after
     *     the cache is refreshed
     */
    public String getPropertyName(int index, Graph graph) {
        return propertyNames.getCachedData(index, graph);
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
