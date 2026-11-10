package com.falkordb.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.statistics.Statistics;

/**
 * Property-based checks that {@link Utils#prepareQuery} always emits a well-formed, non-terminable
 * Cypher string literal — i.e. a generated value can never inject an unescaped {@code "} that would
 * end the literal early. (Exact round-trip fidelity against the real server is covered by
 * {@code ParameterRoundTripIT}.)
 */
class UtilsParamPropertyTest {

    private static final String PREFIX = "CYPHER `p`=";
    private static final Pattern DOTTED_IDENTIFIERS =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*");
    private static final String SUFFIX = " RETURN $p";

    @Property(tries = 2000)
    void encodedStringLiteralCannotBeTerminatedEarly(@ForAll("riskyStrings") String value) {
        Map<String, Object> params = new HashMap<>();
        params.put("p", value);
        String prepared = Utils.prepareQuery("RETURN $p", params);
        String literal = prepared.substring(PREFIX.length(), prepared.length() - SUFFIX.length());
        assertWellFormedStringLiteral(literal, value);
    }

    /**
     * The run-copying encoder must spell a value exactly as a character-by-character encoder does, and
     * reject exactly the values it rejects.
     */
    @Property(tries = 2000)
    void escapedValueMatchesTheCharacterByCharacterEncoder(@ForAll("encoderStrings") String value) {
        Map<String, Object> params = new HashMap<>();
        params.put("p", value);
        String expected = characterByCharacterLiteral(value);
        Statistics.label("outcome").collect(expected == null ? "rejected" : "encoded");
        if (expected == null) {
            assertThrows(IllegalArgumentException.class, () -> Utils.prepareQuery("RETURN $p", params));
            return;
        }
        String prepared = Utils.prepareQuery("RETURN $p", params);
        assertEquals(expected, prepared.substring(PREFIX.length(), prepared.length() - SUFFIX.length()));
    }

    /**
     * The oracle: one append per character, no run copying. Returns {@code null} for a value the encoder
     * must reject: one with a NUL or an unpaired surrogate.
     */
    private static String characterByCharacterLiteral(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\0':
                    return null;
                default:
                    if (Character.isHighSurrogate(c)
                            && i + 1 < value.length()
                            && Character.isLowSurrogate(value.charAt(i + 1))) {
                        sb.append(c).append(value.charAt(++i));
                    } else if (Character.isSurrogate(c)) {
                        return null;
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.append('"').toString();
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
        return riskyChars().list().ofMaxSize(48).map(list -> {
            StringBuilder sb = new StringBuilder(list.size());
            for (char c : list) {
                sb.append(c);
            }
            return sb.toString();
        });
    }

    /** Escapes and literal characters, without NULs or surrogates. */
    private static Arbitrary<Character> riskyChars() {
        return Arbitraries.frequencyOf(
                net.jqwik.api.Tuple.of(3, Arbitraries.of('\\', '"', '\n', '\r', '\t', '\b', '\f', '\'', '/')),
                net.jqwik.api.Tuple.of(2, Arbitraries.chars().range('\u0001', '\u007e')),
                net.jqwik.api.Tuple.of(1, Arbitraries.chars().range('\u00a1', '\ud7ff')),
                net.jqwik.api.Tuple.of(1, Arbitraries.chars().range('\ue000', '\uffff')));
    }

    /**
     * {@link #riskyStrings} with well-formed surrogate pairs mixed in, and now and then a NUL or a lone
     * surrogate, which the encoder must reject.
     */
    @Provide
    Arbitrary<String> encoderStrings() {
        Arbitrary<String> pieces = Arbitraries.frequencyOf(
                net.jqwik.api.Tuple.of(60, riskyChars().map(String::valueOf)),
                net.jqwik.api.Tuple.of(
                        10, Arbitraries.of("\ud83d\ude00", "\ud83e\udd80", "\ud800\udc00", "\udbff\udfff")),
                net.jqwik.api.Tuple.of(1, Arbitraries.of("\u0000", "\ud83d", "\ude00")));
        return pieces.list().ofMaxSize(48).map(list -> String.join("", list));
    }

    /**
     * A procedure name is accepted exactly when it is ASCII Cypher identifiers joined by dots, and is then
     * written unchanged; anything else is rejected with {@code IllegalArgumentException} before a query is
     * built. The oracle is a regex, independent of the character loop under test (the generated names are
     * short, so the regex's recursion is no concern here).
     */
    @Property(tries = 3000)
    void aProcedureNameIsAcceptedExactlyWhenItIsDottedIdentifiers(@ForAll("nameCandidates") String name) {
        boolean valid = isDottedIdentifiers(name);
        Statistics.collect(valid);
        Statistics.coverage(coverage -> {
            coverage.check(true).percentage(p -> p > 20);
            coverage.check(false).percentage(p -> p > 20);
        });
        if (valid) {
            assertEquals("CALL " + name + "()", Utils.prepareProcedure(name, Collections.emptyList(), new HashMap<>()));
        } else {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> Utils.prepareProcedure(name, Collections.emptyList(), new HashMap<>()));
        }
    }

