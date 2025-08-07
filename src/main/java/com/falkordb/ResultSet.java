package com.falkordb;

/**
 * Hold a query result
 */
public interface ResultSet extends Iterable<Record> {

    /**
     * @return the number of records in the result set
     */
    int size();

    /**
     * @return the statistics of the result set
     */
    Statistics getStatistics();

    /**
     * @return the header of the result set
     */
    Header getHeader();

}
