package com.falkordb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.Iterator;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.api.lifecycle.BeforeProperty;

/**
 * Property-based end-to-end round-trip: a generated value sent as a parameter must come back from a
 * real FalkorDB server unchanged. This is the generative, full serialize→server→deserialize counterpart
 * to {@link ParameterRoundTripIT} (fixed cases) and {@code UtilsParamPropertyTest} (serialize-side
 * well-formedness only, no server) — so an escaping bug that produced a well-formed but semantically
 * wrong literal would be caught here.
 */
class ParamRoundTripPropertyIT {

    private GraphContextGenerator client;

    @BeforeProperty
    void createApi() {
        client = TestServer.graph("param-roundtrip-prop");
    }

    @AfterProperty
    void deleteGraph() {
        if (client != null) {
            try {
                client.deleteGraph();
            } finally {
                client.close();
                client = null;
            }
        }
    }

    private Object roundTrip(Object value) {
        ResultSet rs = client.query("RETURN $p AS v", Collections.singletonMap("p", value));
        Iterator<Record> it = rs.iterator();
        assertTrue(it.hasNext(), "expected a row");
        return it.next().getValue("v");
    }

    @Property(tries = 300)
    void arbitraryStringRoundTripsThroughServer(@ForAll("safeStrings") String value) {
        assertEquals(value, roundTrip(value));
    }

    @Property(tries = 200)
    void arbitraryLongRoundTripsThroughServer(@ForAll long value) {
        assertEquals(value, ((Number) roundTrip(value)).longValue());
    }

    /**
     * Strings biased toward the dangerous characters (backslash, quote, control chars), drawn from the
     * BMP but excluding NUL and the surrogate range — so the generator never emits an unpaired
     * surrogate, which (along with NUL) is what the encoder rejects (see {@code UtilsTest}). Valid
     * surrogate pairs (e.g. emoji) round-trip and are exercised by {@code ParameterRoundTripIT}.
     */
    @Provide
    Arbitrary<String> safeStrings() {
        Arbitrary<Character> chars = Arbitraries.frequencyOf(
                Tuple.of(3, Arbitraries.of('\\', '"', '\n', '\r', '\t', '\b', '\f', '\'', '/')),
                Tuple.of(2, Arbitraries.chars().range('\u0001', '~')),
                Tuple.of(1, Arbitraries.chars().range('\u00a1', '\ud7ff')),
                Tuple.of(1, Arbitraries.chars().range('\ue000', '\uffff')));
        return chars.list().ofMaxSize(64).map(list -> {
            StringBuilder sb = new StringBuilder(list.size());
            for (char c : list) {
                sb.append(c);
            }
            return sb.toString();
        });
    }
}
