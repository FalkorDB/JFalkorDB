package com.falkordb;

/**
 * Hold a query result
 */
public interface ResultSet extends Iterable<Record> {

    /**
     * Returns the number of records in the result set.
     *
     * @return the number of records in the result set
     */
    int size();

    /**
     * Returns the statistics of the result set.
     *
     * @return the statistics of the result set
     */
    Statistics getStatistics();

    /**
     * Returns the header of the result set.
     *
     * @return the header of the result set
     */
    Header getHeader();
}
