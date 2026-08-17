package com.falkordb.impl.resultset;

import com.falkordb.Record;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * An implementation of the Record interface.
 */
public class RecordImpl implements Record {

    private final List<String> header;
    private final List<@Nullable Object> values;

    /**
     * Creates a new RecordImpl.
     *
     * <p>The header list is shared, not copied: every record in a result set holds the one schema
     * list {@code HeaderImpl} built, so a copy here would cost an allocation per row. Callers must
     * therefore pass an unmodifiable, unaliased list — {@code HeaderImpl.getSchemaNames()} is one —
     * because it is handed straight back by {@link #keys()}, which is documented unmodifiable.
     *
     * @param header the header of the record; must be unmodifiable, as {@link #keys()} returns it
     * @param values the values of the record; a NULL column is stored as {@code null}
     */
    public RecordImpl(List<String> header, List<@Nullable Object> values) {
        this.header = header;
        this.values = values;
    }

    @Override
    public <T> @Nullable T getValue(int index) {
        // Unchecked and erased: nothing here can verify T, so the compiler inserts the checkcast at the
        // caller's assignment. Documented on Record#getValue(int) and pinned by RecordImplTest.
        return (T) this.values.get(index);
    }

    @Override
    public <T> @Nullable T getValue(String key) {
        return getValue(indexOf(key));
    }

    @Override
    public @Nullable String getString(int index) {
        Object value = this.values.get(index);
        // A NULL column deserializes to null; getValue is documented @Nullable, so report the same
        // absence here rather than throwing NPE from null.toString().
        return value == null ? null : value.toString();
    }

    @Override
    public @Nullable String getString(String key) {
        return getString(indexOf(key));
    }

    /**
     * Resolves a column name to its index, failing with the offending key rather than letting the
     * -1 from a miss reach values.get() and surface as "Index -1 out of bounds".
     */
    private int indexOf(String key) {
        int index = this.header.indexOf(key);
        if (index < 0) {
            throw new IllegalArgumentException(
                    "No such column in this record: " + key + "; columns are " + this.header);
        }
        return index;
    }

    @Override
    public List<String> keys() {
        return header;
    }

    @Override
    public List<@Nullable Object> values() {
        return this.values;
    }

    @Override
    public boolean containsKey(String key) {
        return this.header.contains(key);
    }

    @Override
    public int size() {
        return this.header.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RecordImpl)) return false;
        RecordImpl record = (RecordImpl) o;
        return Objects.equals(header, record.header) && Objects.equals(values, record.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(header, values);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Record{");
        sb.append("values=").append(values);
        sb.append('}');
        return sb.toString();
    }
}
