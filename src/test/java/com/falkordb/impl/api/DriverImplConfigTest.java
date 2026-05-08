package com.falkordb.impl.api;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.util.SafeEncoder;

/**
 * Unit tests for DriverImpl configGet/configSet response parsing logic.
 * Tests the parseConfigGetResponse and parseConfigSetResponse methods directly.
 */
public class DriverImplConfigTest {

    private DriverImpl driverImpl;

    @BeforeEach
    public void setUp() {
        driverImpl = new DriverImpl("localhost", 6379);
    }

    @Test
    public void testParseConfigGetResponseWithByteArrayValue() {
        List<Object> response = Arrays.asList(
                SafeEncoder.encode("RESULTSET_SIZE"),
                SafeEncoder.encode("100"));
        String result = driverImpl.parseConfigGetResponse(response);
        Assertions.assertEquals("100", result);
    }

    @Test
    public void testParseConfigGetResponseWithLongValue() {
        List<Object> response = Arrays.asList(
                SafeEncoder.encode("RESULTSET_SIZE"),
                Long.valueOf(100));
        String result = driverImpl.parseConfigGetResponse(response);
        Assertions.assertEquals("100", result);
    }

    @Test
    public void testParseConfigGetResponseWithNullValue() {
        List<Object> response = Arrays.asList(
                SafeEncoder.encode("RESULTSET_SIZE"),
                null);
        Assertions.assertThrows(JedisDataException.class, () -> {
            driverImpl.parseConfigGetResponse(response);
        });
    }

    @Test
    public void testParseConfigGetResponseWithEmptyList() {
        List<Object> response = Collections.emptyList();
        Assertions.assertThrows(JedisDataException.class, () -> {
            driverImpl.parseConfigGetResponse(response);
        });
    }

    @Test
    public void testParseConfigGetResponseWithSingleElementList() {
        List<Object> response = Collections.singletonList(SafeEncoder.encode("RESULTSET_SIZE"));
        Assertions.assertThrows(JedisDataException.class, () -> {
            driverImpl.parseConfigGetResponse(response);
        });
    }

    @Test
    public void testParseConfigGetResponseWithNonListResponse() {
        Assertions.assertThrows(JedisDataException.class, () -> {
            driverImpl.parseConfigGetResponse("not a list");
        });
    }

    @Test
    public void testParseConfigGetResponseWithNull() {
        Assertions.assertThrows(JedisDataException.class, () -> {
            driverImpl.parseConfigGetResponse(null);
        });
    }

    @Test
    public void testParseConfigSetResponseWithOkBytes() {
        boolean result = driverImpl.parseConfigSetResponse(SafeEncoder.encode("OK"));
        Assertions.assertTrue(result);
    }

    @Test
    public void testParseConfigSetResponseWithOkString() {
        boolean result = driverImpl.parseConfigSetResponse("OK");
        Assertions.assertTrue(result);
    }

    @Test
    public void testParseConfigSetResponseWithNull() {
        boolean result = driverImpl.parseConfigSetResponse(null);
        Assertions.assertFalse(result);
    }

    @Test
    public void testParseConfigSetResponseWithErrorString() {
        boolean result = driverImpl.parseConfigSetResponse("ERR");
        Assertions.assertFalse(result);
    }

    @Test
    public void testParseConfigSetResponseCaseInsensitive() {
        Assertions.assertTrue(driverImpl.parseConfigSetResponse(SafeEncoder.encode("ok")));
        Assertions.assertTrue(driverImpl.parseConfigSetResponse(SafeEncoder.encode("Ok")));
        Assertions.assertTrue(driverImpl.parseConfigSetResponse(SafeEncoder.encode("OK")));
    }
}
