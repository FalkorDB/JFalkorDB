package com.falkordb.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class UtilsTest {

    private static String q(Object value) {
        Map<String, Object> params = new HashMap<>();
        params.put("param", value);
        return Utils.prepareQuery("RETURN $param", params);
    }

    @Test
    public void testPrepareProcedure() {
        assertEquals("CALL prc()", Utils.prepareProcedure("prc", Arrays.asList(new String[] {}), new HashMap<>()));

        assertEquals(
                "CALL prc(\"a\",\"b\")",
                Utils.prepareProcedure("prc", Arrays.asList(new String[] {"a", "b"}), new HashMap<>()));

        Map<String, List<String>> kwargs = new HashMap<>();
        kwargs.put("y", Arrays.asList(new String[] {"ka", "kb"}));
        assertEquals(
                "CALL prc(\"a\",\"b\")ka,kb",
                Utils.prepareProcedure("prc", Arrays.asList(new String[] {"a", "b"}), kwargs));

        assertEquals("CALL prc()ka,kb", Utils.prepareProcedure("prc", Arrays.asList(new String[] {}), kwargs));
    }

    @Test
    public void testPrepareProcedureEscapesArgs() {
        // A backslash-terminated arg must be escaped so it can't break out of the quoted argument.
        assertEquals("CALL prc(\"a\\\\\")", Utils.prepareProcedure("prc", Arrays.asList("a\\"), new HashMap<>()));
    }

    @Test
    public void testParamsPrep() {
        // Parameter names are validated and backtick-quoted; values are encoded as Cypher literals.
        assertEquals("CYPHER `param`=\"\" RETURN $param", q(""));
        assertEquals("CYPHER `param`=\"\\\"\" RETURN $param", q("\""));
        assertEquals("CYPHER `param`=\"\\\"st\" RETURN $param", q("\"st"));
        assertEquals("CYPHER `param`=1 RETURN $param", q(1));
        assertEquals("CYPHER `param`=2.3 RETURN $param", q(2.3));
        assertEquals("CYPHER `param`=true RETURN $param", q(true));
        assertEquals("CYPHER `param`=false RETURN $param", q(false));
        assertEquals("CYPHER `param`=null RETURN $param", q(null));
        assertEquals("CYPHER `param`=\"str\" RETURN $param", q("str"));
        assertEquals("CYPHER `param`=\"s\\\"tr\" RETURN $param", q("s\"tr"));

        Integer[] arr = {1, 2, 3};
        assertEquals("CYPHER `param`=[1, 2, 3] RETURN $param", q(arr));
        assertEquals("CYPHER `param`=[1, 2, 3] RETURN $param", q(Arrays.asList(1, 2, 3)));

        String[] strArr = {"1", "2", "3"};
        assertEquals("CYPHER `param`=[\"1\", \"2\", \"3\"] RETURN $param", q(strArr));
        assertEquals("CYPHER `param`=[\"1\", \"2\", \"3\"] RETURN $param", q(Arrays.asList("1", "2", "3")));
    }

    @Test
    public void testEscapesBackslashAndControlChars() {
        // Backslash is escaped (the previous encoder left it raw, allowing a break-out / injection).
        assertEquals("CYPHER `param`=\"a\\\\\" RETURN $param", q("a\\"));
        assertEquals("CYPHER `param`=\"a\\\\\\\"b\" RETURN $param", q("a\\\"b"));
        assertEquals("CYPHER `param`=\"x\\ny\" RETURN $param", q("x\ny"));
        assertEquals("CYPHER `param`=\"x\\ty\" RETURN $param", q("x\ty"));
        assertEquals("CYPHER `param`=\"x\\r\\b\\f\" RETURN $param", q("x\r\b\f"));
        // A classic injection attempt is encoded as a plain string literal, not executed.
        String attack = "\" RETURN 1; MATCH (n) DETACH DELETE n //";
        assertEquals("CYPHER `param`=\"\\\" RETURN 1; MATCH (n) DETACH DELETE n //\" RETURN $param", q(attack));
    }

    @Test
    public void testKeepsUnicodeRaw() {
        assertEquals("CYPHER `param`=\"\u00e9\ud83d\ude00\" RETURN $param", q("\u00e9\ud83d\ude00"));
    }

    @Test
    public void testRejectsNulAndUnpairedSurrogates() {
        assertThrows(IllegalArgumentException.class, () -> q("a\u0000b"));
        assertThrows(IllegalArgumentException.class, () -> q("a\ud800b")); // unpaired high
        assertThrows(IllegalArgumentException.class, () -> q("a\udc00b")); // unpaired low
    }

    @Test
    public void testRejectsInvalidParameterNames() {
        for (String bad : new String[] {"", " ", "a b", "1a", "a-b", "a.b", "a`b", "a=b", "$a", "a\u0000"}) {
            Map<String, Object> params = new HashMap<>();
            params.put(bad, 1);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> Utils.prepareQuery("RETURN 1", params),
                    "expected rejection of parameter name: " + bad);
        }
    }

    @Test
    public void testBacktickQuotedNameDefeatsCypherPrefix() {
        // "CYPHER" is a valid identifier but would be misparsed if emitted bare in the header.
        Map<String, Object> params = new HashMap<>();
        params.put("CYPHER", 1);
        assertEquals("CYPHER `CYPHER`=1 RETURN $CYPHER", Utils.prepareQuery("RETURN $CYPHER", params));
    }

    @Test
    public void testNumericBounds() {
        assertEquals("CYPHER `param`=9223372036854775807 RETURN $param", q(Long.MAX_VALUE));
        assertEquals("CYPHER `param`=9223372036854775807 RETURN $param", q(BigInteger.valueOf(Long.MAX_VALUE)));
        // Long.MIN_VALUE is in range (both as a boxed long and a BigInteger).
        assertEquals("CYPHER `param`=-9223372036854775808 RETURN $param", q(Long.MIN_VALUE));
        assertEquals("CYPHER `param`=-9223372036854775808 RETURN $param", q(BigInteger.valueOf(Long.MIN_VALUE)));
        // Out-of-int64-range BigInteger (both signs) and non-finite doubles are rejected.
        assertThrows(
                IllegalArgumentException.class,
                () -> q(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)));
        assertThrows(
                IllegalArgumentException.class,
                () -> q(BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE)));
        assertThrows(IllegalArgumentException.class, () -> q(BigInteger.TEN.pow(30)));
        assertThrows(IllegalArgumentException.class, () -> q(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> q(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> q(Float.NEGATIVE_INFINITY));
    }

    @Test
    public void testRejectsUnsupportedTypes() {
        assertThrows(IllegalArgumentException.class, () -> q(new java.math.BigDecimal("1.5")));
        assertThrows(IllegalArgumentException.class, () -> q(new Object()));
    }

    @Test
    public void testMapEncoding() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("bareKey", 1);
        value.put("needs quote", "x");
        assertEquals("CYPHER `param`={bareKey: 1, `needs quote`: \"x\"} RETURN $param", q(value));

        // Non-String map keys, and keys containing a backtick, are rejected.
        Map<Object, Object> badKey = new HashMap<>();
        badKey.put(1, "v");
        assertThrows(IllegalArgumentException.class, () -> q(badKey));

        Map<String, Object> backtickKey = new HashMap<>();
        backtickKey.put("a`b", "v");
        assertThrows(IllegalArgumentException.class, () -> q(backtickKey));

        Map<String, Object> surrogateKey = new HashMap<>();
        surrogateKey.put("bad\ud800key", "v"); // unpaired high surrogate
        assertThrows(IllegalArgumentException.class, () -> q(surrogateKey));
    }

    @Test
    public void testPrepareProcedureEmptyKwargs() {
        // An empty "y" kwargs list must not crash (previously indexed get(0) on an empty list).
        Map<String, List<String>> kwargs = new HashMap<>();
        kwargs.put("y", new ArrayList<>());
        assertEquals("CALL prc()", Utils.prepareProcedure("prc", Arrays.asList(new String[] {}), kwargs));
    }

    @Test
    public void testRejectsCyclicContainers() {
        List<Object> cyclicList = new ArrayList<>();
        cyclicList.add(cyclicList);
        assertThrows(IllegalArgumentException.class, () -> q(cyclicList));

        Map<String, Object> cyclicMap = new HashMap<>();
        cyclicMap.put("self", cyclicMap);
        assertThrows(IllegalArgumentException.class, () -> q(cyclicMap));
    }

    @Test
    public void testExceptionMessagesEscapeControlChars() {
        // A rejected parameter name/key must not leak a raw newline into the exception message
        // (log-forging); safeDisplay escapes control characters.
        Map<String, Object> badName = new HashMap<>();
        badName.put("bad\nname", 1);
        IllegalArgumentException e1 =
                assertThrows(IllegalArgumentException.class, () -> Utils.prepareQuery("RETURN 1", badName));
        assertFalse(e1.getMessage().contains("\n"), "name message must not contain a raw newline");
        assertTrue(e1.getMessage().contains("\\u000a"));

        Map<String, Object> badKey = new HashMap<>();
        badKey.put("k\r`", "v");
        IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class, () -> q(badKey));
        assertFalse(e2.getMessage().contains("\r"), "key message must not contain a raw carriage return");

        // Also escape C1 controls (U+0085 NEL) and Unicode line/paragraph separators (U+2028/U+2029).
        Map<String, Object> unicodeName = new HashMap<>();
        unicodeName.put("bad\u2028\u0085name", 1);
        IllegalArgumentException e3 =
                assertThrows(IllegalArgumentException.class, () -> Utils.prepareQuery("RETURN 1", unicodeName));
        assertFalse(e3.getMessage().contains("\u2028"), "message must not contain a raw line separator");
        assertFalse(e3.getMessage().contains("\u0085"), "message must not contain a raw NEL");
    }

    @Test
    public void testSharedNonCyclicReferenceIsAllowed() {
        // The same (non-cyclic) list appearing twice as siblings must not be flagged as a cycle.
        List<Integer> shared = Arrays.asList(1, 2);
        List<Object> outer = new ArrayList<>();
        outer.add(shared);
        outer.add(shared);
        assertTrue(q(outer).contains("[[1, 2], [1, 2]]"));
    }
}
