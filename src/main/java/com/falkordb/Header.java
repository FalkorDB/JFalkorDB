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
     * Returns the schema names.
     *
     * <p>The returned list is unmodifiable; mutating it throws
     * {@link UnsupportedOperationException}. Copy it if you need a mutable list. (Before 0.11.0 this
     * exposed the header's internal list, so callers could corrupt the schema in place.)
     *
     * @return the schema names, unmodifiable
     */
    List<String> getSchemaNames();

    /**
     * Returns the schema types.
     *
     * <p>The returned list is unmodifiable; mutating it throws
     * {@link UnsupportedOperationException}. Copy it if you need a mutable list. (Before 0.11.0 this
     * exposed the header's internal list, so callers could corrupt the schema in place.)
     *
     * @return the schema types, unmodifiable
     */
    List<ResultSetColumnTypes> getSchemaTypes();
}
