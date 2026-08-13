package com.falkordb.impl.resultset;

import com.falkordb.Statistics;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import redis.clients.jedis.util.SafeEncoder;

/**
 * Query result statistics interface implementation
 */
public class StatisticsImpl implements Statistics {
    // members
    private final Map<Statistics.Label, String> statistics;

    /**
     * A raw representation of query execution statistics is a list of strings
     * (byte arrays which need to be de-serialized).
     * Each string is built in the form of "K:V" where K is statistics label and V is its value.
     * @param raw a raw representation of the query execution statistics
     */
    public StatisticsImpl(List<byte[]> raw) {
        // Parsed once, up front: the raw reply is a handful of short strings, and an immutable map
        // keeps this result type safe to hand across threads (see AsyncGraph) and gives equals and
        // hashCode stable value semantics, which a List<byte[]> cannot provide.
        this.statistics = Collections.unmodifiableMap(parse(raw));
    }

    private static Map<Statistics.Label, String> parse(List<byte[]> raw) {
        Map<Statistics.Label, String> parsed = new EnumMap<>(Statistics.Label.class);
        for (byte[] tuple : raw) {
            String text = SafeEncoder.encode(tuple);
            // Only the FIRST colon delimits label from value; the value itself may contain colons.
            String[] rowTuple = text.split(":", 2);
            if (rowTuple.length == 2) {
                Statistics.Label label = Statistics.Label.getEnum(rowTuple[0]);
                if (label != null) {
                    parsed.put(label, rowTuple[1].trim());
                }
            }
        }
        return parsed;
    }

    /**
     *
     * @param label the requested statistic label as key
     * @return a string with the value, if key exists, null otherwise
     */
    @Override
    public String getStringValue(Statistics.Label label) {
        return statistics.get(label);
    }

    /**
     *
     * @param label the requested statistic label as key
     * @return a string with the value, if key exists, 0 otherwise
     */
    public int getIntValue(Statistics.Label label) {
        String value = getStringValue(label);
        return value == null ? 0 : Integer.parseInt(value);
    }

    /**
     *
     * @return number of nodes created after query execution
     */
    @Override
    public int nodesCreated() {
        return getIntValue(Label.NODES_CREATED);
    }

    /**
     *
     * @return number of nodes deleted after query execution
     */
    @Override
    public int nodesDeleted() {
        return getIntValue(Label.NODES_DELETED);
    }

    /**
     *
     * @return number of indices added after query execution
     */
    @Override
    public int indicesAdded() {
        return getIntValue(Label.INDICES_ADDED);
    }

    @Override
    public int indicesDeleted() {
        return getIntValue(Label.INDICES_DELETED);
    }

    /**
     *
     * @return number of labels added after query execution
     */
    @Override
    public int labelsAdded() {
        return getIntValue(Label.LABELS_ADDED);
    }

    /**
     *
     * @return number of relationship deleted after query execution
     */
    @Override
    public int relationshipsDeleted() {
        return getIntValue(Label.RELATIONSHIPS_DELETED);
    }

    /**
     *
     * @return number of relationship created after query execution
     */
    @Override
    public int relationshipsCreated() {
        return getIntValue(Label.RELATIONSHIPS_CREATED);
    }

    /**
     *
     * @return number of properties set after query execution
     */
    @Override
    public int propertiesSet() {
        return getIntValue(Label.PROPERTIES_SET);
    }

    /**
     *
     * @return The execution plan was cached on Graph.
     */
    @Override
    public boolean cachedExecution() {
        return getIntValue(Label.CACHED_EXECUTION) == 1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StatisticsImpl)) return false;
        StatisticsImpl that = (StatisticsImpl) o;
        return Objects.equals(statistics, that.statistics);
    }

    @Override
    public int hashCode() {
        return Objects.hash(statistics);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("StatisticsImpl{");
        sb.append("statistics=").append(statistics);
        sb.append('}');
        return sb.toString();
    }
}
