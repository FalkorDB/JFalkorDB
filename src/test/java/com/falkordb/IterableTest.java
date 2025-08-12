package com.falkordb;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.falkordb.test.BaseTestContainerTestIT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class IterableTest extends BaseTestContainerTestIT {

    private GraphContextGenerator api;

    @BeforeEach
    public void createApi() {
        api = FalkorDB.driver(getFalkordbHost(),getFalkordbPort()).graph("social");
    }

    @AfterEach
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
            assertNotNull(record);
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
        for (Record row : rs) {
            assertNotNull(row);
            count++;
        }
        assertEquals(rs.size(), count);
    }

}
