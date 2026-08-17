package com.falkordb.impl.resultset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.falkordb.Record;
import com.falkordb.ResultSet;
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
}
