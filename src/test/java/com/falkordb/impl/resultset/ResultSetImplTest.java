package com.falkordb.impl.resultset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.falkordb.Record;
import com.falkordb.ResultSet;
import com.falkordb.exceptions.GraphException;
import com.falkordb.graph_entities.Node;
import com.falkordb.impl.graph_cache.GraphCache;
import com.falkordb.impl.graph_cache.SchemaNames;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.util.KeyValue;
import redis.clients.jedis.util.SafeEncoder;

public class ResultSetImplTest {

    @Test
    public void emptyResponseYieldsEmptyResultSet() {
        // An empty array reply must produce the empty ResultSet the size()!=3 branch was written to
        // build. The run-time-error probe used to read get(size()-1) before any empty check, so an
        // empty reply threw IndexOutOfBoundsException and left that branch unreachable.
        ResultSet resultSet = new ResultSetImpl(new ArrayList<>(), null, null);

        assertEquals(0, resultSet.size());
        assertTrue(resultSet.getHeader().getSchemaNames().isEmpty());
        assertFalse(resultSet.iterator().hasNext());
        assertEquals(0, resultSet.getStatistics().nodesCreated());
    }

    @Test
    public void recordsFromAParsedReplyExposeAnUnmodifiableKeyList() {
        // keys() is documented unmodifiable, but RecordImpl deliberately SHARES the schema list
        // rather than copying it per row, so the guarantee lives in the parse path. Assert it on a
        // record built the way every user-visible record is, instead of only on one handed a list
        // the test itself made unmodifiable.
        List<Object> column = Arrays.asList(1L, SafeEncoder.encode("n")); // COLUMN_SCALAR
        List<List<Object>> rawHeader = Collections.singletonList(column);
        List<Object> cell = Arrays.asList(2L, SafeEncoder.encode("v")); // VALUE_STRING
        List<List<Object>> rawRows = Collections.singletonList(Collections.singletonList((Object) cell));
        List<Object> rawResponse = Arrays.asList(rawHeader, rawRows, new ArrayList<>());

        ResultSet resultSet = new ResultSetImpl(rawResponse, null, null);
        Record record = resultSet.iterator().next();

        assertEquals(Collections.singletonList("n"), record.keys());
        assertEquals("v", record.getString(0));
        assertThrows(UnsupportedOperationException.class, () -> record.keys().add("injected"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> resultSet.getHeader().getSchemaNames().add("injected"));
    }

    @Test
    public void schemaIdsOutsideTheIntRangeAreRejectedBeforeAnyLookup() {
        // A label, relationship-type or property-key id used to be narrowed with intValue(), so 4294967296
        // wrapped to 0 and decoded as the first label, and 2147483648 turned negative. The guard fires
        // before the cache is consulted, which is why no graph is needed here.
        for (long id : new long[] {-1L, Integer.MAX_VALUE + 1L, 1L << 32}) {
            List<Object> node = Arrays.asList(0L, Collections.singletonList(id), Collections.emptyList());
            assertInvalidId("label", id, Arrays.asList(8L, node)); // VALUE_NODE

            List<Object> edge = Arrays.asList(0L, id, 0L, 0L, Collections.emptyList());
            assertInvalidId("relationship type", id, Arrays.asList(7L, edge)); // VALUE_EDGE

            List<Object> property = Arrays.asList(id, 3L, 1L); // key id, VALUE_INTEGER, 1
            List<Object> propertyNode = Arrays.asList(0L, Collections.emptyList(), Collections.singletonList(property));
            assertInvalidId("property key", id, Arrays.asList(8L, propertyNode));
        }
    }

    @Test
    public void nullInsideAMapOrAnArrayDecodesToANullValue() {
        // Record documents that a map or list value can hold null values: `RETURN {a: null}` and
        // `RETURN [1, null]` send VALUE_NULL entries inside the container, which decode to null.
        List<Object> nullScalar = Arrays.asList(1L, null); // VALUE_NULL
        List<Object> map = Arrays.asList(10L, Arrays.asList(SafeEncoder.encode("a"), nullScalar)); // VALUE_MAP
        List<Object> array = Arrays.asList(6L, Arrays.asList(Arrays.asList(3L, 1L), nullScalar)); // VALUE_ARRAY
        List<List<Object>> rawHeader = Arrays.asList(
                Arrays.asList(1L, SafeEncoder.encode("m")), Arrays.asList(1L, SafeEncoder.encode("l"))); // scalars
        List<List<Object>> rawRows = Collections.singletonList(Arrays.asList((Object) map, array));
        List<Object> rawResponse = Arrays.asList(rawHeader, rawRows, new ArrayList<>());

        Record record = new ResultSetImpl(rawResponse, null, null).iterator().next();

        Map<String, Object> m = record.getValue("m");
        assertTrue(m.containsKey("a"));
        assertNull(m.get("a"));
        assertEquals(Arrays.asList(1L, null), record.getValue("l"));
    }

    private static void assertInvalidId(String entryName, long id, List<Object> cell) {
        List<Object> column = Arrays.asList(1L, SafeEncoder.encode("v")); // COLUMN_SCALAR
        List<List<Object>> rawHeader = Collections.singletonList(column);
        List<List<Object>> rawRows = Collections.singletonList(Collections.singletonList((Object) cell));
        List<Object> rawResponse = Arrays.asList(rawHeader, rawRows, new ArrayList<>());

        GraphException e =
                assertThrows(GraphException.class, () -> new ResultSetImpl(rawResponse, null, new GraphCache()));
        assertEquals("Invalid " + entryName + " id " + id, e.getMessage());
    }

    // ---- the schema element FalkorDB 6.2+ appends to a reply to a query sent with SCHEMA_HASH ----

    private static final long HASH = 42L;

    /** The RESP2 schema element: the hash, and the names when {@code labels} is not null. */
    private static List<Object> schemaElement(long hash, String... labels) {
        List<Object> element = new ArrayList<>(Arrays.asList(SafeEncoder.encode("hash"), hash));
        if (labels.length > 0) {
            element.addAll(Arrays.asList(
                    SafeEncoder.encode("labels"), bytesList(labels),
                    SafeEncoder.encode("relationshipTypes"), bytesList(),
                    SafeEncoder.encode("propertyKeys"), bytesList("name")));
        }
        return element;
    }

    private static List<Object> bytesList(String... names) {
        List<Object> list = new ArrayList<>();
        for (String name : names) {
            list.add(SafeEncoder.encode(name));
        }
        return list;
    }

    /** {@code [header, rows, stats]} with one node of label id 0 and property key id 0 = "Alice". */
    private static List<Object> nodeReply() {
        List<Object> column = Arrays.asList(1L, SafeEncoder.encode("n"));
        List<Object> property = Arrays.asList(0L, 2L, SafeEncoder.encode("Alice")); // key 0, VALUE_STRING
        List<Object> node = Arrays.asList(7L, Collections.singletonList(0L), Collections.singletonList(property));
        List<Object> cell = Arrays.asList(8L, node); // VALUE_NODE
        List<Object> reply = new ArrayList<>();
        reply.add(Collections.singletonList(column));
        reply.add(Collections.singletonList(Collections.singletonList(cell)));
        reply.add(Collections.singletonList(SafeEncoder.encode("Cached execution: 0")));
        return reply;
    }

    private static Node onlyNode(ResultSet resultSet) {
        assertEquals(1, resultSet.size());
        return resultSet.iterator().next().getValue(0, Node.class);
    }

    @Test
    public void aReplyWithNamesIsDecodedWithThemWithoutALookup() {
        // the graph is never asked: the reply names what its ids mean
        GraphCache cache = new GraphCache();
        List<Object> reply = nodeReply();
        reply.add(schemaElement(HASH, "Person"));

        Node node = onlyNode(new ResultSetImpl(reply, null, cache, cache.request()));

        assertEquals("Person", node.getLabel(0));
        assertEquals("Alice", node.getProperty("name").getValue());
        assertEquals(Long.valueOf(HASH), cache.request().hash(), "the names are kept for the next query");
        assertEquals(4, reply.size(), "the reply itself is left as it was, so it can be decoded again");
    }

    @Test
    public void aReplyWithNamesInResp3IsDecodedWithThem() {
        GraphCache cache = new GraphCache();
        List<Object> reply = nodeReply();
        List<Object> element = schemaElement(HASH, "Person");
        List<Object> map = new ArrayList<>();
        for (int i = 0; i < element.size(); i += 2) {
            map.add(new KeyValue<>(element.get(i), element.get(i + 1)));
        }
        reply.add(map);

        assertEquals(
                "Person",
                onlyNode(new ResultSetImpl(reply, null, cache, cache.request())).getLabel(0));
    }

    @Test
    public void aHashOnlyReplyIsDecodedWithTheNamesTheQuerySent() {
        GraphCache cache = new GraphCache();
        List<Object> first = nodeReply();
        first.add(schemaElement(HASH, "Person"));
        new ResultSetImpl(first, null, cache, cache.request());
        SchemaNames sent = cache.request();

        // the cache moves on (another reply brought a recreated graph's names) before this reply is read
        List<Object> other = nodeReply();
        other.add(schemaElement(7L, "Movie"));
        new ResultSetImpl(other, null, cache, cache.request());

        List<Object> confirmed = nodeReply();
        confirmed.add(schemaElement(HASH));
        assertEquals(
                "Person",
                onlyNode(new ResultSetImpl(confirmed, null, cache, sent)).getLabel(0));
    }

    @Test
    public void aWriteReplyWithOnlyStatisticsAndTheElementHasNoRows() {
        GraphCache cache = new GraphCache();
        List<Object> reply = new ArrayList<>();
        reply.add(Collections.singletonList(SafeEncoder.encode("Nodes created: 1")));
        reply.add(schemaElement(HASH, "Person"));

        ResultSet resultSet = new ResultSetImpl(reply, null, cache, cache.request());

        assertEquals(0, resultSet.size());
        assertEquals(1, resultSet.getStatistics().nodesCreated(), "the element is not read as statistics");
    }

    @Test
    public void anErrorBeforeTheElementIsStillReported() {
        GraphCache cache = new GraphCache();
        List<Object> reply = new ArrayList<>();
        reply.add(new JedisDataException("ERR boom"));
        reply.add(schemaElement(HASH));

        GraphException e =
                assertThrows(GraphException.class, () -> new ResultSetImpl(reply, null, cache, cache.request()));
        assertTrue(e.getMessage().contains("boom"), e.getMessage());
    }

    @Test
    public void anIdTheReplysNamesLackIsReportedNotLookedUp() {
        GraphCache cache = new GraphCache();
        List<Object> reply = nodeReply();
        List<Object> element = schemaElement(HASH, "Person");
        element.set(element.size() - 1, bytesList()); // no property keys
        reply.add(element);

        GraphException e =
                assertThrows(GraphException.class, () -> new ResultSetImpl(reply, null, cache, cache.request()));
        assertTrue(e.getMessage().startsWith("Cannot decode property key id 0:"), e.getMessage());
    }

    @Test
    public void aHashOnlyReplyToAQueryThatSentNoHashIsReported() {
        List<Object> reply = nodeReply();
        reply.add(schemaElement(HASH));

        GraphException e = assertThrows(GraphException.class, () -> new ResultSetImpl(reply, null, new GraphCache()));
        assertTrue(e.getMessage().startsWith("The query ran"), e.getMessage());
    }
}
