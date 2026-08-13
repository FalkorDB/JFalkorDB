package com.falkordb.impl.resultset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.falkordb.ResultSet;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

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
}
