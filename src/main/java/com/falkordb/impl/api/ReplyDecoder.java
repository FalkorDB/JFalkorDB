package com.falkordb.impl.api;

import com.falkordb.ResultSet;
import com.falkordb.impl.graph_cache.GraphCache;
import com.falkordb.impl.graph_cache.SchemaNames;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Decodes a graph reply that a pipeline or a transaction queued. */
@FunctionalInterface
interface ReplyDecoder {

    /**
     * Decodes a reply.
     *
     * @param reply the raw reply
     * @param cache the cache to look up ids with, when the reply carries no schema element
     * @param sent  the names whose hash the query sent as {@code SCHEMA_HASH}, or null when it sent none
     * @return the result set
     */
    ResultSet decode(List<Object> reply, GraphCache cache, @Nullable SchemaNames sent);
}
