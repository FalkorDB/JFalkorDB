package com.falkordb;

/**
 * Hold a query result
 */
public interface ResultSet extends Iterable<Record> {

    int size();

    Statistics getStatistics();

    Header getHeader();

}