package com.falkordb.impl.api;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.util.SafeEncoder;

import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for DriverImpl parseListResponse method to ensure comprehensive coverage
 */
public class DriverImplParseResponseTest {

    private DriverImpl driverImpl;

    @BeforeEach
    public void setUp() {
        // Create a DriverImpl instance for testing (we won't use the connection)
        driverImpl = new DriverImpl("localhost", 6379);
    }

    @Test
    public void testParseListResponseWithEmptyList() {
        List<Object> emptyList = Arrays.asList();
        List<String> result = driverImpl.parseListResponse(emptyList);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isEmpty());
    }

    @Test
    public void testParseListResponseWithStringList() {
        List<Object> stringList = Arrays.asList("graph1", "graph2", "graph3");
        List<String> result = driverImpl.parseListResponse(stringList);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(3, result.size());
        Assertions.assertEquals("graph1", result.get(0));
        Assertions.assertEquals("graph2", result.get(1));
        Assertions.assertEquals("graph3", result.get(2));
    }

    @Test
    public void testParseListResponseWithByteArrayList() {
        byte[] bytes1 = SafeEncoder.encode("graph1");
        byte[] bytes2 = SafeEncoder.encode("graph2");
        List<Object> byteArrayList = Arrays.asList(bytes1, bytes2);

        List<String> result = driverImpl.parseListResponse(byteArrayList);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(2, result.size());
        Assertions.assertEquals("graph1", result.get(0));
        Assertions.assertEquals("graph2", result.get(1));
    }

    @Test
    public void testParseListResponseWithMixedTypes() {
        byte[] bytes1 = SafeEncoder.encode("graph1");
        String string1 = "graph2";
        List<Object> mixedList = Arrays.asList(bytes1, string1);

        List<String> result = driverImpl.parseListResponse(mixedList);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(2, result.size());
        Assertions.assertEquals("graph1", result.get(0));
        Assertions.assertEquals("graph2", result.get(1));
    }

    @Test
    public void testParseListResponseWithUnsupportedTypes() {
        // Test with unsupported types (Integer, null, etc.) that should be ignored
        List<Object> mixedList = Arrays.asList("graph1", 123, null, "graph2", new Object());

        List<String> result = driverImpl.parseListResponse(mixedList);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(2, result.size());
        Assertions.assertEquals("graph1", result.get(0));
        Assertions.assertEquals("graph2", result.get(1));
    }

    @Test
    public void testParseListResponseWithNonListResponse() {
        // Test when response is not a List (should return empty list)
        String nonListResponse = "not a list";
        List<String> result = driverImpl.parseListResponse(nonListResponse);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isEmpty());
    }

    @Test
    public void testParseListResponseWithNullResponse() {
        // Test when response is null (should return empty list)
        List<String> result = driverImpl.parseListResponse(null);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isEmpty());
    }
}