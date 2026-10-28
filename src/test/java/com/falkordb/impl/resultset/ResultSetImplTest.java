package com.falkordb.impl.resultset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.falkordb.Record;
import com.falkordb.ResultSet;
import com.falkordb.exceptions.GraphException;
import com.falkordb.impl.graph_cache.GraphCache;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
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

    private static void assertInvalidId(String entryName, long id, List<Object> cell) {
        List<Object> column = Arrays.asList(1L, SafeEncoder.encode("v")); // COLUMN_SCALAR
        List<List<Object>> rawHeader = Collections.singletonList(column);
        List<List<Object>> rawRows = Collections.singletonList(Collections.singletonList((Object) cell));
        List<Object> rawResponse = Arrays.asList(rawHeader, rawRows, new ArrayList<>());

        GraphException e =
                assertThrows(GraphException.class, () -> new ResultSetImpl(rawResponse, null, new GraphCache()));
        assertEquals("Invalid " + entryName + " id " + id, e.getMessage());
    }
}
