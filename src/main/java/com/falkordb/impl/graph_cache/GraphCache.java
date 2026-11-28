package com.falkordb.impl.graph_cache;

import com.falkordb.Graph;
import com.falkordb.exceptions.GraphException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.Nullable;

/**
 * A class to store a local cache in the client, for a specific graph.
 * Holds the labels, property names and relationship types
 *
 * <p>Compact replies name labels, property keys and relationship types by their position in the lists
 * that {@code db.labels()}, {@code db.propertyKeys()} and {@code db.relationshipTypes()} return. A graph that is
 * deleted and recreated reuses the positions for other names, so a cached name can be stale.
 *
 * <p>FalkorDB 6.2 and later fixes that with the schema hash. A query sends the hash of the names cached when it was
 * sent ({@link #request()}), and the server adds a schema element to the reply: the hash alone when it is still
 * the graph's, and the graph's names too when it isn't. Such a reply is decoded with the names the server confirmed
 * or sent ({@link #decodeWith}), never with a lookup, and names it sent replace the cached ones.
 *
 * <p>A reply without the element, from an older server or to a query sent without the hash, is decoded with the
 * cached names as before: when a reply names a position the cache doesn't hold yet, the whole list is fetched again
 * and replaces the cached one, so entries left over from a graph that was deleted and recreated are replaced too. A
 * cached position is not checked against the server, so until the next refresh or {@link #clear()} it can still
 * give an old name. A position the fresh list doesn't reach either is reported as a {@link GraphException}.
 */
public class GraphCache {

    private static final String NOT_IN_REPLY = "the schema the server sent with the reply has no such id";
    private static final String NOT_CONFIRMED = "the schema the server confirmed for the reply has no such id";

    /** What every {@link FrozenGraphCache} shares in place of state of its own, which it never uses. */
    private static final GraphCacheList UNUSED = new GraphCacheList(SchemaNames.Kind.LABEL);

    private final AtomicReference<SchemaNames> names;
    private final boolean schemaHash;
    private final GraphCacheList labels;
    private final GraphCacheList propertyNames;
    private final GraphCacheList relationshipTypes;

    /**
     * A cache whose queries send the schema hash.
     */
    public GraphCache() {
        this(true);
    }

    /**
     * A cache whose queries send the schema hash, or don't.
     *
     * @param schemaHash whether queries decoded with this cache send the schema hash, which FalkorDB 6.2 and later
     *     answer with the names a reply needs; older servers ignore it
     */
    public GraphCache(boolean schemaHash) {
        this.schemaHash = schemaHash;
        this.names = new AtomicReference<>(SchemaNames.EMPTY);
        ReentrantLock refreshLock = new ReentrantLock();
        this.labels = new GraphCacheList(SchemaNames.Kind.LABEL, names, refreshLock);
        this.propertyNames = new GraphCacheList(SchemaNames.Kind.PROPERTY_KEY, names, refreshLock);
        this.relationshipTypes = new GraphCacheList(SchemaNames.Kind.RELATIONSHIP_TYPE, names, refreshLock);
    }

    /**
     * For {@link FrozenGraphCache}, which overrides every method that would use this cache's own state, so it is
     * given none: a reply decoded with names its schema element sent allocates nothing else.
     */
    GraphCache(@Nullable FrozenGraphCache unused) {
        this.schemaHash = false;
        this.names = new AtomicReference<>(SchemaNames.EMPTY);
        this.labels = UNUSED;
        this.propertyNames = UNUSED;
        this.relationshipTypes = UNUSED;
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
     * Returns the names a query about to be sent sends the hash of, and later decodes a hash-only reply with: the
     * ones cached now. The query sends {@code SCHEMA_HASH} with {@link SchemaNames#hashToSend()}.
     *
     * @return the cached names, or null when this cache's queries send no schema hash
     */
    public @Nullable SchemaNames request() {
        return schemaHash ? names.get() : null;
    }

    /**
     * Returns the cache to decode a reply that carried a schema element with. It never looks a name up:
     *
     * <ul>
     *   <li>An element with names: the reply is decoded with them, since they are the names its ids refer to,
     *       including names the query itself added. They are also offered to this cache (see {@link #offer}).
     *   <li>An element with the hash only, which is the one the query sent: the reply is decoded with the names
     *       the query sent the hash of, whatever this cache holds by now. A query that sent {@code 0} because no
     *       hash was cached gets that answer only from a graph with no names at all.
     * </ul>
     *
     * @param reply the reply's schema element
     * @param sent  the names whose hash the query sent, from {@link #request()}; null when it sent none
     * @return a cache that holds the names to decode the reply with, and reports an id they lack
     * @throws GraphException if the element has the hash only, but it isn't the one the query sent
     */
    public GraphCache decodeWith(SchemaReply reply, @Nullable SchemaNames sent) {
        SchemaNames fromReply = reply.names();
        if (fromReply != null) {
            offer(fromReply);
            return new FrozenGraphCache(fromReply, NOT_IN_REPLY);
        }
        if (sent == null || reply.hash() != sent.hashToSend()) {
            throw new GraphException("The query ran, but its reply can't be decoded: the server sent schema hash "
                    + reply.hash() + " without the schema, and the query sent "
                    + (sent == null ? "no schema hash" : "schema hash " + sent.hashToSend()));
        }
        if (sent.hash() != null) {
            return new FrozenGraphCache(sent, NOT_CONFIRMED);
        }
        SchemaNames empty = new SchemaNames(
                reply.hash(),
                SchemaNames.EMPTY.labels(),
                SchemaNames.EMPTY.relationshipTypes(),
                SchemaNames.EMPTY.propertyKeys());
        offer(empty);
        return new FrozenGraphCache(empty, NOT_CONFIRMED);
    }

    /**
     * Offers names a reply carried, which replace the cached ones unless they have the same hash: the last reply
     * wins. Which reply wins never decides how a reply is decoded, since each is decoded with the names it sent or
     * the ones its query sent the hash of; it only decides whether the next query gets the names again. So a reply
     * that crossed a newer one, or that a replica behind its primary sent, costs at most one more reply with names.
     * (Keeping the cached names when the offered ones are a prefix of them would never recover from a graph
     * recreated with fewer names.)
     *
     * @param offered names a reply carried, with their hash
     */
    void offer(SchemaNames offered) {
        Long hash = offered.hash();
        names.updateAndGet(current -> hash != null && hash.equals(current.hash()) ? current : offered);
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
        return new FrozenGraphCache(names.get(), FrozenGraphCache.DELETED);
    }

    /**
     * Clears the cache
     */
    public void clear() {
        labels.clear();
    }
}
