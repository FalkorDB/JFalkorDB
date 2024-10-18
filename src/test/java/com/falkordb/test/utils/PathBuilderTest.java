package com.falkordb.test.utils;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.falkordb.graph_entities.Edge;

public class PathBuilderTest {

    /**
    * Tests if the PathBuilder throws an IllegalArgumentException when initialized with an invalid size.
    * 
    * This test method verifies that creating a PathBuilder with a size of 0 and attempting to build it
    * results in an IllegalArgumentException with the expected error message.
    *
    * @throws IllegalArgumentException if the PathBuilder is initialized with an invalid size
    */
    @Test
    public void testPathBuilderSizeException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            PathBuilder builder = new PathBuilder(0);
            builder.build();
        });
        assertTrue(exception.getMessage().equalsIgnoreCase("Path builder nodes count should be edge count + 1"));
    }

    /**
     * Tests the PathBuilder class for throwing an IllegalArgumentException when appending an Edge to an empty path.
     * 
     * This method verifies that the PathBuilder correctly throws an IllegalArgumentException
     * when an attempt is made to append an Edge object to an empty path. The expected behavior
     * is that the PathBuilder should only accept Node objects for the first element of the path.
     * 
     * @throws IllegalArgumentException if an Edge is appended to an empty path
     */
    @Test
    public void testPathBuilderArgumentsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            PathBuilder builder = new PathBuilder(0);
            builder.append(new Edge());
        });
        assertTrue(exception.getMessage().equalsIgnoreCase("Path Builder expected Node but was Edge"));
    }
}
