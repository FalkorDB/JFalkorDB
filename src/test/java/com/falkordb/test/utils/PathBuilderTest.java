package com.falkordb.test.utils;

import com.falkordb.graph_entities.Edge;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PathBuilderTest {

    @Test
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
