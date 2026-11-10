package com.falkordb;

import java.util.List;
import java.util.Objects;
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
     * @deprecated since 0.16.0, and to be removed in 1.0.0, because {@code T} is unchecked. Use
     *     {@link #getValue(int, Class)}, which checks the type here and names the column when it
     *     doesn't match: {@code String s = record.getValue(0)} becomes {@code String s =
     *     record.getValue(0, String.class)}. For an untyped read use {@code getValue(index,
     *     Object.class)} or {@link #values()}{@code .get(index)}.
     */
    @Deprecated
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
     * @deprecated since 0.16.0, and to be removed in 1.0.0, because {@code T} is unchecked. Use
     *     {@link #getValue(String, Class)}, which checks the type here and names the column when it
     *     doesn't match: {@code Node n = record.getValue("a")} becomes {@code Node n =
     *     record.getValue("a", Node.class)}. For an untyped read use {@code getValue(key,
     *     Object.class)}.
     */
    @Deprecated
    <T> @Nullable T getValue(String key);

    /**
     * The value at the given field index, checked against {@code type}
     *
     * <p>Unlike {@link #getValue(int)}, the type is checked here: a value of another type throws a
     * {@link ClassCastException} from this call, naming the column, the value's class and {@code
     * type}, even if you then assign the result to {@code Object}. A null value is returned as
     * {@code null}, whatever the type.
     *
     * <ul>
     *   <li>The value must be an instance of {@code type}, so a supertype matches ({@code
     *       GraphEntity.class} for a node, {@code Object.class} for anything). Nothing is converted:
     *       FalkorDB integers decode as {@link Long} and floats as {@link Double}, so asking for
     *       {@code Integer.class} on an integer column throws.
     *   <li>A primitive class stands for its wrapper: {@code long.class} reads a {@link Long}. The
     *       result is still {@code null} for a null value, so {@code long n = record.getValue(0,
     *       long.class)} throws {@link NullPointerException} when unboxing a null column.
     *   <li>Only the raw class is checked: {@code List<?> l = record.getValue(0, List.class)} checks
     *       that the value is a list, not what its elements are.
     * </ul>
     *
     * <p>An index out of range fails exactly as {@link #getValue(int)} does.
     *
     * @param index field index
     * @param type the class the value must be an instance of; a primitive class stands for its
     *     wrapper
     * @param <T> return value type
     *
     * @return the value at the field, or {@code null} if the stored value is null
     * @throws ClassCastException if the value is not null and not an instance of {@code type}
     * @throws NullPointerException if {@code type} is null
     * @since 0.16.0
     */
    default <T> @Nullable T getValue(int index, Class<T> type) {
        Objects.requireNonNull(type, "type must not be null");
        @Nullable Object value = getValue(index);
        return RecordValues.cast(this, value, type, index, null);
    }

    /**
     * The value at the given field, checked against {@code type}
     *
     * <p>The type is checked exactly as in {@link #getValue(int, Class)}: a non-null value that is
     * not an instance of {@code type} throws a {@link ClassCastException} from this call, naming the
     * column, the value's class and {@code type}; a primitive class stands for its wrapper; a null
     * value is returned as {@code null}. An unknown key fails exactly as {@link #getValue(String)}
     * does.
     *
     * @param key header key
     * @param type the class the value must be an instance of; a primitive class stands for its
     *     wrapper
     * @param <T> return value type
     *
     * @return the value at the field, or {@code null} if the stored value is null
     * @throws IllegalArgumentException if the record has no column with that key
     * @throws ClassCastException if the value is not null and not an instance of {@code type}
     * @throws NullPointerException if {@code type} is null
     * @since 0.16.0
     */
    default <T> @Nullable T getValue(String key, Class<T> type) {
        Objects.requireNonNull(type, "type must not be null");
        @Nullable Object value = getValue(key);
        return RecordValues.cast(this, value, type, -1, key);
    }

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
     * before dereferencing it, as you would the {@link #getValue(int, Class)} of that column.
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
