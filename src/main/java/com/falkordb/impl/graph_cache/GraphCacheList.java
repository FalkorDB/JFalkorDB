package com.falkordb.impl.graph_cache;

import com.falkordb.Graph;
import com.falkordb.Record;
import com.falkordb.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Represents a local cache of list of strings. Holds data from a specific procedure, for a specific graph.
 */
class GraphCacheList {

    private final String procedure;
    private final List<String> data = new CopyOnWriteArrayList<>();
    // Serializes cache refresh; don't synchronize on `data` itself (it's a concurrent collection).
    // This is a ReentrantLock rather than a `synchronized` block on purpose: refreshing the cache
    // issues a blocking query (getProcedureInfo -> graph.callProcedure), and on JDK 21-23 a
    // `synchronized` monitor held across a blocking call PINS the carrier thread, so many concurrent
    // queries on virtual threads would not scale. A ReentrantLock held across the same blocking call
    // does not pin. (It is still reentrant, matching `synchronized`, so a refresh that recursively
    // touches this cache on the same thread is safe.) Note: cold connection creation inside
    // commons-pool2's GenericObjectPool.create() is itself `synchronized` and can still capture a
    // carrier under load — that is upstream; mitigate by warming the pool (see the Wave-5 docs).
    private final ReentrantLock refreshLock = new ReentrantLock();

    /**
     * @param procedure - exact procedure command
     */
    public GraphCacheList(String procedure) {
        this.procedure = procedure;
    }

    /**
     * A method to return a cached item if it is in the cache, or re-validate the cache if its invalidated
     * @param index index of data item
     * @return The string value of the specific procedure response, at the given index.
     */
    public String getCachedData(int index, Graph graph) {
        if (index >= data.size()) {
            refreshLock.lock();
            try {
                if (index >= data.size()) {
                    getProcedureInfo(graph);
                }
            } finally {
                refreshLock.unlock();
            }
        }
        return data.get(index);
    }

    /**
     * Auxiliary method to parse a procedure result set and refresh the cache
     */
    private void getProcedureInfo(Graph graph) {
        ResultSet resultSet = graph.callProcedure(procedure);
        List<String> newData = new ArrayList<>();
        int i = 0;
        for (Record record : resultSet) {
            if (i >= data.size()) {
                newData.add(record.getString(0));
            }
            i++;
        }
        data.addAll(newData);
    }

    public void clear() {
        // Serialize with an in-flight refresh (both guard `data` via refreshLock) so a clear can't
        // interleave with getProcedureInfo()'s append and leave a half-refreshed cache.
        refreshLock.lock();
        try {
            data.clear();
        } finally {
            refreshLock.unlock();
        }
    }
}
