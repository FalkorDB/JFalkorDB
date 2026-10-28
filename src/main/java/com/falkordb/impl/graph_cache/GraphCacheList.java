package com.falkordb.impl.graph_cache;

import com.falkordb.Graph;
import com.falkordb.Record;
import com.falkordb.ResultSet;
import com.falkordb.exceptions.GraphException;
import com.falkordb.impl.Utils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Represents a local cache of list of strings. Holds data from a specific procedure, for a specific graph.
 *
 * <p>The list is never edited in place: a refresh or a {@link #clear()} replaces it as a whole. A reader
 * takes one snapshot and uses it for both the bounds check and the lookup, so it never sees a list that
 * a concurrent refresh or clear has half-replaced.
 */
class GraphCacheList {

    private final String procedure;
    private final String entryName;
    // Replaced, never mutated: written only under refreshLock, read without it.
    private volatile List<String> data = Collections.emptyList();
    // Serializes refreshes and clears; readers take no lock.
    // This is a ReentrantLock rather than a `synchronized` block on purpose: refreshing the cache
    // issues a blocking query (getProcedureInfo -> graph.readOnlyQuery), and on JDK 21-23 a
    // `synchronized` monitor held across a blocking call PINS the carrier thread, so many concurrent
    // queries on virtual threads would not scale. A ReentrantLock held across the same blocking call
    // does not pin. (It is still reentrant, matching `synchronized`, so a refresh that recursively
    // touches this cache on the same thread is safe.) Note: cold connection creation inside
    // commons-pool2's GenericObjectPool.create() is itself `synchronized` and can still capture a
    // carrier under load — that is upstream; mitigate by warming the pool (see the Wave-5 docs).
    private final ReentrantLock refreshLock = new ReentrantLock();

    /**
     * @param procedure - exact procedure command
     * @param entryName - what an entry is, such as "label", for error messages
     */
    public GraphCacheList(String procedure, String entryName) {
        this.procedure = procedure;
        this.entryName = entryName;
    }

    /**
     * A method to return a cached item if it is in the cache, or re-validate the cache if its invalidated
     * @param index index of data item
     * @return The string value of the specific procedure response, at the given index.
     * @throws GraphException if the index is negative, or the procedure lists no entry at it even after a
     *     refresh, for instance because the graph was deleted or recreated after the reply was sent
     */
    public String getCachedData(int index, Graph graph) {
        if (index < 0) {
            throw new GraphException("Invalid " + entryName + " id " + index);
        }
        List<String> snapshot = data;
        if (index >= snapshot.size()) {
            snapshot = refresh(index, graph);
        }
        return snapshot.get(index);
    }

    /**
     * Fetches the whole list again, unless another thread already fetched one that has the index.
     *
     * @return a snapshot that has the index
     * @throws GraphException if the fresh list does not reach the index either
     */
    private List<String> refresh(int index, Graph graph) {
        List<String> snapshot;
        refreshLock.lock();
        try {
            snapshot = data;
            if (index >= snapshot.size()) {
                snapshot = getProcedureInfo(graph);
                data = snapshot;
            }
        } finally {
            refreshLock.unlock();
        }
        if (index >= snapshot.size()) {
            throw new GraphException("Unknown " + entryName + " id " + index + ": " + procedure + "() lists "
                    + snapshot.size() + ", so the graph was probably deleted or recreated after the reply was sent");
        }
        return snapshot;
    }

    /**
     * Auxiliary method to fetch the procedure's whole current list.
     *
     * The procedure is issued as a read-only query rather than through
     * {@code callProcedure}, which routes to GRAPH.QUERY. GRAPH.QUERY is a write
     * command, so a replica rejects it with READONLY. Because cache warming is
     * triggered while decoding a compact response that contains a node, an edge
     * or a path, sending it as a write turned every entity returning
     * {@code readOnlyQuery} into a failure against a replica.
     *
     * db.labels, db.relationshipTypes and db.propertyKeys are all read-only, so
     * GRAPH.RO_QUERY serves them on a primary and on a replica alike.
     *
     * Every entry is replaced, not only the new ones. Within one graph the server only appends to these
     * lists, so a refresh normally just extends the cached one. When the graph was deleted and recreated,
     * by another client or by another handle with its own cache, the old entries no longer apply, and
     * keeping them would decode the new graph's ids into the old graph's names. The price is the opposite
     * case: a reply the old graph sent, decoded after the refresh, gets the new graph's names for the ids
     * both graphs have.
     *
     * @return the list, unmodifiable
     */
    private List<String> getProcedureInfo(Graph graph) {
        String preparedProcedure = Utils.prepareProcedure(procedure, Utils.DUMMY_LIST, Utils.DUMMY_MAP);
        ResultSet resultSet = graph.readOnlyQuery(preparedProcedure);
        List<String> fresh = new ArrayList<>(resultSet.size());
        for (Record record : resultSet) {
            fresh.add(record.getString(0));
        }
        return Collections.unmodifiableList(fresh);
    }

    public void clear() {
        // Serialize with an in-flight refresh, so a refresh that fetched the list before the clear can't
        // publish it after the clear.
        refreshLock.lock();
        try {
            data = Collections.emptyList();
        } finally {
            refreshLock.unlock();
        }
    }
}
