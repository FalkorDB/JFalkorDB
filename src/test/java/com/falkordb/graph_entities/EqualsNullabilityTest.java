package com.falkordb.graph_entities;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * This package is {@code @NullMarked}, so an {@code equals(Object)} override without {@code @Nullable} on its
 * parameter would narrow {@link Object#equals(Object)}, which accepts null. Each override here is annotated, and
 * each really does accept null: this test pins the annotation and the behaviour together, so they can't drift.
 */
class EqualsNullabilityTest {

    private static final List<Object> INSTANCES = Arrays.asList(
            new Node(),
            new Edge(),
            new Property<>("name", "value"),
            new Path(Collections.<Node>emptyList(), Collections.<Edge>emptyList()),
            new Point(1.0, 2.0));

    @Test
    void equalsAcceptsNullAndSaysSo() throws NoSuchMethodException {
        for (Object instance : INSTANCES) {
            assertNullableEqualsParameter(instance.getClass());
            // assertNotEquals(x, null) calls x.equals(null).
            assertNotEquals(instance, null, instance.getClass().getSimpleName() + ".equals(null)");
        }
    }

    @Test
    void graphEntityEqualsAcceptsNullAndSaysSo() throws NoSuchMethodException {
        // Node and Edge reject null before they reach super.equals, so exercise GraphEntity's own override.
        GraphEntity entity = new GraphEntity() {
            @Override
            public String toString() {
                return "entity";
            }
        };
        assertNullableEqualsParameter(GraphEntity.class);
        assertNotEquals(entity, null, "GraphEntity.equals(null)");
    }

    private static void assertNullableEqualsParameter(Class<?> type) throws NoSuchMethodException {
        assertTrue(
                type.getDeclaredMethod("equals", Object.class)
                        .getAnnotatedParameterTypes()[0]
                        .isAnnotationPresent(Nullable.class),
                type.getSimpleName() + ".equals(Object) parameter should be @Nullable");
    }
}
