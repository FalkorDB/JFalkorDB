package com.falkordb;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The type check behind {@link Record#getValue(int, Class)} and {@link Record#getValue(String, Class)}.
 *
 * <p>Package-private rather than in {@code Record}: a Java 8 interface can't have private methods,
 * and a static interface method would become public API.
 */
final class RecordValues {

    /** Primitive class to its wrapper; {@code long.class} is typed {@code Class<Long>}, so map it. */
    private static final Map<Class<?>, Class<?>> WRAPPERS;

    static {
        Map<Class<?>, Class<?>> wrappers = new HashMap<>();
        wrappers.put(boolean.class, Boolean.class);
        wrappers.put(byte.class, Byte.class);
        wrappers.put(char.class, Character.class);
        wrappers.put(short.class, Short.class);
        wrappers.put(int.class, Integer.class);
        wrappers.put(long.class, Long.class);
        wrappers.put(float.class, Float.class);
        wrappers.put(double.class, Double.class);
        wrappers.put(void.class, Void.class);
        WRAPPERS = Collections.unmodifiableMap(wrappers);
    }

    private RecordValues() {}

    /**
     * Returns {@code value} as a {@code T}, or throws a {@link ClassCastException} that names the
     * column, the value's class and the requested class.
     *
     * @param record the record the value came from; consulted for the column's index or name only
     *     when the check fails
     * @param value the column's value, already looked up
     * @param type the requested class; a primitive class stands for its wrapper
     * @param index the column's index, or -1 if the caller looked it up by name
     * @param key the column's name, or {@code null} if the caller looked it up by index
     */
    static <T> @Nullable T cast(Record record, @Nullable Object value, Class<T> type, int index, @Nullable String key) {
        if (value == null) {
            return null;
        }
        Class<?> boxed = WRAPPERS.getOrDefault(type, type);
        if (!boxed.isInstance(value)) {
            throw new ClassCastException(message(record, value, type, boxed, index, key));
        }
        @SuppressWarnings("unchecked") // isInstance just checked it; a primitive T is its wrapper
        T typed = (T) value;
        return typed;
    }

    /**
     * The {@link ClassCastException} message: the column's index and name where the record can
     * resolve them, the value's class, the requested class (and its wrapper, for a primitive), and a
     * hint at FalkorDB's numeric types when a number was asked for as another number.
     */
    private static String message(
            Record record, Object value, Class<?> type, Class<?> boxed, int index, @Nullable String key) {
        // Built only on failure, so the success path never calls keys(). An implementer's keys() may
        // not resolve the column (shorter than the values, or a lookup that isn't exact matching):
        // then the part it can't resolve is left out rather than reported wrong.
        List<String> keys = record.keys();
        int column = key == null ? index : keys.indexOf(key);
        @Nullable String name = key != null ? key : index >= 0 && index < keys.size() ? keys.get(index) : null;
        StringBuilder sb = new StringBuilder("Column");
        if (column >= 0) {
            sb.append(' ').append(column);
        }
        if (name != null) {
            sb.append(column >= 0 ? " (\"" : " \"").append(name).append(column >= 0 ? "\")" : "\"");
        }
        sb.append(" holds ").append(value.getClass().getName()).append(", not the requested ");
        sb.append(type.getName());
        if (boxed != type) {
            sb.append(" (as ").append(boxed.getName()).append(')');
        }
        if (value instanceof Number && Number.class.isAssignableFrom(boxed)) {
            sb.append("; FalkorDB integers decode as java.lang.Long and floats as java.lang.Double");
        }
        return sb.toString();
    }
}
