package com.falkordb;

import java.util.List;

/**
 * Query response header interface. Represents the response schema (column names and types)
 */
public interface Header {

    /**
     * The type of a column in a result set.
     */
    enum ResultSetColumnTypes {
        /**
         * An unknown column type.
         */
        COLUMN_UNKNOWN,
        /**
         * A scalar column type.
         */
        COLUMN_SCALAR,
        /**
         * A node column type.
         */
        COLUMN_NODE,
        /**
         * A relation column type.
         */
        COLUMN_RELATION
    }

    /**
     * @return the schema names
     */
    List<String> getSchemaNames();

    /**
     * @return the schema types
     */
    List<ResultSetColumnTypes> getSchemaTypes();
}