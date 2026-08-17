package com.falkordb.impl.resultset;

import redis.clients.jedis.exceptions.JedisDataException;

enum ResultSetScalarTypes {
    VALUE_UNKNOWN,
    VALUE_NULL,
    VALUE_STRING,
    VALUE_INTEGER, // 64 bit long.
    VALUE_BOOLEAN,
    VALUE_DOUBLE,
    VALUE_ARRAY,
    VALUE_EDGE,
    VALUE_NODE,
    VALUE_PATH,
    VALUE_MAP,
    VALUE_POINT,
    VALUE_VECTORF32,
    VALUE_DATETIME,
    VALUE_DATE,
    VALUE_TIME,
    VALUE_DURATION;

    private static final ResultSetScalarTypes[] values = values();

    /**
     * Resolves a server-supplied scalar-type ordinal. Takes a long and range-checks it before
     * narrowing: intValue() keeps only the low 32 bits, so an out-of-range ordinal such as
     * 4294967297 would wrap to 1 and be accepted as VALUE_NULL.
     */
    public static ResultSetScalarTypes getValue(long index) {
        if (index < 0 || index >= values.length) {
            throw new JedisDataException("Unrecognized response type");
        }
        return values[(int) index];
    }
}
