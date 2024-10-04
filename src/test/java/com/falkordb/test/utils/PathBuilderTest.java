package com.falkordb.test.utils;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.falkordb.graph_entities.Edge;

public class PathBuilderTest {

    /**
     * Tests the exception thrown when attempting to build a path with an invalid size.
     * 
     * This test verifies that the PathBuilder throws an IllegalArgumentException
     * when initialized with a size of 0 and the build method is called.
     * 
     * @throws IllegalArgumentException with the message "Path builder nodes count should be edge count + 1"
     */
    @Test
    /**
     * Tests if the PathBuilder throws an IllegalArgumentException when an Edge is appended instead of a Node.
     * 
     * This test method verifies that the PathBuilder correctly validates its input and throws an
     * IllegalArgumentException with an appropriate error message when an Edge object is provided
     * instead of the expected Node object.
     * 
     * @throws IllegalArgumentException Expected to be thrown when an Edge is appended to the PathBuilder
     */
    public void testPathBuilderSizeException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            PathBuilder builder = new PathBuilder(0);
            builder.build();
        });
        assertTrue(exception.getMessage().equalsIgnoreCase("Path builder nodes count should be edge count + 1"));
    }

    @Test
    public void testPathBuilderArgumentsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            PathBuilder builder = new PathBuilder(0);
            builder.append(new Edge());
        });
        assertTrue(exception.getMessage().equalsIgnoreCase("Path Builder expected Node but was Edge"));
    }
}
