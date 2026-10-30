package com.falkordb.impl.api;

import com.falkordb.ResultSet;
import com.falkordb.impl.graph_cache.GraphCache;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
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
 *   <li>Which cache decodes the reply. Each {@code deleteGraph()} ends a <em>segment</em> of the queue. Once the
 *       delete can have run, the replies queued before it can't look ids up any more: the graph that sent them
 *       may be gone, and a lookup would find whatever graph has the name by then. So they are decoded with a
 *       {@linkplain GraphCache#snapshot() frozen copy} of the cache, and an id the copy lacks is reported.
 * </ul>
 *
 * <p>The copy is taken when the delete is queued, the last moment the delete is sure not to have run: Jedis may
 * send it to the server long before the replies are read, and from then on the shared cache can learn the names
 * of a graph recreated under the same name, from this pipeline or from any other context of the graph. A segment
 * queued after another delete that hasn't run yet belongs to a graph the client has never read, so its copy is
 * empty. When the replies have been read, a delete that turns out to have failed, or a transaction that was
 * discarded or aborted, re-opens the segments of the deletes that didn't run.
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
        /** The copy taken when the delete that ends the segment was queued; null for the current segment. */
        @Nullable
        GraphCache frozen;
        /**
         * Set when the delete that ended the segment didn't run: the segment queued after it, whose graph is still the
         * one this segment's replies came from. Only such a segment links to a later one, so a reply held from an
         * early batch doesn't hold every later segment's copy.
         */
        @Nullable
        Segment reopenedInto;

        /**
         * The cache this segment's replies are decoded with: the copy taken when the delete that ends the segment
         * was queued, or the live cache for the current segment. A segment re-opened because its delete didn't run
         * is decoded like the segment it was re-opened into.
         */
        GraphCache cache() {
            Segment segment = this;
            for (Segment next = segment.reopenedInto; next != null; next = segment.reopenedInto) {
                segment = next;
            }
            GraphCache copy = segment.frozen;
            return copy != null ? copy : cache;
        }
    }

    /** A queued {@code GRAPH.DELETE}: its reply, the segment it ends, and the segment queued after it. */
    private static final class QueuedDelete {
        final Response<String> reply;
        final Segment ended;
        final Segment next;

        QueuedDelete(Response<String> reply, Segment ended, Segment next) {
            this.reply = reply;
            this.ended = ended;
            this.next = next;
        }

        /** The delete didn't run: the replies of the segment it ended come from the graph the next segment has. */
        void didNotRun() {
            ended.reopenedInto = next;
            if (next.frozen == EMPTY) {
                // The next segment got an empty copy because this delete was due to run before it.
                next.frozen = ended.frozen;
            }
        }
    }

    /** Builds a delete's reply: the delete ran, so it clears the live cache. */
    private final class DeleteBuilder extends Builder<String> {
        @Override
        public String build(Object reply) {
            String statistics = BuilderFactory.STRING.build(reply);
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

    /**
     * Queues a {@code GRAPH.DELETE}, which ends the current segment. The segment is frozen before the delete is sent,
     * the last moment the delete is sure not to have run.
     *
     * <p>Jedis writes a command before it records its response, so a delete whose send failed may still have reached
     * the server and run. Its segment then stays frozen, and the live cache is cleared.
     *
     * @param send queues the delete with the builder it is given, and returns its response
     * @return the response
     */
    Response<String> queueDelete(Function<Builder<String>, Response<String>> send) {
        Segment ended = current;
        ended.frozen = deletes.isEmpty() ? cache.snapshot() : EMPTY;
        current = new Segment();
        Response<String> reply = runDelete(send);
        deletes.add(new QueuedDelete(reply, ended, current));
        return reply;
    }

    /**
     * Sends a {@code GRAPH.DELETE} that isn't queued, as a transaction does after {@code exec()}: its builder clears
     * the live cache once it ran. So does a failed send, after which the delete may have run. A delete the server
     * rejects clears nothing: Jedis keeps the error in the response without building it.
     *
     * @param send sends the delete with the builder it is given, and returns its response
     * @return the response
     */
    Response<String> runDelete(Function<Builder<String>, Response<String>> send) {
        try {
            return send.apply(new DeleteBuilder());
        } catch (RuntimeException e) {
            cache.clear();
            throw e;
        }
    }

    /**
     * Called once the replies have been read: by {@code sync()}, which leaves them unbuilt, or by
     * {@code syncAndReturnAll()} or {@code exec()}, which have built them. Builds each delete's reply, so a
     * delete that ran clears the cache even if its reply is never read, and re-opens the segment of a delete that
     * failed. The error stays in its response for the caller.
     */
    void read() {
        for (QueuedDelete delete : deletes) {
            try {
                delete.reply.get();
            } catch (JedisDataException failed) {
                delete.didNotRun();
            }
        }
        deletes.clear();
    }

    /** Called after a transaction was discarded or aborted: none of its deletes ran. */
    void discarded() {
        for (QueuedDelete delete : deletes) {
            delete.didNotRun();
        }
        deletes.clear();
    }

    /**
     * Called when reading the replies failed part way, so whether the deletes ran is unknown. The live cache is
     * cleared, in case one did; the segments stay frozen.
     */
    void unknown() {
        if (!deletes.isEmpty()) {
            cache.clear();
        }
        deletes.clear();
    }
}
