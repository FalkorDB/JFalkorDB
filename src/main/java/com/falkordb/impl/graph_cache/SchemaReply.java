package com.falkordb.impl.graph_cache;

import com.falkordb.exceptions.GraphException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import redis.clients.jedis.util.SafeEncoder;

/**
 * The schema element FalkorDB 6.2 and later adds, last, to the reply of a query sent with {@code SCHEMA_HASH}: the
 * graph's schema hash, and the names too when the hash the query sent was not the current one.
 *
 * <p>RESP3 sends it as a map, which Jedis reads as a list of {@link Map.Entry} pairs; RESP2 sends the same pairs as a
 * flat list, {@code ["hash", S, "labels", [...], "relationshipTypes", [...], "propertyKeys", [...]]}. Keys are read
 * by name, in any order, and keys this client doesn't know are ignored, so a later server can add some.
 */
public final class SchemaReply {

    static final String HASH = "hash";
    static final String LABELS = "labels";
    static final String RELATIONSHIP_TYPES = "relationshipTypes";
    static final String PROPERTY_KEYS = "propertyKeys";

    private final long hash;
    private final @Nullable SchemaNames names;

    private SchemaReply(long hash, @Nullable SchemaNames names) {
        this.hash = hash;
        this.names = names;
    }

    /**
     * Returns the graph's current schema hash.
     *
     * @return the hash
     */
    public long hash() {
        return hash;
    }

    /**
     * Returns the names the hash stands for, or null when the reply sent only the hash.
     *
     * @return the names, or null
     */
    public @Nullable SchemaNames names() {
        return names;
    }

    /**
     * Reads a reply's last element as a schema element.
     *
     * @param element the last element of a graph reply
     * @return the schema element, or null when {@code element} isn't one, as on a server older than 6.2
     * @throws GraphException if {@code element} is a schema element whose values have the wrong types, or that
     *     carries some of the three name lists but not all of them
     */
    public static @Nullable SchemaReply parse(@Nullable Object element) {
        if (!(element instanceof List)) {
            return null;
        }
        List<?> items = (List<?>) element;
        if (items.isEmpty()) {
            return null;
        }
        if (items.get(0) instanceof Map.Entry) {
            // RESP3: no other top-level element of a graph reply is a map.
            return hasHashEntry(items) ? fromEntries(items) : null;
        }
        // RESP2: no other last element of a graph reply has the bare string "hash" at a key position: statistics
        // hold only "Name: value" strings, and a header or rows hold lists. So a list that does is a schema element,
        // whatever the order of its keys, and a value of the wrong type in it is an error rather than a reason to
        // read it as statistics.
        if (!hasHashKey(items)) {
            return null;
        }
        if (items.size() % 2 != 0) {
            throw new GraphException("Malformed schema element in the reply: " + items.size() + " items do not form"
                    + " key-value pairs");
        }
        Fields fields = new Fields();
        for (int i = 0; i < items.size(); i += 2) {
            fields.put(items.get(i), items.get(i + 1));
        }
        return fields.toReply();
    }

    /** Whether a RESP2 flat list has the key {@code hash} at a key position: 0, 2, 4, ... */
    private static boolean hasHashKey(List<?> items) {
        for (int i = 0; i < items.size(); i += 2) {
            if (isHashKey(items.get(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasHashEntry(List<?> items) {
        for (Object item : items) {
            if (!(item instanceof Map.Entry)) {
                return false;
            }
            if (isHashKey(((Map.Entry<?, ?>) item).getKey())) {
                return true;
            }
        }
        return false;
    }

    private static SchemaReply fromEntries(List<?> entries) {
        Fields fields = new Fields();
        for (Object item : entries) {
            if (!(item instanceof Map.Entry)) {
                throw new GraphException(
                        "Malformed schema element in the reply: a map entry was expected, but got " + typeOf(item));
            }
            Map.Entry<?, ?> entry = (Map.Entry<?, ?>) item;
            fields.put(entry.getKey(), entry.getValue());
        }
        return fields.toReply();
    }

    private static boolean isHashKey(@Nullable Object key) {
        return HASH.equals(text(key));
    }

    private static @Nullable String text(@Nullable Object value) {
        if (value instanceof byte[]) {
            return SafeEncoder.encode((byte[]) value);
        }
        return value instanceof String ? (String) value : null;
    }

    private static String typeOf(@Nullable Object value) {
        return value == null ? "nil" : value.getClass().getSimpleName();
    }

    /** The values of the keys this client knows, as they are read. */
    private static final class Fields {
        private @Nullable Object hash;
        private @Nullable List<String> labels;
        private @Nullable List<String> relationshipTypes;
        private @Nullable List<String> propertyKeys;

        void put(@Nullable Object rawKey, @Nullable Object value) {
            String key = text(rawKey);
            if (key == null) {
                throw new GraphException(
                        "Malformed schema element in the reply: a key must be a string, but got " + typeOf(rawKey));
            }
            switch (key) {
                case HASH:
                    hash = value;
                    break;
                case LABELS:
                    labels = names(key, value);
                    break;
                case RELATIONSHIP_TYPES:
                    relationshipTypes = names(key, value);
                    break;
                case PROPERTY_KEYS:
                    propertyKeys = names(key, value);
                    break;
                default:
                    // A key a later server added: ignored.
                    break;
            }
        }

        SchemaReply toReply() {
            if (!(hash instanceof Long)) {
                throw new GraphException(
                        "Malformed schema element in the reply: hash must be an integer, but got " + typeOf(hash));
            }
            long value = (Long) hash;
            if (labels == null && relationshipTypes == null && propertyKeys == null) {
                return new SchemaReply(value, null);
            }
            if (labels == null || relationshipTypes == null || propertyKeys == null) {
                throw new GraphException("Malformed schema element in the reply: it lists some of " + LABELS + ", "
                        + RELATIONSHIP_TYPES + " and " + PROPERTY_KEYS + ", but not all three");
            }
            return new SchemaReply(value, new SchemaNames(value, labels, relationshipTypes, propertyKeys));
        }

        private static List<String> names(String key, @Nullable Object value) {
            if (!(value instanceof List)) {
                throw new GraphException(
                        "Malformed schema element in the reply: " + key + " must be a list, but got " + typeOf(value));
            }
            List<?> raw = (List<?>) value;
            List<String> names = new ArrayList<>(raw.size());
            for (Object item : raw) {
                String name = text(item);
                if (name == null) {
                    throw new GraphException("Malformed schema element in the reply: " + key
                            + " must list strings, but lists " + typeOf(item));
                }
                names.add(name);
            }
            return names;
        }
    }
}
