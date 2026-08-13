package com.falkordb.impl.resultset;

import com.falkordb.Header;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.util.SafeEncoder;

/**
 * Query result header interface implementation
 */
// Final because the constructor can throw on a malformed reply: a partially-constructed non-final
// class is reachable via a subclass finalizer (SpotBugs CT_CONSTRUCTOR_THROW). ResultSetImpl is final
// for the same reason. Nothing extends this internal type.
public final class HeaderImpl implements Header {

    // members
    private static final ResultSetColumnTypes[] COLUMN_TYPES = ResultSetColumnTypes.values();

    private final List<ResultSetColumnTypes> schemaTypes;
    private final List<String> schemaNames;

    /**
     * Parameterized constructor
     * A raw representation of a header (query response schema) is a list.
     * Each entry in the list is a tuple (list of size 2).
     * tuple[0] represents the type of the column, and tuple[1] represents the name of the column.
     *
     * @param raw - raw representation of a header
     */
    public HeaderImpl(List<List<Object>> raw) {
        // Built once, up front, into immutable lists. Building lazily without synchronization let two
        // threads both observe an empty list and both append to it, duplicating every column — reachable
        // now that AsyncGraph hands a ResultSet to many threads.
        List<ResultSetColumnTypes> types = new ArrayList<>(raw.size());
        List<String> names = new ArrayList<>(raw.size());
        for (List<Object> tuple : raw) {
            types.add(columnType(((Long) tuple.get(0)).intValue()));
            names.add(SafeEncoder.encode((byte[]) tuple.get(1)));
        }
        this.schemaTypes = Collections.unmodifiableList(types);
        this.schemaNames = Collections.unmodifiableList(names);
    }

    /**
     * Resolves a server-supplied column-type ordinal, matching the guarded lookup
     * {@code ResultSetScalarTypes.getValue} performs for scalar types.
     */
    private static ResultSetColumnTypes columnType(int index) {
        try {
            return COLUMN_TYPES[index];
        } catch (IndexOutOfBoundsException e) {
            throw new JedisDataException("Unrecognized response type");
        }
    }

    /**
     * @return a list of column names, ordered by they appearance in the query
     */
    @Override
    public List<String> getSchemaNames() {
        return schemaNames;
    }

    /**
     * @return a list of column types, ordered by they appearance in the query
     */
    @Override
    public List<ResultSetColumnTypes> getSchemaTypes() {
        return schemaTypes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HeaderImpl)) return false;
        HeaderImpl header = (HeaderImpl) o;
        return Objects.equals(getSchemaTypes(), header.getSchemaTypes())
                && Objects.equals(getSchemaNames(), header.getSchemaNames());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getSchemaTypes(), getSchemaNames());
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("HeaderImpl{");
        sb.append("schemaTypes=").append(schemaTypes);
        sb.append(", schemaNames=").append(schemaNames);
        sb.append('}');
        return sb.toString();
    }
}
