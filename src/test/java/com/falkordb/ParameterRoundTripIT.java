package com.falkordb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.falkordb.graph_entities.Node;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * End-to-end verification that {@link com.falkordb.impl.Utils#prepareQuery} encodes parameters so
 * that arbitrary values round-trip through a real FalkorDB server unchanged, and so that
 * attacker-controlled string values cannot break out of the string literal and inject Cypher.
 */
public class ParameterRoundTripIT {

    private GraphContextGenerator client;

    @BeforeEach
    public void createApi() {
        client = TestServer.graph("param-roundtrip");
    }

    @AfterEach
    public void deleteGraph() {
        client.deleteGraph();
        client.close();
    }

    private Object roundTrip(Object value) {
        ResultSet rs = client.query("RETURN $p AS v", Collections.singletonMap("p", value));
        Iterator<Record> it = rs.iterator();
        assertTrue(it.hasNext(), "expected a row");
        return it.next().getValue("v");
    }

    @Test
    public void stringsRoundTripExactly() {
        String[] values = {
            "",
            "simple",
            "with \"double quotes\"",
            "back\\slash",
            "trailing-backslash\\",
            "\\\"", // backslash then quote — the classic break-out sequence
            "line\nbreak\ttab\rreturn",
            "control\u0001\u001b\u0007chars",
            "café ☕ 😀 — Unicode",
        };
        for (String v : values) {
            assertEquals(v, roundTrip(v), () -> "round-trip mismatch for: " + escape(v));
        }
    }

    @Test
    public void scalarsRoundTrip() {
        assertEquals(true, roundTrip(true));
        assertEquals(false, roundTrip(false));
        assertEquals(null, roundTrip(null));
        assertEquals(42L, ((Number) roundTrip(42)).longValue());
        assertEquals(Long.MAX_VALUE, ((Number) roundTrip(Long.MAX_VALUE)).longValue());
        assertEquals(-7L, ((Number) roundTrip(-7)).longValue());
        assertEquals(1.5, ((Number) roundTrip(1.5)).doubleValue(), 0.0);
        assertEquals(1.0e10, ((Number) roundTrip(1.0e10)).doubleValue(), 0.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void listRoundTrips() {
        Object out = roundTrip(Arrays.asList(1, "two", true));
        List<Object> list = (List<Object>) out;
        assertEquals(3, list.size());
        assertEquals(1L, ((Number) list.get(0)).longValue());
        assertEquals("two", list.get(1));
        assertEquals(true, list.get(2));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void mapRoundTrips() {
        Map<String, Object> in = new LinkedHashMap<>();
        in.put("bareKey", 1);
        in.put("needs quoting!", "value with \" quote");
        Object out = roundTrip(in);
        Map<String, Object> map = (Map<String, Object>) out;
        assertEquals(1L, ((Number) map.get("bareKey")).longValue());
        assertEquals("value with \" quote", map.get("needs quoting!"));
    }

    @ParameterizedTest(name = "integer {0} round-trips as long")
    @MethodSource("integerBoundaries")
    public void integerBoundariesRoundTrip(Object input, long expected) {
        assertEquals(expected, ((Number) roundTrip(input)).longValue());
    }

    static Stream<Arguments> integerBoundaries() {
        return Stream.of(
                Arguments.of(0, 0L),
                Arguments.of(-1, -1L),
                Arguments.of(Integer.MIN_VALUE, (long) Integer.MIN_VALUE),
                Arguments.of(Integer.MAX_VALUE, (long) Integer.MAX_VALUE),
                Arguments.of(Long.MIN_VALUE, Long.MIN_VALUE),
                Arguments.of(Long.MAX_VALUE, Long.MAX_VALUE));
    }

    @ParameterizedTest(name = "double {0} round-trips")
    @MethodSource("doubleBoundaries")
    public void doubleBoundariesRoundTrip(double input) {
        assertEquals(input, ((Number) roundTrip(input)).doubleValue(), 0.0);
    }

    static Stream<Double> doubleBoundaries() {
        // FalkorDB returns doubles with ~15 significant digits and a finite range: full-precision values
        // such as Math.PI (17 digits) come back rounded, and Double.MAX_VALUE overflows to Infinity. This
        // matrix therefore exercises representative magnitudes/signs that round-trip within that fidelity.
        return Stream.of(0.0, 1.5, -1.5, 0.25, 1.0e-10, 1.0e10, 6.022e23, -2.5e-100);
    }

    @ParameterizedTest(name = "unicode [{index}] round-trips")
    @MethodSource("unicodeStrings")
    public void unicodeStringsRoundTrip(String value) {
        assertEquals(value, roundTrip(value), () -> "round-trip mismatch for: " + escape(value));
    }

    static Stream<String> unicodeStrings() {
        return Stream.of(
                "café ☕ 😀", // accents, symbol, astral emoji
                "日本語のテキスト", // CJK
                "Ålesund Øystein Þórr", // Nordic letters
                "family 👨‍👩‍👧‍👦 zwj", // ZWJ emoji sequence
                "flag 🇮🇱 regional", // regional-indicator surrogate pairs
                "combining e\u0301 = é", // combining acute accent
                "rtl مرحبا שלום ltr", // bidirectional scripts
                "astral 𝕏 𝟛 math"); // astral-plane math alphanumerics
    }

    @Test
    @SuppressWarnings("unchecked")
    public void emptyAndNestedCollectionsRoundTrip() {
        assertTrue(((List<Object>) roundTrip(Collections.emptyList())).isEmpty());
        assertTrue(((Map<String, Object>) roundTrip(Collections.emptyMap())).isEmpty());

        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("nums", Arrays.asList(1, 2));
        Object out = roundTrip(Arrays.asList(Collections.emptyList(), inner));
        List<Object> list = (List<Object>) out;
        assertEquals(2, list.size());
        assertTrue(((List<Object>) list.get(0)).isEmpty());
        Map<String, Object> outMap = (Map<String, Object>) list.get(1);
        assertEquals(2, ((List<Object>) outMap.get("nums")).size());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void nullInsideCollectionsRoundTrips() {
        List<Object> list = (List<Object>) roundTrip(Arrays.asList("a", null, 3));
        assertEquals(3, list.size());
        assertEquals("a", list.get(0));
        assertNull(list.get(1));
        assertEquals(3L, ((Number) list.get(2)).longValue());
    }

    @Test
    public void injectionPayloadIsInertData() {
        // Seed a sentinel node.
        client.query("CREATE (:Sentinel {id: 1})");

        // A value crafted to break out of the string literal and delete all nodes.
        String payload = "\\\" RETURN 1 AS x //\n MATCH (n) DETACH DELETE n //";
        Object echoed = roundTrip(payload);
        assertEquals(payload, echoed, "payload must be returned verbatim, proving it was inert data");

        // The sentinel must still be present — the injected DETACH DELETE never ran.
        ResultSet rs = client.query("MATCH (n:Sentinel) RETURN count(n) AS c");
        long count = ((Number) rs.iterator().next().getValue("c")).longValue();
        assertEquals(1L, count, "the injection payload must not have deleted the sentinel node");
    }

    @Test
    public void injectionViaMatchPropertyIsPrevented() {
        client.query("CREATE (:Person {name: 'Alice'})");
        // If the value were concatenated raw, this would match/return everything; parameterized, it
        // is a literal name that matches nobody.
        String malicious = "Alice'}) DETACH DELETE (n) //";
        ResultSet rs = client.query(
                "MATCH (p:Person {name: $name}) RETURN count(p) AS c", Collections.singletonMap("name", malicious));
        long matched = ((Number) rs.iterator().next().getValue("c")).longValue();
        assertEquals(0L, matched, "no person is literally named the injection string");

        // Alice must still exist.
        ResultSet after = client.query("MATCH (p:Person) RETURN count(p) AS c");
        long remaining = ((Number) after.iterator().next().getValue("c")).longValue();
        assertEquals(1L, remaining);
    }

    @Test
    public void nodeParameterIsStillUsableAfterHardening() {
        // Sanity: an ordinary parameterized create/read cycle still works.
        client.query("CREATE (:T {name: $n})", Collections.singletonMap("n", "hello"));
        ResultSet rs = client.query("MATCH (t:T {name: $n}) RETURN t", Collections.singletonMap("n", "hello"));
        Iterator<Record> it = rs.iterator();
        assertTrue(it.hasNext());
        Node node = it.next().getValue("t");
        assertEquals("hello", node.getProperty("name").getValue());
        assertFalse(it.hasNext());
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 || c > 0x7e) {
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
