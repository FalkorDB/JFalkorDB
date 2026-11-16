package com.falkordb.impl.api;

import com.falkordb.exceptions.GraphException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.util.KeyValue;
import redis.clients.jedis.util.SafeEncoder;

/**
 * Decodes the reply to a command queued with {@code sendCommand} or {@code sendBinaryCommand}, which can be any
 * Redis reply, into JDK types only. Jedis reads a simple, bulk or verbatim string as a {@code byte[]}, an integer as
 * a {@code Long}, an array or a set as a {@code List} (holding a {@code JedisDataException} where an element is an
 * error), nil as {@code null}, and, in RESP3, a double as a {@code Double}, a boolean as a {@code Boolean}, a big
 * number as a {@code BigInteger} and a map as a {@code List} of Jedis' {@code KeyValue}s.
 */
final class RawReplies {

    private RawReplies() {}

    /** Decodes strings as UTF-8 {@code String}s. */
    static @Nullable Object text(@Nullable Object reply) {
        return decode(reply, true);
    }

    /** Keeps strings as {@code byte[]}. */
    static @Nullable Object binary(@Nullable Object reply) {
        return decode(reply, false);
    }

    private static @Nullable Object decode(@Nullable Object reply, boolean text) {
        if (reply instanceof byte[]) {
            return text ? SafeEncoder.encode((byte[]) reply) : reply;
        }
        if (reply instanceof List) {
            List<?> elements = (List<?>) reply;
            if (!elements.isEmpty() && elements.get(0) instanceof KeyValue) {
                Map<@Nullable Object, @Nullable Object> map = new LinkedHashMap<>();
                for (Object element : elements) {
                    KeyValue<?, ?> entry = (KeyValue<?, ?>) element;
                    map.put(decode(entry.getKey(), text), decode(entry.getValue(), text));
                }
                return map;
            }
            List<@Nullable Object> decoded = new ArrayList<>(elements.size());
            for (Object element : elements) {
                decoded.add(decode(element, text));
            }
            return decoded;
        }
        if (reply instanceof JedisDataException && !(reply instanceof GraphException)) {
            JedisDataException error = (JedisDataException) reply;
            return new GraphException(error.getMessage(), error);
        }
        return reply; // a Long, Double, Boolean, BigInteger or null, as Jedis read it
    }
}
