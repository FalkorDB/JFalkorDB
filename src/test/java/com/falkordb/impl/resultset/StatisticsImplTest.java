package com.falkordb.impl.resultset;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.falkordb.Statistics;
import java.util.ArrayList;
import java.util.List;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.util.SafeEncoder;

public class StatisticsImplTest {

    private static StatisticsImpl statisticsOf(String... lines) {
        List<byte[]> raw = new ArrayList<>(lines.length);
        for (String line : lines) {
            raw.add(SafeEncoder.encode(line));
        }
        return new StatisticsImpl(raw);
    }

    @Test
    public void equalsHashCodeContract() {
        // StatisticsImpl is an internal, non-final result type compared with instanceof. Its statistics
        // map is always constructor-initialized (never null in practice), so NULL_FIELDS is safe.
        EqualsVerifier.forClass(StatisticsImpl.class)
                .suppress(Warning.NONFINAL_FIELDS, Warning.STRICT_INHERITANCE, Warning.NULL_FIELDS)
                .verify();
    }

    @Test
    public void equalStatisticsFromDistinctByteArraysAreEqual() {
        // Two responses carrying identical statistics must compare equal and hash alike. The raw
        // reply is a List<byte[]>, and byte[] inherits identity equals/hashCode, so including it in
        // equals/hashCode made value-equal statistics unequal and hashCode vary between instances.
        StatisticsImpl first = statisticsOf("Nodes created: 2", "Properties set: 4");
        StatisticsImpl second = statisticsOf("Nodes created: 2", "Properties set: 4");

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    public void keepsStatisticValueContainingColon() {
        // Each raw entry is "K:V" with the FIRST colon as the delimiter; the value may itself contain
        // colons. Splitting on every colon yielded 3 parts, failed the length==2 guard, and dropped
        // the statistic entirely.
        StatisticsImpl statistics = statisticsOf("Query internal execution time: 0:00.4");

        assertEquals("0:00.4", statistics.getStringValue(Statistics.Label.QUERY_INTERNAL_EXECUTION_TIME));
    }

    @Test
    public void unrecognizedAndMalformedEntriesAreIgnored() {
        // An entry with no colon, and one whose label is unknown, must not abort parsing of the rest.
        StatisticsImpl statistics = statisticsOf("no delimiter here", "Totally unknown label: 7", "Nodes created: 3");

        assertEquals(3, statistics.nodesCreated());
    }
}
