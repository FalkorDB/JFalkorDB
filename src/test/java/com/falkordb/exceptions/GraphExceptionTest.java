package com.falkordb.exceptions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GraphExceptionTest {

    @Test
    public void testGraphExceptionWithMessage() {
        String message = "Test error message";
        GraphException exception = new GraphException(message);
        
        assertEquals(message, exception.getMessage());
        assertNotNull(exception);
    }

    @Test
    public void testGraphExceptionWithCause() {
        Throwable cause = new RuntimeException("Root cause");
        GraphException exception = new GraphException(cause);
        
        assertEquals(cause, exception.getCause());
        assertNotNull(exception);
    }

    @Test
    public void testGraphExceptionWithMessageAndCause() {
        String message = "Test error message";
        Throwable cause = new RuntimeException("Root cause");
        GraphException exception = new GraphException(message, cause);
        
        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
        assertNotNull(exception);
    }

    @Test
    public void testGraphExceptionIsThrowable() {
        GraphException exception = new GraphException("test");
        
        assertThrows(GraphException.class, () -> {
            throw exception;
        });
    }
}
