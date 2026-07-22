package com.falkordb.graph_entities;

import java.util.Objects;

/**
 * A Graph entity property. Has a name, type, and value.
 *
 * @param <T> the type of the property value
 */
public class Property<T> {

    // members
    private String name;
    private T value;

    /**
     * Default constructor
     */
    public Property() {}

    /**
     * Parameterized constructor
     *
     * @param name property name
     * @param value property value
     */
    public Property(String name, T value) {
        this.name = name;
        this.value = value;
    }

    /**
     * Returns the property name.
     *
     * @return property name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the property name.
     *
     * @param name property name to be set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the property value.
     *
     * @return property value
     */
    public T getValue() {
        return value;
    }

    /**
     * Sets the property value.
     *
     * @param value property value to be set
     */
    public void setValue(T value) {
        this.value = value;
    }

    // equals() treats an Integer value as equal to the numerically-equal Long (the server can return
    // either width for the same value). hashCode() must apply the SAME normalization, otherwise equal
    // properties can hash differently - e.g. Integer(-1).hashCode() == -1 but Long(-1L).hashCode() == 0.
    private static Object normalizeValue(Object value) {
        if (value instanceof Integer) {
            return Long.valueOf(((Integer) value).longValue());
        }
        return value;
    }

    private boolean valueEquals(Object value1, Object value2) {
        return Objects.equals(normalizeValue(value1), normalizeValue(value2));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Property)) return false;
        Property<?> property = (Property<?>) o;
        return Objects.equals(name, property.name) && valueEquals(value, property.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, normalizeValue(value));
    }

    /**
     * Default toString implementation
     * @return string representation of the property
     */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Property{");
        sb.append("name='").append(name).append('\'');
        sb.append(", value=").append(value);
        sb.append('}');
        return sb.toString();
    }
}
