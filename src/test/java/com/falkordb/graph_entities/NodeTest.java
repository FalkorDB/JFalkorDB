package com.falkordb.graph_entities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class NodeTest {

    @Test
    public void testDefaultConstructor() {
        Node node = new Node();
        assertNotNull(node);
        assertEquals(0, node.getNumberOfLabels());
    }

    @Test
    public void testConstructorWithCapacities() {
        Node node = new Node(5, 10);
        assertNotNull(node);
        assertEquals(0, node.getNumberOfLabels());
    }

    @Test
    public void testAddLabel() {
        Node node = new Node();
        node.addLabel("Person");
        
        assertEquals(1, node.getNumberOfLabels());
        assertEquals("Person", node.getLabel(0));
    }

    @Test
    public void testAddMultipleLabels() {
        Node node = new Node();
        node.addLabel("Person");
        node.addLabel("Employee");
        node.addLabel("Manager");
        
        assertEquals(3, node.getNumberOfLabels());
        assertEquals("Person", node.getLabel(0));
        assertEquals("Employee", node.getLabel(1));
        assertEquals("Manager", node.getLabel(2));
    }

    @Test
    public void testRemoveLabel() {
        Node node = new Node();
        node.addLabel("Person");
        node.addLabel("Employee");
        
        node.removeLabel("Person");
        
        assertEquals(1, node.getNumberOfLabels());
        assertEquals("Employee", node.getLabel(0));
    }

    @Test
    public void testGetLabelByIndex() {
        Node node = new Node();
        node.addLabel("Person");
        node.addLabel("Employee");
        
        assertEquals("Person", node.getLabel(0));
        assertEquals("Employee", node.getLabel(1));
    }

    @Test
    public void testGetLabelThrowsIndexOutOfBounds() {
        Node node = new Node();
        node.addLabel("Person");
        
        assertThrows(IndexOutOfBoundsException.class, () -> node.getLabel(5));
    }

    @Test
    public void testGetNumberOfLabels() {
        Node node = new Node();
        assertEquals(0, node.getNumberOfLabels());
        
        node.addLabel("Person");
        assertEquals(1, node.getNumberOfLabels());
        
        node.addLabel("Employee");
        assertEquals(2, node.getNumberOfLabels());
    }

    @Test
    public void testEqualsWithSameObject() {
        Node node = new Node();
        node.addLabel("Person");
        
        assertEquals(node, node);
    }

    @Test
    public void testEqualsWithEqualNodes() {
        Node node1 = new Node();
        node1.setId(1);
        node1.addLabel("Person");
        
        Node node2 = new Node();
        node2.setId(1);
        node2.addLabel("Person");
        
        assertEquals(node1, node2);
    }

    @Test
    public void testEqualsWithDifferentLabels() {
        Node node1 = new Node();
        node1.addLabel("Person");
        
        Node node2 = new Node();
        node2.addLabel("Employee");
        
        assertNotEquals(node1, node2);
    }

    @Test
    public void testEqualsWithNull() {
        Node node = new Node();
        assertNotEquals(node, null);
    }

    @Test
    public void testEqualsWithDifferentType() {
        Node node = new Node();
        assertNotEquals(node, "some string");
    }

    @Test
    public void testHashCode() {
        Node node1 = new Node();
        node1.setId(1);
        node1.addLabel("Person");
        
        Node node2 = new Node();
        node2.setId(1);
        node2.addLabel("Person");
        
        assertEquals(node1.hashCode(), node2.hashCode());
    }

    @Test
    public void testToString() {
        Node node = new Node();
        node.setId(1);
        node.addLabel("Person");
        node.addProperty("name", "John");
        
        String result = node.toString();
        
        assertTrue(result.contains("Node{"));
        assertTrue(result.contains("labels="));
        assertTrue(result.contains("id="));
        assertTrue(result.contains("propertyMap="));
    }

    @Test
    public void testToStringEmptyNode() {
        Node node = new Node();
        String result = node.toString();
        
        assertNotNull(result);
        assertTrue(result.contains("Node{"));
    }
}
