package com.falkordb.impl.graph_cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.falkordb.Graph;
import com.falkordb.exceptions.GraphException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** How a graph's cache uses the schema element of a FalkorDB 6.2+ reply. */
class GraphCacheTest {

    /** The hash-path never asks a graph, so none is given. */
    private static final Graph NO_GRAPH = null;

    private final GraphCache cache = new GraphCache();

    @Test
    void anEmptyCacheSendsZero() {
        SchemaNames sent = cache.request();
        assertSame(SchemaNames.EMPTY, sent);
        assertEquals(0L, sent.hashToSend());
    }

    @Test
    void switchedOffACacheSendsNoHash() {
        assertNull(new GraphCache(false).request());
    }

    @Test
    void aHashAboveLongMaxValueIsSentBackWithTheSameBits() {
        // the server sends the 64-bit hash as a signed integer; it is echoed, never recomputed
        long hash = -1146456808972410223L;
        cache.decodeWith(reply(hash, names(hash, "Person")), cache.request());
        assertEquals("-1146456808972410223", Long.toString(cache.request().hashToSend()));
    }

    @Test
    void namesFromAReplyAreKeptAndDecodedWith() {
        SchemaNames person = names(1L, "Person");
        GraphCache decoding = cache.decodeWith(reply(1L, person), cache.request());

        assertEquals("Person", decoding.getLabel(0, NO_GRAPH));
        assertEquals(person, cache.request());
    }

    @Test
    void theLastReplyWithOtherNamesWins() {
        // one test per case of the replace rule: whatever is cached, names with another hash replace it
        SchemaNames person = names(1L, "Person");
        SchemaNames grown = names(2L, "Person", "City");
        SchemaNames recreated = names(3L, "Movie");
        SchemaNames prefixOfTheCached = names(4L, "Person");

        cache.offer(person);
        assertEquals(person, cache.request(), "an empty cache stores them");
        cache.offer(grown);
        assertEquals(grown, cache.request(), "the graph grew");
        cache.offer(recreated);
        assertEquals(recreated, cache.request(), "the graph was recreated");
        cache.offer(names(3L, "Movie"));
        assertSame(recreated, cache.request(), "the same hash keeps what is cached");
        cache.offer(grown);
        cache.offer(prefixOfTheCached);
        // a recreated graph whose names are a prefix of the old ones: kept forever by a prefix rule, which never
        // recovers; replaced here, at the price of one more reply with names when a lagging replica answered
        assertEquals(prefixOfTheCached, cache.request(), "a graph recreated with fewer names");
    }

    @Test
    void namesFromARefreshAreReplacedByAnyReply() {
        // a refresh on an older server has no hash
        cache.offer(names(1L, "Person"));
        SchemaNames refreshed = SchemaNames.Kind.LABEL.replace(cache.request(), Arrays.asList("Person", "City"));
        assertNull(refreshed.hash());
        cache.offer(refreshed);
        assertEquals(refreshed, cache.request());

        SchemaNames sent = names(1L, "Person");
        cache.offer(sent);
        assertEquals(sent, cache.request());
    }

    @Test
    void aHashOnlyReplyIsDecodedWithWhatTheQuerySentEvenAfterTheCacheMovedOn() {
        cache.offer(names(1L, "Person"));
        SchemaNames sent = cache.request();
        cache.offer(names(2L, "Movie"));

        GraphCache decoding = cache.decodeWith(reply(1L, null), sent);

        assertEquals("Person", decoding.getLabel(0, NO_GRAPH));
    }

    @Test
    void aHashOnlyZeroAnswersAQueryThatSentZeroWithAnEmptySchema() {
        // the cache held names from a refresh, so the query sent 0; the server's schema hash is 0, so it has none
        SchemaNames refreshed = SchemaNames.Kind.LABEL.replace(SchemaNames.EMPTY, Collections.singletonList("Old"));
        cache.offer(refreshed);

        GraphCache decoding = cache.decodeWith(reply(0L, null), cache.request());

        GraphException e = assertThrows(GraphException.class, () -> decoding.getLabel(0, NO_GRAPH));
        assertTrue(e.getMessage().startsWith("Cannot decode label id 0:"), e.getMessage());
        assertEquals(Long.valueOf(0L), cache.request().hash(), "the empty schema replaced the refreshed names");
    }

    @Test
    void aHashOnlyReplyThatIsNotWhatTheQuerySentIsReported() {
        cache.offer(names(1L, "Person"));
        SchemaNames sent = cache.request();

        GraphException mismatch = assertThrows(GraphException.class, () -> cache.decodeWith(reply(2L, null), sent));
        assertTrue(mismatch.getMessage().contains("schema hash 2 without the schema"), mismatch.getMessage());
        assertTrue(mismatch.getMessage().startsWith("The query ran"), mismatch.getMessage());

        assertThrows(GraphException.class, () -> cache.decodeWith(reply(1L, null), null));
    }

    @Test
    void anIdTheConfirmedNamesLackIsReportedNotLookedUp() {
        cache.offer(names(1L, "Person"));
        GraphCache decoding = cache.decodeWith(reply(1L, null), cache.request());

        GraphException e = assertThrows(GraphException.class, () -> decoding.getLabel(1, NO_GRAPH));
        assertTrue(e.getMessage().contains("confirmed"), e.getMessage());
    }

    @Test
    void clearForgetsTheNamesAndTheirHash() {
        cache.offer(names(1L, "Person"));
        cache.clear();
        assertSame(SchemaNames.EMPTY, cache.request());
    }

    @Test
    void aSnapshotHoldsTheNamesAReplyBrought() {
        cache.offer(names(1L, "Person"));
        GraphCache copy = cache.snapshot();
        cache.clear();
        assertEquals("Person", copy.getLabel(0, NO_GRAPH));
    }

    private static SchemaNames names(long hash, String... labels) {
        List<String> none = Collections.emptyList();
        return new SchemaNames(hash, Arrays.asList(labels), none, none);
    }

    /** A reply's schema element: the hash, and the names unless {@code names} is null. */
    private static SchemaReply reply(long hash, SchemaNames names) {
        if (names == null) {
            return SchemaReply.parse(SchemaReplyTest.resp2("hash", hash));
        }
        Object[] labels = names.labels().toArray();
        return SchemaReply.parse(SchemaReplyTest.resp2(
                "hash", hash,
                "labels", SchemaReplyTest.strings(Arrays.copyOf(labels, labels.length, String[].class)),
                "relationshipTypes", SchemaReplyTest.strings(),
                "propertyKeys", SchemaReplyTest.strings()));
    }
}
