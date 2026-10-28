package com.falkordb.impl.api;

import com.falkordb.ResultSet;
import java.util.List;

/**
 * A graph that refuses every query. Decoding a reply with it shows whether the reply needs a schema lookup: if it
 * does, decoding stops with {@link Needed} before anything is sent, and no cache lock is held by then, since the
 * cache takes its lock only around the lookup itself.
 */
final class NoLookups extends AbstractGraph {

    static final NoLookups GRAPH = new NoLookups();

    /** Thrown instead of looking an id up. Carries no stack trace: it is caught right away. */
    static final class Needed extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Needed() {
            super("a schema lookup is needed", null, false, false);
        }
    }

    private NoLookups() {}

    @Override
    protected ResultSet sendQuery(String preparedQuery) {
        throw new Needed();
    }

    @Override
    protected ResultSet sendReadOnlyQuery(String preparedQuery) {
        throw new Needed();
    }

    @Override
    protected ResultSet sendQuery(String preparedQuery, long timeout) {
        throw new Needed();
    }

    @Override
    protected ResultSet sendReadOnlyQuery(String preparedQuery, long timeout) {
        throw new Needed();
    }

    @Override
    protected ResultSet sendProfile(String preparedQuery) {
        throw new Needed();
    }

    @Override
    protected List<String> sendExplain(String preparedQuery) {
        throw new Needed();
    }

    @Override
    public String copyGraph(String destinationGraphId) {
        throw new Needed();
    }

    @Override
    public String deleteGraph() {
        throw new Needed();
    }

    @Override
    public void close() {}
}
