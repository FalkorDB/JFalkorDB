package com.falkordb.impl.graph_cache;

import com.falkordb.exceptions.GraphException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One consistent copy of a graph's schema names: its labels, relationship types and property keys, each list in id
 * order, so an id is an index. It never changes; a newer schema is a new copy.
 *
 * <p>The {@linkplain #hash() hash} is the one FalkorDB 6.2 and later sends with a reply to a query that carried
 * {@code SCHEMA_HASH}. It is {@code null} when the names were fetched with {@code db.labels()} and the like, on a
 * server that sends no hash, or when nothing is known yet. The client never computes it: it only sends back what the
 * server sent.
 */
public final class SchemaNames {

    /** No names, and no hash: what a cache holds before it learned anything, and after it was cleared. */
    public static final SchemaNames EMPTY =
            new SchemaNames(null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

    private final @Nullable Long hash;
    private final List<String> labels;
    private final List<String> relationshipTypes;
    private final List<String> propertyKeys;

    /**
     * Creates names that never change: the lists are copied.
     *
     * @param hash              the server's hash of these names, or null when it isn't known
     * @param labels            the labels, in id order
     * @param relationshipTypes the relationship types, in id order
     * @param propertyKeys      the property keys, in id order
     */
    public SchemaNames(
            @Nullable Long hash, List<String> labels, List<String> relationshipTypes, List<String> propertyKeys) {
        this.hash = hash;
        this.labels = copy(labels);
        this.relationshipTypes = copy(relationshipTypes);
        this.propertyKeys = copy(propertyKeys);
    }

    private static List<String> copy(List<String> names) {
        return names.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(names));
    }

    /**
     * Returns the server's hash of these names, or null when it isn't known.
     *
     * @return the hash, or null
     */
    public @Nullable Long hash() {
        return hash;
    }

    /**
     * Returns the value to send as {@code SCHEMA_HASH}: the hash, or {@code 0} when it isn't known.
     *
     * @return the value to send
     */
    public long hashToSend() {
        return hash == null ? 0L : hash;
    }

    /**
     * Returns the labels, in id order.
     *
     * @return the labels
     */
    public List<String> labels() {
        return labels;
    }

    /**
     * Returns the relationship types, in id order.
     *
     * @return the relationship types
     */
    public List<String> relationshipTypes() {
        return relationshipTypes;
    }

    /**
     * Returns the property keys, in id order.
     *
     * @return the property keys
     */
    public List<String> propertyKeys() {
        return propertyKeys;
    }

    /** A copy with the labels replaced, as a {@code db.labels()} refresh makes. Its hash isn't known. */
    SchemaNames withLabels(List<String> fresh) {
        return new SchemaNames(null, fresh, relationshipTypes, propertyKeys);
    }

    /** A copy with the relationship types replaced. Its hash isn't known. */
    SchemaNames withRelationshipTypes(List<String> fresh) {
        return new SchemaNames(null, labels, fresh, propertyKeys);
    }

    /** A copy with the property keys replaced. Its hash isn't known. */
    SchemaNames withPropertyKeys(List<String> fresh) {
        return new SchemaNames(null, labels, relationshipTypes, fresh);
    }

    /**
     * Whether each of these lists starts with the matching list of {@code other}. Within one graph the server only
     * appends names, so a schema is a prefix of every later schema of the same graph.
     *
     * @param other the schema to compare with
     * @return whether these names are the same as, or an older version of, {@code other}'s
     */
    boolean isPrefixOf(SchemaNames other) {
        return isPrefix(labels, other.labels)
                && isPrefix(relationshipTypes, other.relationshipTypes)
                && isPrefix(propertyKeys, other.propertyKeys);
    }

    private static boolean isPrefix(List<String> shorter, List<String> longer) {
        return shorter.size() <= longer.size() && shorter.equals(longer.subList(0, shorter.size()));
    }

    /**
     * Returns the name with the given id, or throws {@code missing}'s message for one these names don't reach.
     *
     * @param kind    which list to look in
     * @param id      the id
     * @param missing why an id can be missing, for the error
     * @return the name
     * @throws GraphException if the id is negative or past the end of the list
     */
    String name(Kind kind, int id, String missing) {
        List<String> names = kind.of(this);
        if (id < 0 || id >= names.size()) {
            throw new GraphException("Cannot decode " + kind.entryName + " id " + id + ": " + missing);
        }
        return names.get(id);
    }

    /** The three lists, with the procedure that fetches each from a server that sends no schema. */
    enum Kind {
        LABEL("db.labels", "label"),
        RELATIONSHIP_TYPE("db.relationshipTypes", "relationship type"),
        PROPERTY_KEY("db.propertyKeys", "property key");

        final String procedure;
        final String entryName;

        Kind(String procedure, String entryName) {
            this.procedure = procedure;
            this.entryName = entryName;
        }

        List<String> of(SchemaNames names) {
            switch (this) {
                case LABEL:
                    return names.labels;
                case RELATIONSHIP_TYPE:
                    return names.relationshipTypes;
                default:
                    return names.propertyKeys;
            }
        }

        SchemaNames replace(SchemaNames names, List<String> fresh) {
            switch (this) {
                case LABEL:
                    return names.withLabels(fresh);
                case RELATIONSHIP_TYPE:
                    return names.withRelationshipTypes(fresh);
                default:
                    return names.withPropertyKeys(fresh);
            }
        }
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SchemaNames)) {
            return false;
        }
        SchemaNames that = (SchemaNames) o;
        return Objects.equals(hash, that.hash)
                && labels.equals(that.labels)
                && relationshipTypes.equals(that.relationshipTypes)
                && propertyKeys.equals(that.propertyKeys);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hash, labels, relationshipTypes, propertyKeys);
    }

    @Override
    public String toString() {
        // Names can be sensitive, so only their counts are shown.
        return "SchemaNames{hash=" + hash + ", labels=" + labels.size() + ", relationshipTypes="
                + relationshipTypes.size() + ", propertyKeys=" + propertyKeys.size() + "}";
    }
}
