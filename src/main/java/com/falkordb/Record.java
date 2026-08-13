package com.falkordb;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Container for Graph result values.
 *
 * List records are returned from Graph statement execution, contained within a ResultSet.
 */
public interface Record {

    /**
     * The value at the given field index
     *
     * @param index field index
     * @param <T> return value type
     *
     * @return the value at the field, or {@code null} if the stored value is null
     */
    <T> @Nullable T getValue(int index);

    /**
     * The value at the given field
     *
     * @param key header key
     * @param <T> return value type
     *
     * @return the value at the field, or {@code null} if the stored value is null
     * @throws IllegalArgumentException if the record has no column with that key
     */
    <T> @Nullable T getValue(String key);

    /**
     * The value at the given field index (represented as String)
     *
     * @param index field index
     * @return string representation of the value, or {@code null} if the stored value is null
     */
    @Nullable
    String getString(int index);

    /**
     * The value at the given field (represented as String)
     *
     * @param key header key
     *
     * @return string representation of the value, or {@code null} if the stored value is null
     * @throws IllegalArgumentException if the record has no column with that key
     */
    @Nullable
    String getString(String key);

    /**
     * The keys of the record
     *
     * <p>Records share the result set's schema, so the returned list is unmodifiable; mutating it
     * throws {@link UnsupportedOperationException}. Copy it if you need a mutable list. (Before
     * 0.11.0 this exposed the shared header list, so mutating it corrupted every record in the
     * result set.)
     *
     * @return list of the record key, unmodifiable
     */
    List<String> keys();

    /**
     * The values of the record
     *
     * @return list of the record values
     */
    List<Object> values();

    /**
     * Check if the record header contains the given key
     *
     * @param key header key
     *
     * @return <code>true</code> if the the key exists
     */
    boolean containsKey(String key);

    /**
     * The number of fields in this record
     *
     * @return the number of fields
     */
    int size();
}
