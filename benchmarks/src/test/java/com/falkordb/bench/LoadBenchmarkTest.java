package com.falkordb.bench;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.falkordb.bench.LoadBenchmark.CurveRow;
import com.falkordb.bench.LoadBenchmark.Metric;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** Unit tests for the pure calculation + serialization logic of the load benchmark. */
class LoadBenchmarkTest {

    @Test
    void percentileUsIndexesTheSortedNanosAndConvertsToMicros() {
        long[] sorted = new long[100];
        for (int i = 0; i < 100; i++) {
            sorted[i] = (i + 1) * 1000L; // 1..100 us, expressed in ns
        }
        assertEquals(50.0, LoadBenchmark.percentileUs(sorted, 50), 1e-9);
        assertEquals(95.0, LoadBenchmark.percentileUs(sorted, 95), 1e-9);
        assertEquals(99.0, LoadBenchmark.percentileUs(sorted, 99), 1e-9);
        assertEquals(0.0, LoadBenchmark.percentileUs(new long[0], 50), 1e-9);
    }

    @Test
    void parseLoadsAcceptsTrimmedPositiveIntegers() {
        assertArrayEquals(new int[] {1, 8, 64}, LoadBenchmark.parseLoads("1, 8 ,64"));
    }

    @Test
    void parseLoadsRejectsNonIntegerNonPositiveAndEmpty() {
        assertThrows(IllegalArgumentException.class, () -> LoadBenchmark.parseLoads("1,abc"));
        assertThrows(IllegalArgumentException.class, () -> LoadBenchmark.parseLoads("1,0"));
        assertThrows(IllegalArgumentException.class, () -> LoadBenchmark.parseLoads("-4"));
        assertThrows(IllegalArgumentException.class, () -> LoadBenchmark.parseLoads(""));
    }

    @Test
    void writeJsonUsesDotDecimalRegardlessOfDefaultLocale() throws Exception {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY); // a decimal-comma locale
            Path tmp = Files.createTempFile("bench-latency", ".json");
            LoadBenchmark.writeJson(tmp, Collections.singletonList(new Metric("client_p50 @load=1", "us", 1234.5)));
            String json = new String(Files.readAllBytes(tmp));
            assertTrue(json.contains("1234.500"), json);
            assertFalse(json.contains("1234,500"), json);
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void writeCurveJsonUsesDotDecimalRegardlessOfDefaultLocale() throws Exception {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            Path tmp = Files.createTempFile("bench-curve", ".json");
            LoadBenchmark.writeCurveJson(tmp, List.of(new CurveRow(8, 12744.0, 468.5, 682.6, 813.1)));
            String json = new String(Files.readAllBytes(tmp));
            assertTrue(json.contains("12744.000"), json);
            assertFalse(json.contains("12744,000"), json);
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void parseServerNanosParsesTheReportedFormat() {
        assertEquals(500000L, LoadBenchmark.parseServerNanos("0.5 milliseconds"));
        assertEquals(2000000L, LoadBenchmark.parseServerNanos("2 milliseconds"));
        assertEquals(250000L, LoadBenchmark.parseServerNanos("0.25")); // no unit suffix
        assertEquals(1500000L, LoadBenchmark.parseServerNanos("  1.5 milliseconds  ")); // surrounding whitespace
        assertTrue(LoadBenchmark.parseServerNanos("0.229334 milliseconds") > 0); // realistic value
    }

    @Test
    void parseServerNanosFailsFastOnMissingOrUnparseableValue() {
        String[] bad = {null, "", "   ", "milliseconds", "abc", "1.2.3", "ms 0.5", "N/A", "0x10"};
        for (String value : bad) {
            assertThrows(
                    IllegalStateException.class,
                    () -> LoadBenchmark.parseServerNanos(value),
                    "expected failure for input: " + value);
        }
    }
}
