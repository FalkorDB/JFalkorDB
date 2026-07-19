package com.falkordb.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based checks that {@link Utils#prepareQuery} always emits a well-formed, non-terminable
 * Cypher string literal — i.e. a generated value can never inject an unescaped {@code "} that would
 * end the literal early. (Exact round-trip fidelity against the real server is covered by
 * {@code ParameterRoundTripIT}.)
 */
class UtilsParamPropertyTest {

    private static final String PREFIX = "CYPHER `p`=";
    private static final String SUFFIX = " RETURN $p";

    @Property(tries = 2000)
    void encodedStringLiteralCannotBeTerminatedEarly(@ForAll("riskyStrings") String value) {
        Map<String, Object> params = new HashMap<>();
        params.put("p", value);
        String prepared = Utils.prepareQuery("RETURN $p", params);
        String literal = prepared.substring(PREFIX.length(), prepared.length() - SUFFIX.length());
        assertWellFormedStringLiteral(literal, value);
    }

    private static void assertWellFormedStringLiteral(String literal, String source) {
        assertEquals('"', literal.charAt(0), () -> "literal must open with a quote for: " + source);
        assertEquals('"', literal.charAt(literal.length() - 1), () -> "literal must close with a quote for: " + source);
        int i = 1;
        int last = literal.length() - 1;
        while (i < last) {
            char c = literal.charAt(i);
            if (c == '\\') {
                // A backslash always introduces a two-char escape; skip its target.
                i += 2;
            } else if (c == '"') {
                throw new AssertionError("unescaped quote at index " + i + " would terminate the literal early "
                        + "for input: " + source);
            } else {
                i++;
            }
        }
        // A well-formed literal consumes exactly up to the closing quote (no dangling escape).
        assertEquals(last, i, () -> "dangling escape before closing quote for: " + source);
    }

    /**
     * Strings biased toward the dangerous characters (backslash, quote, control chars) but excluding
     * NUL and surrogates, which the encoder rejects outright (covered by {@code UtilsTest}).
     */
    @Provide
    Arbitrary<String> riskyStrings() {
        Arbitrary<Character> chars = Arbitraries.frequencyOf(
                net.jqwik.api.Tuple.of(3, Arbitraries.of('\\', '"', '\n', '\r', '\t', '\b', '\f', '\'', '/')),
                net.jqwik.api.Tuple.of(2, Arbitraries.chars().range('\u0001', '\u007e')),
                net.jqwik.api.Tuple.of(1, Arbitraries.chars().range('\u00a1', '\ud7ff')),
                net.jqwik.api.Tuple.of(1, Arbitraries.chars().range('\ue000', '\uffff')));
        return chars.list().ofMaxSize(48).map(list -> {
            StringBuilder sb = new StringBuilder(list.size());
            for (char c : list) {
                sb.append(c);
            }
            return sb.toString();
        });
    }
}
