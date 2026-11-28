package com.falkordb.impl.graph_cache;

import com.falkordb.Graph;
import org.jspecify.annotations.Nullable;

/**
 * Names that never change and are never looked up: an id they don't hold is reported instead. Used for a copy of a
 * {@link GraphCache} (see {@link GraphCache#snapshot()}), and for the names a reply's schema element sent or
 * confirmed (see {@link GraphCache#decodeWith}).
 */
final class FrozenGraphCache extends GraphCache {

    /** Why a copy taken by {@link GraphCache#snapshot()} lacks an id. */
    static final String DELETED = "deleteGraph() was queued after this command in the same pipeline or transaction,"
            + " so the id can no longer be looked up, and the client had not cached it";

    private final SchemaNames names;
    private final String missing;

    /**
     * @param names   the names
     * @param missing why an id they lack is missing, for the error
     */
    FrozenGraphCache(SchemaNames names, String missing) {
        super(GraphCache.UNUSED);
        this.names = names;
        this.missing = missing;
    }

    @Override
    public String getLabel(int index, Graph graph) {
        return names.name(SchemaNames.Kind.LABEL, index, missing);
    }

    @Override
    public String getRelationshipType(int index, Graph graph) {
        return names.name(SchemaNames.Kind.RELATIONSHIP_TYPE, index, missing);
    }

    @Override
    public String getPropertyName(int index, Graph graph) {
        return names.name(SchemaNames.Kind.PROPERTY_KEY, index, missing);
    }

    /** A copy is only ever decoded with, never sent with. */
    @Override
    public @Nullable SchemaNames request() {
        return null;
    }

    /**
     * A copy never changes. Names a reply decoded with a copy carried aren't kept: the copy decodes the replies
     * queued before a {@code deleteGraph()}, so they are the deleted graph's.
     */
    @Override
    void offer(SchemaNames offered) {}

    /** A copy never changes, so it is its own snapshot. */
    @Override
    public GraphCache snapshot() {
        return this;
    }

    /** A copy is never refreshed, so there is nothing to clear. */
    @Override
    public void clear() {}
}
