package com.falkordb.graph_entities;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PropertyTest {

    @Test
    public void testDefaultConstructor() {
        Property<String> property = new Property<>();
        assertNull(property.getName());
        assertNull(property.getValue());
    }

    @Test
    public void testParameterizedConstructor() {
        String name = "testName";
        String value = "testValue";
        Property<String> property = new Property<>(name, value);
        
        assertEquals(name, property.getName());
        assertEquals(value, property.getValue());
    }

    @Test
    public void testSettersAndGetters() {
        Property<Integer> property = new Property<>();
        
        property.setName("age");
        property.setValue(25);
        
        assertEquals("age", property.getName());
        assertEquals(25, property.getValue());
    }

    @Test
    public void testEqualsWithSameObject() {
        Property<String> property = new Property<>("name", "value");
        assertEquals(property, property);
    }

    @Test
    public void testEqualsWithEqualProperties() {
        Property<String> property1 = new Property<>("name", "value");
        Property<String> property2 = new Property<>("name", "value");
        
        assertEquals(property1, property2);
    }

    @Test
    public void testEqualsWithDifferentName() {
        Property<String> property1 = new Property<>("name1", "value");
        Property<String> property2 = new Property<>("name2", "value");
        
        assertNotEquals(property1, property2);
    }

    @Test
    public void testEqualsWithDifferentValue() {
        Property<String> property1 = new Property<>("name", "value1");
        Property<String> property2 = new Property<>("name", "value2");
        
        assertNotEquals(property1, property2);
    }

    @Test
    public void testEqualsWithNull() {
        Property<String> property = new Property<>("name", "value");
        assertNotEquals(property, null);
    }

    @Test
    public void testEqualsWithDifferentType() {
        Property<String> property = new Property<>("name", "value");
        assertNotEquals(property, "some string");
    }

    @Test
    public void testEqualsWithIntegerValues() {
        // Test that Integer values are correctly compared as Long values
        Property<Integer> property1 = new Property<>("count", 42);
        Property<Long> property2 = new Property<>("count", 42L);
        
        assertEquals(property1, property2);
    }

    @Test
    public void testHashCode() {
        Property<String> property1 = new Property<>("name", "value");
        Property<String> property2 = new Property<>("name", "value");
        
        assertEquals(property1.hashCode(), property2.hashCode());
    }

    @Test
    public void testHashCodeWithNullValues() {
        Property<String> property1 = new Property<>(null, null);
        Property<String> property2 = new Property<>(null, null);
        
        assertEquals(property1.hashCode(), property2.hashCode());
    }

    @Test
    public void testToString() {
        Property<String> property = new Property<>("name", "value");
        String result = property.toString();
        
        assertTrue(result.contains("name"));
        assertTrue(result.contains("value"));
        assertTrue(result.contains("Property{"));
    }

    @Test
    public void testToStringWithNull() {
        Property<String> property = new Property<>();
        String result = property.toString();
        
        assertNotNull(result);
        assertTrue(result.contains("Property{"));
    }

    @Test
    public void testPropertyWithDifferentTypes() {
        Property<Integer> intProperty = new Property<>("age", 25);
        Property<Double> doubleProperty = new Property<>("score", 98.5);
        Property<Boolean> boolProperty = new Property<>("active", true);
        
        assertEquals(25, intProperty.getValue());
        assertEquals(98.5, doubleProperty.getValue());
        assertEquals(true, boolProperty.getValue());
    }
}
