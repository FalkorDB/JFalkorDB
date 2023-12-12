package com.falkordb;

import static org.junit.jupiter.api.Assertions.assertEquals;
 
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class IterableTest {

    private GraphContextGenerator api;

    @Before
    public void createApi() {
        api = FalkorDB.driver().graph("social");
    }

    @After
    public void deleteGraph() {
        api.deleteGraph();
        api.close();
    }

    @Test
    public void testRecordsIterator() {
        api.query("UNWIND(range(0,50)) as i CREATE(:N{i:i})");

        ResultSet rs = api.query("MATCH(n) RETURN n");
        int count = 0;
        for (Record record : rs) {
            count++;
        }
        assertEquals(rs.size(), count);
    }

    @Test
    public void testRecordsIterable() {
        api.query("UNWIND(range(0,50)) as i CREATE(:N{i:i})");

        ResultSet rs = api.query("MATCH(n) RETURN n");
        int count = 0;
        for (@SuppressWarnings("unused")
        Record row : rs) {
            count++;
        }
        assertEquals(rs.size(), count);
    }

    @Test
    public void testRecordsIteratorAndIterable() {
        api.query("UNWIND(range(0,50)) as i CREATE(:N{i:i})");

        ResultSet rs = api.query("MATCH(n) RETURN n");
        rs.iterator().next();
        int count = 0;
        for (@SuppressWarnings("unused")
        Record row : rs) {
            count++;
        }
        assertEquals(rs.size(), count);
    }

}
