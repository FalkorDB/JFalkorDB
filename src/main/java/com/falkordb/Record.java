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
     * <p>{@code T} is inferred from the assignment, never checked: the cast is unchecked and erased,
     * so the compiler inserts it at <em>your</em> call site. Asking for the wrong type therefore
     * throws {@link ClassCastException} there rather than here, and throws nothing at all while the
     * value stays untyped (assigned to {@code Object}, or passed straight on). Check the column's
     * {@link Header#getSchemaTypes() schema type} if you cannot rely on the query shape.
     *
     * @param index field index
     * @param <T> return value type, unchecked
     *
     * @return the value at the field, or {@code null} if the stored value is null
     */
    <T> @Nullable T getValue(int index);

    /**
     * The value at the given field
     *
     * <p>{@code T} is unchecked, exactly as in {@link #getValue(int)}: a wrong type surfaces as a
     * {@link ClassCastException} at the caller, not here.
     *
     * @param key header key
     * @param <T> return value type, unchecked
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
     * <p>A NULL column deserializes to {@code null}, so the returned list may contain null elements
     * — hence {@code List<@Nullable Object>} rather than {@code List<Object>}. Null-check an element
     * before dereferencing it, as you would the {@link #getValue(int)} of that column.
     *
     * @return list of the record values, whose elements may be {@code null}
     */
    List<@Nullable Object> values();

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
