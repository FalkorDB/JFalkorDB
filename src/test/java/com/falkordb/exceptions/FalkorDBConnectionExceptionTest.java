package com.falkordb.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.exceptions.JedisConnectionException;

class FalkorDBConnectionExceptionTest {

    @Test
    void carriesAMessage() {
        FalkorDBConnectionException e = new FalkorDBConnectionException("down");
        assertEquals("down", e.getMessage());
        assertNull(e.getCause());
    }

    @Test
    void carriesACause() {
        SocketTimeoutException cause = new SocketTimeoutException("Read timed out");
        FalkorDBConnectionException e = new FalkorDBConnectionException(cause);
        assertSame(cause, e.getCause());
        assertEquals(cause.toString(), e.getMessage());
    }

    @Test
    void carriesAMessageAndACause() {
        SocketTimeoutException cause = new SocketTimeoutException("Read timed out");
        FalkorDBConnectionException e = new FalkorDBConnectionException("down", cause);
        assertEquals("down", e.getMessage());
        assertSame(cause, e.getCause());
    }

    @Test
    void isStillCaughtAsJedisConnectionException() {
        // what callers catch today keeps matching
        assertThrows(JedisConnectionException.class, () -> {
            throw new FalkorDBConnectionException("down");
        });
    }
}
