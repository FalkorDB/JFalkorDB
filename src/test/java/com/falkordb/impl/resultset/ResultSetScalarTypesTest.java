package com.falkordb.impl.resultset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import redis.clients.jedis.exceptions.JedisDataException;

public class ResultSetScalarTypesTest {

    @Test
    public void resolvesKnownOrdinals() {
        assertEquals(ResultSetScalarTypes.VALUE_NULL, ResultSetScalarTypes.getValue(1L));
        assertEquals(ResultSetScalarTypes.VALUE_DOUBLE, ResultSetScalarTypes.getValue(5L));
    }

    @Test
    public void unknownOrdinalIsReportedAsAProtocolError() {
        assertThrows(JedisDataException.class, () -> ResultSetScalarTypes.getValue(99L));
        assertThrows(JedisDataException.class, () -> ResultSetScalarTypes.getValue(-1L));
    }

    @Test
    public void outOfRangeOrdinalThatWrapsToAValidIntIsRejected() {
        // 4294967297 == 2^32 + 1: narrowing to int first would yield 1 and be accepted as VALUE_NULL,
        // so the range check has to happen on the long the server actually sent.
        assertThrows(JedisDataException.class, () -> ResultSetScalarTypes.getValue(4294967297L));
    }
}
