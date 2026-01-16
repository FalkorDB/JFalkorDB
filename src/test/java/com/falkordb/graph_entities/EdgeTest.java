package com.falkordb.graph_entities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class EdgeTest {

    @Test
    public void testDefaultConstructor() {
        Edge edge = new Edge();
        assertNotNull(edge);
        assertNull(edge.getRelationshipType());
    }

    @Test
    public void testConstructorWithCapacity() {
        Edge edge = new Edge(10);
        assertNotNull(edge);
        assertNull(edge.getRelationshipType());
    }

    @Test
    public void testSetAndGetRelationshipType() {
        Edge edge = new Edge();
        edge.setRelationshipType("KNOWS");
        
        assertEquals("KNOWS", edge.getRelationshipType());
    }

    @Test
    public void testSetAndGetSource() {
        Edge edge = new Edge();
        edge.setSource(1L);
        
        assertEquals(1L, edge.getSource());
    }

    @Test
    public void testSetAndGetDestination() {
        Edge edge = new Edge();
        edge.setDestination(2L);
        
        assertEquals(2L, edge.getDestination());
    }

    @Test
    public void testCompleteEdge() {
        Edge edge = new Edge();
        edge.setId(100);
        edge.setRelationshipType("WORKS_FOR");
        edge.setSource(10L);
        edge.setDestination(20L);
        edge.addProperty("since", 2020);
        
        assertEquals(100, edge.getId());
        assertEquals("WORKS_FOR", edge.getRelationshipType());
        assertEquals(10L, edge.getSource());
        assertEquals(20L, edge.getDestination());
        assertEquals(2020, edge.getProperty("since").getValue());
    }

    @Test
    public void testEqualsWithSameObject() {
        Edge edge = new Edge();
        edge.setRelationshipType("KNOWS");
        
        assertEquals(edge, edge);
    }

    @Test
    public void testEqualsWithEqualEdges() {
        Edge edge1 = new Edge();
        edge1.setId(1);
        edge1.setRelationshipType("KNOWS");
        edge1.setSource(10L);
        edge1.setDestination(20L);
        
        Edge edge2 = new Edge();
        edge2.setId(1);
        edge2.setRelationshipType("KNOWS");
        edge2.setSource(10L);
        edge2.setDestination(20L);
        
        assertEquals(edge1, edge2);
    }

    @Test
    public void testEqualsWithDifferentRelationshipType() {
        Edge edge1 = new Edge();
        edge1.setRelationshipType("KNOWS");
        edge1.setSource(10L);
        edge1.setDestination(20L);
        
        Edge edge2 = new Edge();
        edge2.setRelationshipType("LIKES");
        edge2.setSource(10L);
        edge2.setDestination(20L);
        
        assertNotEquals(edge1, edge2);
    }

    @Test
    public void testEqualsWithDifferentSource() {
        Edge edge1 = new Edge();
        edge1.setRelationshipType("KNOWS");
        edge1.setSource(10L);
        edge1.setDestination(20L);
        
        Edge edge2 = new Edge();
        edge2.setRelationshipType("KNOWS");
        edge2.setSource(15L);
        edge2.setDestination(20L);
        
        assertNotEquals(edge1, edge2);
    }

    @Test
    public void testEqualsWithDifferentDestination() {
        Edge edge1 = new Edge();
        edge1.setRelationshipType("KNOWS");
        edge1.setSource(10L);
        edge1.setDestination(20L);
        
        Edge edge2 = new Edge();
        edge2.setRelationshipType("KNOWS");
        edge2.setSource(10L);
        edge2.setDestination(25L);
        
        assertNotEquals(edge1, edge2);
    }

    @Test
    public void testEqualsWithNull() {
        Edge edge = new Edge();
        assertNotEquals(edge, null);
    }

    @Test
    public void testEqualsWithDifferentType() {
        Edge edge = new Edge();
        assertNotEquals(edge, "some string");
    }

    @Test
    public void testHashCode() {
        Edge edge1 = new Edge();
        edge1.setId(1);
        edge1.setRelationshipType("KNOWS");
        edge1.setSource(10L);
        edge1.setDestination(20L);
        
        Edge edge2 = new Edge();
        edge2.setId(1);
        edge2.setRelationshipType("KNOWS");
        edge2.setSource(10L);
        edge2.setDestination(20L);
        
        assertEquals(edge1.hashCode(), edge2.hashCode());
    }

    @Test
    public void testToString() {
        Edge edge = new Edge();
        edge.setId(1);
        edge.setRelationshipType("KNOWS");
        edge.setSource(10L);
        edge.setDestination(20L);
        edge.addProperty("weight", 1.5);
        
        String result = edge.toString();
        
        assertTrue(result.contains("Edge{"));
        assertTrue(result.contains("relationshipType="));
        assertTrue(result.contains("source="));
        assertTrue(result.contains("destination="));
        assertTrue(result.contains("id="));
        assertTrue(result.contains("propertyMap="));
    }

    @Test
    public void testToStringEmptyEdge() {
        Edge edge = new Edge();
        String result = edge.toString();
        
        assertNotNull(result);
        assertTrue(result.contains("Edge{"));
    }
}
