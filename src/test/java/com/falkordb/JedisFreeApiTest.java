package com.falkordb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The types that replace {@link GraphPipeline} and {@link GraphTransaction} name no Jedis type in any signature,
 * supertype or type-parameter bound, so a Jedis release can't change them. An early, narrow form of the gate 0.18.0
 * puts on the whole API, once the deprecated types are gone.
 */
class JedisFreeApiTest {

    @Test
    void theNewBatchTypesNameNoJedisType() throws NoSuchMethodException {
        List<String> found = new ArrayList<>();
        for (Class<?> type : Arrays.asList(Reply.class, GraphContext.Pipeline.class, GraphContext.Transaction.class)) {
            found.addAll(jedisTypesIn(type));
        }
        check(GraphContext.class.getMethod("pipeline"), found);
        check(GraphContext.class.getMethod("transaction"), found);
        assertEquals(new ArrayList<String>(), found);
    }

    /** The gate itself: each place a Jedis type can hide is found. */
    @Test
    void theGateFindsAJedisTypeWhereverItIsNamed() {
        assertTrue(jedisTypesIn(ClassBound.class).toString().contains("parameter T"), "a type parameter's bound");
        assertTrue(
                jedisTypesIn(MethodBound.class).toString().contains("bounded()"), "a method's type parameter's bound");
        assertTrue(jedisTypesIn(WildcardBound.class).toString().contains("wildcard("), "a wildcard's bound");
        assertTrue(jedisTypesIn(Supertype.class).toString().contains("extends"), "a supertype's type argument");
        assertTrue(jedisTypesIn(ArrayOf.class).toString().contains("array()"), "an array's component type");
        assertEquals(Collections.emptyList(), jedisTypesIn(SelfBound.class), "a recursive bound, which names none");
    }

    /** Every Jedis type that {@code type}'s own type parameters, supertypes and public methods name. */
    private static List<String> jedisTypesIn(Class<?> type) {
        List<String> found = new ArrayList<>();
        Set<TypeVariable<?>> seen = new HashSet<>();
        for (TypeVariable<?> parameter : type.getTypeParameters()) {
            names(parameter, type.toGenericString() + " parameter " + parameter + ": ", found, seen);
        }
        checkSupertypes(type, found);
        for (Method method : type.getMethods()) {
            check(method, found);
        }
        return found;
    }

    private static void checkSupertypes(Class<?> type, List<String> found) {
        for (Type supertype : type.getGenericInterfaces()) {
            Set<TypeVariable<?>> seen = new HashSet<>();
            names(supertype, type.getName() + " extends ", found, seen);
            Class<?> raw = supertype instanceof ParameterizedType
                    ? (Class<?>) ((ParameterizedType) supertype).getRawType()
                    : (Class<?>) supertype;
            for (TypeVariable<?> parameter : raw.getTypeParameters()) {
                names(parameter, raw.getName() + " parameter " + parameter + ": ", found, seen);
            }
            checkSupertypes(raw, found);
        }
    }

    private static void check(Method method, List<String> found) {
        String where = method.toGenericString() + ": ";
        Set<TypeVariable<?>> seen = new HashSet<>();
        names(method.getGenericReturnType(), where, found, seen);
        for (Type parameter : method.getGenericParameterTypes()) {
            names(parameter, where, found, seen);
        }
        for (Type thrown : method.getGenericExceptionTypes()) {
            names(thrown, where, found, seen);
        }
        for (TypeVariable<Method> variable : method.getTypeParameters()) {
            names(variable, where, found, seen);
        }
    }

    /**
     * Adds every Jedis type {@code type} names: its type arguments, and the bounds of its variables and wildcards.
     * {@code seen} compares type variables by equality, as reflection may return a new instance for the same one,
     * which a recursive bound such as {@code T extends Comparable<T>} would otherwise follow for ever.
     */
    private static void names(Type type, String where, List<String> found, Set<TypeVariable<?>> seen) {
        if (type instanceof Class) {
            Class<?> cls = (Class<?>) type;
            if (cls.isArray()) {
                names(cls.getComponentType(), where, found, seen);
            } else if (cls.getName().startsWith("redis.clients.")) {
                found.add(where + cls.getName());
            }
        } else if (type instanceof ParameterizedType) {
            names(((ParameterizedType) type).getRawType(), where, found, seen);
            for (Type argument : ((ParameterizedType) type).getActualTypeArguments()) {
                names(argument, where, found, seen);
            }
        } else if (type instanceof GenericArrayType) {
            names(((GenericArrayType) type).getGenericComponentType(), where, found, seen);
        } else if (type instanceof WildcardType) {
            for (Type bound : ((WildcardType) type).getUpperBounds()) {
                names(bound, where, found, seen);
            }
            for (Type bound : ((WildcardType) type).getLowerBounds()) {
                names(bound, where, found, seen);
            }
        } else if (type instanceof TypeVariable && seen.add((TypeVariable<?>) type)) {
            for (Type bound : ((TypeVariable<?>) type).getBounds()) {
                names(bound, where, found, seen);
            }
        }
    }

    // Fixtures: each names a Jedis type in one place the gate must look.

    interface ClassBound<T extends redis.clients.jedis.Response<?>> {}

    interface MethodBound {
        @SuppressWarnings("TypeParameterUnusedInFormals") // only its bound matters here
        <T extends redis.clients.jedis.Response<?>> T bounded();
    }

    interface WildcardBound {
        void wildcard(List<? super redis.clients.jedis.Response<String>> sink);
    }

    interface Supertype extends java.util.function.Supplier<redis.clients.jedis.Response<String>> {}

    interface ArrayOf {
        redis.clients.jedis.Response<?>[] array();
    }

    interface SelfBound<T extends Comparable<T>> {
        <U extends Comparable<U>> U self(T t);
    }
}
