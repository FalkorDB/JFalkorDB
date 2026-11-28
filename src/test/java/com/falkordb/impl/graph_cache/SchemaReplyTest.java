package com.falkordb.impl.graph_cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.falkordb.exceptions.GraphException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.util.KeyValue;
import redis.clients.jedis.util.SafeEncoder;

/**
 * Reading the schema element FalkorDB 6.2+ appends to a reply, in the two encodings: RESP2's flat list and RESP3's
 * map, which Jedis reads as a list of {@link KeyValue} pairs. The fixtures mirror what the server sends.
 */
class SchemaReplyTest {

    /** The hash the server sent for {@code (:Person:City)-[:LIVES_IN]} with keys {@code name, age}: all 64 bits. */
    private static final long HASH = -1146456808972410223L;

    @Test
    void readsAHashOnlyElementInBothEncodings() {
        for (Object element : Arrays.asList(resp2("hash", HASH), resp3("hash", HASH))) {
            SchemaReply reply = SchemaReply.parse(element);
            assertNotNull(reply, String.valueOf(element));
            assertEquals(HASH, reply.hash());
            assertNull(reply.names());
        }
    }

    @Test
    void readsTheNamesInBothEncodings() {
        Object[] pairs = {
            "hash", HASH,
            "labels", strings("Person", "City"),
            "relationshipTypes", strings("LIVES_IN"),
            "propertyKeys", strings("name", "age")
        };
        for (Object element : Arrays.asList(resp2(pairs), resp3(pairs))) {
            SchemaNames names = SchemaReply.parse(element).names();
            assertEquals(
                    new SchemaNames(
                            HASH,
                            Arrays.asList("Person", "City"),
                            Collections.singletonList("LIVES_IN"),
                            Arrays.asList("name", "age")),
                    names);
        }
    }

    @Test
    void readsEmptyListsAndKeysInAnyOrder() {
        Object[] pairs = {"propertyKeys", strings(), "hash", 0L, "relationshipTypes", strings(), "labels", strings()};
        for (Object element : Arrays.asList(
                resp3(pairs),
                resp2("hash", 0L, "labels", strings(), "relationshipTypes", strings(), "propertyKeys", strings()))) {
            SchemaReply reply = SchemaReply.parse(element);
            assertEquals(0L, reply.hash());
            assertEquals(
                    new SchemaNames(0L, Collections.emptyList(), Collections.emptyList(), Collections.emptyList()),
                    reply.names());
        }
    }

    @Test
    void readsResp2KeysInAnyOrderWithTheHashNotFirst() {
        // the RESP2 element is a flat list, so a key other than hash may come first
        SchemaReply reply = SchemaReply.parse(resp2(
                "labels", strings("Person"),
                "hash", 1L,
                "relationshipTypes", strings(),
                "propertyKeys", strings("name")));
        assertNotNull(reply);
        assertEquals(1L, reply.hash());
        assertEquals(Collections.singletonList("Person"), reply.names().labels());

        SchemaReply hashLast = SchemaReply.parse(resp2("future", strings("x"), "hash", 7L));
        assertEquals(7L, hashLast.hash());
        assertNull(hashLast.names());
    }

    @Test
    void ignoresAKeyALaterServerMayAdd() {
        for (Object element :
                Arrays.asList(resp2("hash", 7L, "future", strings("x")), resp3("future", 1L, "hash", 7L))) {
            SchemaReply reply = SchemaReply.parse(element);
            assertEquals(7L, reply.hash());
            assertNull(reply.names());
        }
    }

    @Test
    void anythingElseIsNotASchemaElement() {
        List<Object> notElements = Arrays.asList(
                null,
                bytes("Nodes created: 1"),
                Collections.emptyList(),
                // statistics
                Arrays.asList(bytes("Nodes created: 1"), bytes("Query internal execution time: 0.1 milliseconds")),
                // a header
                Arrays.asList(Arrays.asList(1L, bytes("n"))),
                // a RESP3 map without a hash
                Collections.singletonList(new KeyValue<>(bytes("labels"), strings())));
        for (Object element : notElements) {
            assertNull(SchemaReply.parse(element), String.valueOf(element));
        }
    }

    @Test
    void aHashThatIsNotAnIntegerIsAnErrorInBothEncodings() {
        // not read as statistics instead, which would fail with a ClassCastException
        for (Object element : Arrays.asList(resp2("hash", bytes("1")), resp3("hash", bytes("1")))) {
            GraphException e = assertThrows(GraphException.class, () -> SchemaReply.parse(element));
            assertTrue(e.getMessage().contains("hash must be an integer"), e.getMessage());
        }
    }

    @Test
    void aFlatListLedByHashThatIsNotPairsIsAnError() {
        GraphException e = assertThrows(
                GraphException.class, () -> SchemaReply.parse(Arrays.asList(bytes("hash"), 1L, bytes("labels"))));
        assertTrue(e.getMessage().contains("key-value pairs"), e.getMessage());
    }

    @Test
    void namesThatAreNotStringListsAreAnError() {
        Object[][] malformed = {
            {"hash", 1L, "labels", 3L, "relationshipTypes", strings(), "propertyKeys", strings()},
            {"hash", 1L, "labels", Arrays.asList(1L), "relationshipTypes", strings(), "propertyKeys", strings()},
        };
        for (Object[] pairs : malformed) {
            for (Object element : Arrays.asList(resp2(pairs), resp3(pairs))) {
                GraphException e = assertThrows(GraphException.class, () -> SchemaReply.parse(element));
                assertTrue(e.getMessage().contains("labels must"), e.getMessage());
            }
        }
    }

    @Test
    void someButNotAllOfTheListsIsAnError() {
        for (Object element : Arrays.asList(
                resp2("hash", 1L, "labels", strings("A")), resp3("hash", 1L, "propertyKeys", strings("a")))) {
            GraphException e = assertThrows(GraphException.class, () -> SchemaReply.parse(element));
            assertTrue(e.getMessage().contains("not all three"), e.getMessage());
        }
    }

    /** RESP2: the pairs as one flat list, keys as bulk strings. */
    static List<Object> resp2(Object... pairs) {
        requirePairs(pairs);
        List<Object> element = new ArrayList<>(pairs.length);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            element.add(bytes((String) pairs[i]));
            element.add(pairs[i + 1]);
        }
        return element;
    }

    /** RESP3: the pairs as Jedis reads a map, a list of key-value pairs. */
    static List<Object> resp3(Object... pairs) {
        requirePairs(pairs);
        List<Object> element = new ArrayList<>(pairs.length / 2);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            element.add(new KeyValue<>(bytes((String) pairs[i]), pairs[i + 1]));
        }
        return element;
    }

    private static void requirePairs(Object[] pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("key-value pairs expected, but got " + pairs.length + " items");
        }
    }

    static List<Object> strings(String... names) {
        List<Object> list = new ArrayList<>(names.length);
        for (String name : names) {
            list.add(bytes(name));
        }
        return list;
    }

    private static byte[] bytes(String text) {
        return SafeEncoder.encode(text);
    }
}
