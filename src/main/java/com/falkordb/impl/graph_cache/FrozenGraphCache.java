package com.falkordb.impl.graph_cache;

import com.falkordb.Graph;
import com.falkordb.exceptions.GraphException;
import java.util.List;

/**
 * A copy of a {@link GraphCache} that never asks the server: an id it doesn't hold is reported instead of
 * looked up. See {@link GraphCache#snapshot()}.
 */
final class FrozenGraphCache extends GraphCache {

    private final List<String> labels;
    private final List<String> propertyNames;
    private final List<String> relationshipTypes;

    FrozenGraphCache(List<String> labels, List<String> propertyNames, List<String> relationshipTypes) {
        this.labels = labels;
        this.propertyNames = propertyNames;
        this.relationshipTypes = relationshipTypes;
    }

    @Override
    public String getLabel(int index, Graph graph) {
        return lookup(labels, index, "label");
    }

    @Override
    public String getRelationshipType(int index, Graph graph) {
        return lookup(relationshipTypes, index, "relationship type");
    }

    @Override
    public String getPropertyName(int index, Graph graph) {
        return lookup(propertyNames, index, "property key");
    }

    /** A copy never changes, so it is its own snapshot. */
    @Override
    public GraphCache snapshot() {
        return this;
    }

    /** A copy is never refreshed, so there is nothing to clear. */
    @Override
    public void clear() {}

    /** Returns the name with the given id, or throws a {@link GraphException} for an id the copy doesn't hold. */
    private static String lookup(List<String> entries, int index, String entryName) {
        if (index < 0 || index >= entries.size()) {
            throw new GraphException("Cannot decode " + entryName + " id " + index + ": deleteGraph() was queued after"
                    + " this command in the same pipeline or transaction, so the id can no longer be looked up, and the"
                    + " client had not cached it");
        }
        return entries.get(index);
    }
}
