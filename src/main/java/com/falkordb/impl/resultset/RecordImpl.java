package com.falkordb.impl.resultset;

import com.falkordb.Record;
import java.util.List;
import java.util.Objects;

/**
 * An implementation of the Record interface.
 */
public class RecordImpl implements Record {

    private final List<String> header;
    private final List<Object> values;

    /**
     * Creates a new RecordImpl.
     * @param header the header of the record
     * @param values the values of the record
     */
    public RecordImpl(List<String> header, List<Object> values) {
        this.header = header;
        this.values = values;
    }

    @Override
    public <T> T getValue(int index) {
        return (T) this.values.get(index);
    }

    @Override
    public <T> T getValue(String key) {
        return getValue(indexOf(key));
    }

    @Override
    public String getString(int index) {
        Object value = this.values.get(index);
        // A NULL column deserializes to null; getValue is documented @Nullable, so report the same
        // absence here rather than throwing NPE from null.toString().
        return value == null ? null : value.toString();
    }

    @Override
    public String getString(String key) {
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
    public List<Object> values() {
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
