package com.falkordb.impl.api;

import com.falkordb.ResultSet;
import com.falkordb.impl.graph_cache.GraphCache;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import org.jspecify.annotations.Nullable;
import redis.clients.jedis.Builder;
import redis.clients.jedis.BuilderFactory;
import redis.clients.jedis.Response;
import redis.clients.jedis.exceptions.JedisDataException;

/**
 * How a pipeline or a transaction decodes the graph replies it queued. Jedis builds a reply only when it is
 * read — by {@code Response.get()}, {@code syncAndReturnAll()} or {@code exec()} — which can be long after the
 * command ran, so two things are decided then rather than when the command is queued:
 *
 * <ul>
 *   <li>Which connection looks up a label, property-key or relationship-type id the cache doesn't hold: see
 *       {@link GraphContextImpl#decodeQueued}.
 *   <li>Which cache decodes the reply. Each {@code deleteGraph()} ends a <em>segment</em> of the queue. Once a
 *       delete has run, the replies queued before it can't look ids up any more: the graph that sent them is
 *       gone, and a lookup would find whatever graph has the name by then. So they are decoded with a
 *       {@linkplain GraphCache#snapshot() frozen copy} of the cache, and an id the copy lacks is reported.
 * </ul>
 *
 * <p>When the segment is frozen depends on how the replies are read. {@code sync()} reads them without
 * decoding, then builds each delete's reply in order: a delete that ran freezes the segment it ends with the
 * cache as it is at that point, then clears the live cache; a delete that failed re-opens its segment, which
 * is then decoded like the one after it. {@code syncAndReturnAll()} and {@code exec()} decode every reply in
 * order before any delete's outcome is known, so the segments ended by their batch's deletes are frozen just
 * before: the first with the cache as it is then, any later one with an empty copy, because the graph it was
 * queued against existed only while the batch ran.
 */
final class QueuedReplies {

    private static final GraphCache EMPTY = new GraphCache().snapshot();

    private final BiFunction<List<Object>, GraphCache, ResultSet> decoder;
    private final GraphCache cache;
    private final BooleanSupplier awaitingReplies;

    /** The replies queued since the last {@code deleteGraph()}. */
    private Segment current = new Segment();
    /** The deletes queued since the replies were last read, oldest first. */
    private final List<QueuedDelete> deletes = new ArrayList<>();

    /** The replies queued between two deletes, which share the cache they are decoded with. */
    private final class Segment {
        /** The segment after the delete that ended this one; null while this is the current segment. */
        @Nullable
        Segment next;
        /** Set when the delete that ends the segment has run, or is about to be read with its batch. */
        @Nullable
        GraphCache frozen;
        /** Set when the delete that ended the segment failed: the graph is still the one {@link #next} has. */
        boolean reopened;

        GraphCache cache() {
            Segment segment = this;
            while (segment.reopened && segment.next != null) {
                segment = segment.next;
            }
            GraphCache copy = segment.frozen;
            return copy != null ? copy : cache;
        }
    }

    /** A queued {@code GRAPH.DELETE}: its reply, and the segment it ends. */
    private static final class QueuedDelete {
        final Response<String> reply;
        final Segment ended;

        QueuedDelete(Response<String> reply, Segment ended) {
            this.reply = reply;
            this.ended = ended;
        }
    }

    /** Builds a delete's reply: the delete ran, so it freezes the segment it ended and clears the cache. */
    private final class DeleteBuilder extends Builder<String> {
        /** Null for a delete that ran at once rather than being queued, as after {@code exec()}. */
        @Nullable
        Segment ended;

        @Override
        public String build(Object reply) {
            String statistics = BuilderFactory.STRING.build(reply);
            Segment segment = ended;
            if (segment != null) {
                if (segment.frozen == null) {
                    segment.frozen = cache.snapshot();
                }
                // Only a segment re-opened by a failed delete follows the link, and this delete ran. Dropping it
                // keeps a reply held from an early batch from holding every later segment's copy of the cache.
                segment.next = null;
            }
            cache.clear();
            return statistics;
        }
    }

    /**
     * @param decoder         decodes a graph reply with the given cache
     * @param cache           the graph's live cache
     * @param awaitingReplies whether the pipeline or transaction has sent commands whose replies are still
     *     unread on the connection
     */
    QueuedReplies(
            BiFunction<List<Object>, GraphCache, ResultSet> decoder,
            GraphCache cache,
            BooleanSupplier awaitingReplies) {
        this.decoder = decoder;
        this.cache = cache;
        this.awaitingReplies = awaitingReplies;
    }

    /** Whether commands sent on the connection still await their replies. */
    boolean awaitsReplies() {
        return awaitingReplies.getAsBoolean();
    }

    /**
     * Returns the builder for a graph reply queued now. One builder per command, so that it decodes with the
     * cache of the segment the command was queued in.
     */
    Builder<ResultSet> resultSet() {
        Segment segment = current;
        return new Builder<ResultSet>() {
            @SuppressWarnings("unchecked")
            @Override
            public ResultSet build(Object reply) {
                return decoder.apply((List<Object>) reply, segment.cache());
            }
        };
    }

    /** Returns the builder for a {@code GRAPH.DELETE}; pass the response to {@link #deleteQueued} once queued. */
    Builder<String> delete() {
        return new DeleteBuilder();
    }

    /**
     * Records a queued delete, which ends the current segment. Not for a delete that ran at once.
     *
     * @param reply   the response of a command queued with a {@link #delete()} builder
     * @param builder that builder
     * @return the response
     */
    Response<String> deleteQueued(Response<String> reply, Builder<String> builder) {
        Segment ended = current;
        current = new Segment();
        ended.next = current;
        ((DeleteBuilder) builder).ended = ended;
        deletes.add(new QueuedDelete(reply, ended));
        return reply;
    }

    /**
     * Called just before {@code syncAndReturnAll()} or {@code exec()} reads and decodes the replies. Freezes the
     * segments their deletes end: the first with a copy of the cache as it is now, which is the deleted graph's,
     * and any later one with an empty copy.
     */
    void decodingAll() {
        boolean first = true;
        for (QueuedDelete delete : deletes) {
            if (delete.ended.frozen == null) {
                delete.ended.frozen = first ? cache.snapshot() : EMPTY;
            }
            first = false;
        }
    }

    /**
     * Called after {@code sync()}, which reads the replies without building them. Builds the deletes' replies in
     * order, so a delete that ran clears the cache even if its reply is never read, and re-opens the segment of
     * a delete that failed. The error stays in its response for the caller.
     */
    void synced() {
        for (QueuedDelete delete : deletes) {
            try {
                delete.reply.get();
            } catch (JedisDataException failed) {
                delete.ended.reopened = true;
            }
        }
        deletes.clear();
    }

    /** Called after the replies were decoded ({@code syncAndReturnAll()}, {@code exec()}) or discarded. */
    void done() {
        deletes.clear();
    }
}