    /** The same for a {@code YIELD} column, which must be a single identifier. */
    @Property(tries = 3000)
    void aYieldColumnIsAcceptedExactlyWhenItIsAnIdentifier(@ForAll("nameCandidates") String column) {
        boolean valid = column.indexOf('.') < 0 && isDottedIdentifiers(column);
        Statistics.collect(valid);
        Statistics.coverage(coverage -> {
            coverage.check(true).percentage(p -> p > 10);
            coverage.check(false).percentage(p -> p > 20);
        });
        Map<String, List<String>> kwargs = new HashMap<>();
        kwargs.put("y", Collections.singletonList(column));
        if (valid) {
            assertEquals("CALL p() YIELD " + column, Utils.prepareProcedure("p", Collections.emptyList(), kwargs));
        } else {
            assertThrows(
                    IllegalArgumentException.class, () -> Utils.prepareProcedure("p", Collections.emptyList(), kwargs));
        }
    }

    private static boolean isDottedIdentifiers(String s) {
        return DOTTED_IDENTIFIERS.matcher(s).matches();
    }

    /**
     * Half well-formed dotted names (one to four identifiers, mostly accepted), half those names with one
     * character inserted or replaced from an alphabet of near misses: separators, quoting, digits, non-ASCII
     * letters and digits that {@code Character.isLetter} would accept, line breaks and NUL.
     */
    @Provide
    Arbitrary<String> nameCandidates() {
        Arbitrary<String> identifier = Combinators.combine(
                        Arbitraries.strings().withChars("abcxyzABCXYZ_").ofLength(1),
                        Arbitraries.strings().withChars("abzAZ_0189").ofMaxLength(6))
                .as((first, rest) -> first + rest);
        Arbitrary<String> dotted = identifier.list().ofMinSize(1).ofMaxSize(4).map(parts -> String.join(".", parts));
        Arbitrary<Character> nearMiss = Arbitraries.of(
                '.', ' ', '`', '(', ')', ';', '-', '$', '0', '7', '\n', '\u2028', '\u0000', '\u00e9', '\u212A',
                '\u0663', '\uFF21', 'a', '_');
        Arbitrary<String> mutated = Combinators.combine(
                        dotted, Arbitraries.integers().between(0, 40), nearMiss, Arbitraries.of(true, false))
                .as((name, at, c, insert) -> {
                    int i = Math.min(at, name.length());
                    if (insert || i == name.length()) {
                        return name.substring(0, i) + c + name.substring(i);
                    }
                    return name.substring(0, i) + c + name.substring(i + 1);
                });
        return Arbitraries.oneOf(dotted, mutated);
    }
}
