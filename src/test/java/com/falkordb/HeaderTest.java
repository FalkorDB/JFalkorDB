package com.falkordb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class HeaderTest {

    @Test
    public void testResultSetColumnTypesEnumValues() {
        Header.ResultSetColumnTypes[] types = Header.ResultSetColumnTypes.values();
        
        assertEquals(4, types.length);
        assertEquals(Header.ResultSetColumnTypes.COLUMN_UNKNOWN, types[0]);
        assertEquals(Header.ResultSetColumnTypes.COLUMN_SCALAR, types[1]);
        assertEquals(Header.ResultSetColumnTypes.COLUMN_NODE, types[2]);
        assertEquals(Header.ResultSetColumnTypes.COLUMN_RELATION, types[3]);
    }

    @Test
    public void testResultSetColumnTypesValueOf() {
        assertEquals(Header.ResultSetColumnTypes.COLUMN_UNKNOWN, 
                Header.ResultSetColumnTypes.valueOf("COLUMN_UNKNOWN"));
        assertEquals(Header.ResultSetColumnTypes.COLUMN_SCALAR, 
                Header.ResultSetColumnTypes.valueOf("COLUMN_SCALAR"));
        assertEquals(Header.ResultSetColumnTypes.COLUMN_NODE, 
                Header.ResultSetColumnTypes.valueOf("COLUMN_NODE"));
        assertEquals(Header.ResultSetColumnTypes.COLUMN_RELATION, 
                Header.ResultSetColumnTypes.valueOf("COLUMN_RELATION"));
    }

    @Test
    public void testResultSetColumnTypesValueOfThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            Header.ResultSetColumnTypes.valueOf("INVALID_TYPE");
        });
    }

    @Test
    public void testResultSetColumnTypesEquality() {
        Header.ResultSetColumnTypes type1 = Header.ResultSetColumnTypes.COLUMN_SCALAR;
        Header.ResultSetColumnTypes type2 = Header.ResultSetColumnTypes.COLUMN_SCALAR;
        Header.ResultSetColumnTypes type3 = Header.ResultSetColumnTypes.COLUMN_NODE;
        
        assertEquals(type1, type2);
        assertNotEquals(type1, type3);
    }

    @Test
    public void testResultSetColumnTypesName() {
        assertEquals("COLUMN_UNKNOWN", Header.ResultSetColumnTypes.COLUMN_UNKNOWN.name());
        assertEquals("COLUMN_SCALAR", Header.ResultSetColumnTypes.COLUMN_SCALAR.name());
        assertEquals("COLUMN_NODE", Header.ResultSetColumnTypes.COLUMN_NODE.name());
        assertEquals("COLUMN_RELATION", Header.ResultSetColumnTypes.COLUMN_RELATION.name());
    }

    @Test
    public void testResultSetColumnTypesOrdinal() {
        assertEquals(0, Header.ResultSetColumnTypes.COLUMN_UNKNOWN.ordinal());
        assertEquals(1, Header.ResultSetColumnTypes.COLUMN_SCALAR.ordinal());
        assertEquals(2, Header.ResultSetColumnTypes.COLUMN_NODE.ordinal());
        assertEquals(3, Header.ResultSetColumnTypes.COLUMN_RELATION.ordinal());
    }
}
