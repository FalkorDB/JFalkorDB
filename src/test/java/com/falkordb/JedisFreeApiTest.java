package com.falkordb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The types that replace {@link GraphPipeline} and {@link GraphTransaction} name no Jedis type in any signature or
 * supertype, so a Jedis release can't change them. An early, narrow form of the gate 0.18.0 puts on the whole API,
 * once the deprecated types are gone.
 */
class JedisFreeApiTest {

    @Test
    void theNewBatchTypesNameNoJedisType() throws NoSuchMethodException {
        List<String> found = new ArrayList<>();
        for (Class<?> type : Arrays.asList(Reply.class, GraphContext.Pipeline.class, GraphContext.Transaction.class)) {
            checkSupertypes(type, found);
            for (Method method : type.getMethods()) {
                check(method, found);
            }
        }
        check(GraphContext.class.getMethod("pipeline"), found);
        check(GraphContext.class.getMethod("transaction"), found);
        assertEquals(new ArrayList<String>(), found);
    }

    private static void checkSupertypes(Class<?> type, List<String> found) {
        for (Type supertype : type.getGenericInterfaces()) {
            names(supertype, type.getName() + " extends ", found);
            if (supertype instanceof Class) {
                checkSupertypes((Class<?>) supertype, found);
            } else if (supertype instanceof ParameterizedType) {
                checkSupertypes((Class<?>) ((ParameterizedType) supertype).getRawType(), found);
            }
        }
    }

    private static void check(Method method, List<String> found) {
        String where = method.toGenericString() + ": ";
        names(method.getGenericReturnType(), where, found);
        for (Type parameter : method.getGenericParameterTypes()) {
            names(parameter, where, found);
        }
        for (Type thrown : method.getGenericExceptionTypes()) {
            names(thrown, where, found);
        }
        for (TypeVariable<Method> variable : method.getTypeParameters()) {
            for (Type bound : variable.getBounds()) {
                names(bound, where, found);
            }
        }
    }

    /** Adds every Jedis type {@code type} names, its type arguments and bounds included. */
    private static void names(Type type, String where, List<String> found) {
        if (type instanceof Class) {
            Class<?> cls = (Class<?>) type;
            if (cls.isArray()) {
                names(cls.getComponentType(), where, found);
            } else if (cls.getName().startsWith("redis.clients.")) {
                found.add(where + cls.getName());
            }
        } else if (type instanceof ParameterizedType) {
            names(((ParameterizedType) type).getRawType(), where, found);
            for (Type argument : ((ParameterizedType) type).getActualTypeArguments()) {
                names(argument, where, found);
            }
        } else if (type instanceof GenericArrayType) {
            names(((GenericArrayType) type).getGenericComponentType(), where, found);
        } else if (type instanceof WildcardType) {
            for (Type bound : ((WildcardType) type).getUpperBounds()) {
                names(bound, where, found);
            }
            for (Type bound : ((WildcardType) type).getLowerBounds()) {
                names(bound, where, found);
            }
        }
    }
}
