package com.falkordb.impl;

import java.lang.reflect.Array;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Utilities for encoding queries and parameters for FalkorDB.
 *
 * <p>Query parameters are sent by prepending a {@code CYPHER name=value ...} header to the query.
 * Values are encoded as Cypher literals; string values are wrapped in double quotes with backslash
 * and double quote escaped, so caller-supplied values cannot break out of the literal and inject
 * Cypher (see {@link #prepareQuery(String, Map)}). Parameter names and non-identifier map keys are
 * backtick-quoted, and only a whitelist of value types is accepted — anything else fails fast rather
 * than being emitted via an unchecked {@code toString()}.
 */
public class Utils {
    /**
     * A dummy list.
     */
    public static final List<String> DUMMY_LIST = new ArrayList<>(0);
    /**
     * A dummy map.
     */
    public static final Map<String, List<String>> DUMMY_MAP = new HashMap<>(0);
    /**
     * The compact string.
     */
    public static final String COMPACT_STRING = "--COMPACT";
    /**
     * The timeout string.
     */
    public static final String TIMEOUT_STRING = "TIMEOUT";

    /** A Cypher identifier: used for parameter names and bare (unquoted) map keys. */
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private Utils() {}

    /**
     * Encodes a string as a double-quoted Cypher string literal, escaping backslash and double quote
     * (and the whitespace/control escapes FalkorDB decodes) so the value cannot terminate the literal.
     *
     * @param str the raw string value
     * @return the encoded Cypher string literal
     * @throws IllegalArgumentException if the string contains a NUL character or an unpaired surrogate
     */
    private static String quoteString(String str) {
        StringBuilder sb = new StringBuilder(str.length() + 2);
        sb.append('"');
        appendEscaped(sb, str);
        sb.append('"');
        return sb.toString();
    }

    private static void appendEscaped(StringBuilder sb, String str) {
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\0':
                    throw new IllegalArgumentException("Cypher string parameters cannot contain the NUL character");
                default:
                    if (Character.isHighSurrogate(c)) {
                        if (i + 1 < str.length() && Character.isLowSurrogate(str.charAt(i + 1))) {
                            sb.append(c).append(str.charAt(++i));
                        } else {
                            throw new IllegalArgumentException("Unpaired surrogate in string parameter at index " + i);
                        }
                    } else if (Character.isLowSurrogate(c)) {
                        throw new IllegalArgumentException("Unpaired surrogate in string parameter at index " + i);
                    } else {
                        // Every other character (including raw control chars and UTF-8) is literal.
                        sb.append(c);
                    }
            }
        }
    }

    /**
     * Prepares and formats a query and its parameters.
     *
     * <p>Each parameter is emitted as {@code `name`=value} in a {@code CYPHER ...} header. Parameter
     * names are validated as Cypher identifiers and backtick-quoted (so a name that collides with the
     * {@code CYPHER} header keyword cannot be misparsed), and values are encoded via the type-checked
     * {@link #appendValue}. Callers should always parameterize user input rather than concatenating it
     * into the query text.
     *
     * @param query - query
     * @param params - query parameters
     * @return query with parameters header
     * @throws IllegalArgumentException if a parameter name is not a valid identifier or a value is not
     *     an encodable type
     */
    public static String prepareQuery(String query, Map<String, Object> params) {
        if (query == null) {
            throw new IllegalArgumentException("query must not be null");
        }
        if (params == null) {
            throw new IllegalArgumentException("params must not be null");
        }
        StringBuilder sb = new StringBuilder("CYPHER ");
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            appendParamName(sb, entry.getKey());
            sb.append('=');
            appendValue(sb, entry.getValue(), seen);
            sb.append(' ');
        }
        sb.append(query);
        return sb.toString();
    }

    private static void appendParamName(StringBuilder sb, String name) {
        if (name == null || !IDENTIFIER.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "Invalid query parameter name: " + safeDisplay(name) + " (must match [A-Za-z_][A-Za-z0-9_]*)");
        }
        // Backtick-quote the (already validated, backtick-free) name so an identifier that happens to
        // be a header keyword such as "CYPHER" is not stripped/misparsed by the server's header parser.
        sb.append('`').append(name).append('`');
    }

    private static void appendValue(StringBuilder sb, Object value, Set<Object> seen) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            sb.append(quoteString((String) value));
        } else if (value instanceof Character) {
            sb.append(quoteString(value.toString()));
        } else if (value instanceof Boolean) {
            sb.append(((Boolean) value) ? "true" : "false");
        } else if (value instanceof Number) {
            appendNumber(sb, (Number) value);
        } else if (value.getClass().isArray()) {
            appendArray(sb, value, seen);
        } else if (value instanceof List) {
            appendList(sb, (List<?>) value, seen);
        } else if (value instanceof Map) {
            appendMap(sb, (Map<?, ?>) value, seen);
        } else {
            throw new IllegalArgumentException(
                    "Unsupported query parameter type: " + value.getClass().getName());
        }
    }

    private static void appendNumber(StringBuilder sb, Number value) {
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            sb.append(value.toString());
        } else if (value instanceof BigInteger) {
            BigInteger bi = (BigInteger) value;
            long exact;
            try {
                exact = bi.longValueExact();
            } catch (ArithmeticException e) {
                // Report the bit length rather than the full value: a huge BigInteger would otherwise
                // materialize an enormous decimal string just to be rejected.
                throw new IllegalArgumentException(
                        "Integer parameter out of signed 64-bit range (bitLength=" + bi.bitLength() + ")", e);
            }
            sb.append(Long.toString(exact));
        } else if (value instanceof Float) {
            float f = (Float) value;
            if (!isFinite(f)) {
                throw new IllegalArgumentException("Non-finite floating-point parameter: " + f);
            }
            sb.append(Float.toString(f));
        } else if (value instanceof Double) {
            double d = (Double) value;
            if (!isFinite(d)) {
                throw new IllegalArgumentException("Non-finite floating-point parameter: " + d);
            }
            sb.append(Double.toString(d));
        } else {
            // BigDecimal and other Number subtypes (e.g. AtomicInteger) are rejected: the server stores
            // decimals as double, so emitting an unbounded/foreign representation could lose precision.
            throw new IllegalArgumentException(
                    "Unsupported numeric parameter type: " + value.getClass().getName());
        }
    }

    private static void appendArray(StringBuilder sb, Object array, Set<Object> seen) {
        enter(seen, array);
        try {
            sb.append('[');
            int length = Array.getLength(array);
            for (int i = 0; i < length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                appendValue(sb, Array.get(array, i), seen);
            }
            sb.append(']');
        } finally {
            seen.remove(array);
        }
    }

    private static void appendList(StringBuilder sb, List<?> list, Set<Object> seen) {
        enter(seen, list);
        try {
            sb.append('[');
            boolean first = true;
            for (Object element : list) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                appendValue(sb, element, seen);
            }
            sb.append(']');
        } finally {
            seen.remove(list);
        }
    }

    private static void appendMap(StringBuilder sb, Map<?, ?> map, Set<Object> seen) {
        enter(seen, map);
        try {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                appendMapKey(sb, entry.getKey());
                sb.append(": ");
                appendValue(sb, entry.getValue(), seen);
            }
            sb.append('}');
        } finally {
            seen.remove(map);
        }
    }

    private static void appendMapKey(StringBuilder sb, Object key) {
        if (!(key instanceof String)) {
            throw new IllegalArgumentException("Map parameter keys must be Strings, got: "
                    + (key == null ? "null" : key.getClass().getName()));
        }
        String k = (String) key;
        if (IDENTIFIER.matcher(k).matches()) {
            sb.append(k);
            return;
        }
        // A non-identifier map-literal key must be backtick-quoted; FalkorDB cannot escape a backtick
        // inside such a key, so a key containing one (or a NUL) is unrepresentable and rejected. Reject
        // unpaired surrogates too, so a key can't be silently mangled to '?' by UTF-8 encoding.
        if (k.indexOf('`') >= 0 || k.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(
                    "Map parameter key cannot contain a backtick or NUL character: " + safeDisplay(k));
        }
        requireWellFormedUtf16(k);
        sb.append('`').append(k).append('`');
    }

    private static void requireWellFormedUtf16(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= s.length() || !Character.isLowSurrogate(s.charAt(i + 1))) {
                    throw new IllegalArgumentException("Unpaired surrogate at index " + i);
                }
                i++;
            } else if (Character.isLowSurrogate(c)) {
                throw new IllegalArgumentException("Unpaired surrogate at index " + i);
            }
        }
    }

    private static void enter(Set<Object> seen, Object container) {
        if (!seen.add(container)) {
            throw new IllegalArgumentException("Cyclic reference in query parameter");
        }
    }

    private static boolean isFinite(double d) {
        return !Double.isNaN(d) && !Double.isInfinite(d);
    }

    private static boolean isFinite(float f) {
        return !Float.isNaN(f) && !Float.isInfinite(f);
    }

    /**
     * Renders a caller-supplied string for an exception message with control characters escaped and the
     * length bounded, so an invalid parameter name/key can't forge log entries or bloat the message.
     */
    private static String safeDisplay(String s) {
        if (s == null) {
            return "null";
        }
        int limit = Math.min(s.length(), 64);
        StringBuilder sb = new StringBuilder(limit + 6).append('"');
        for (int i = 0; i < limit; i++) {
            char c = s.charAt(i);
            if (Character.isHighSurrogate(c) && i + 1 < limit && Character.isLowSurrogate(s.charAt(i + 1))) {
                // A valid surrogate pair (e.g. an emoji): keep both halves together.
                sb.append(c).append(s.charAt(++i));
            } else if (Character.isISOControl(c)
                    || c == '\u2028'
                    || c == '\u2029'
                    || Character.isHighSurrogate(c)
                    || Character.isLowSurrogate(c)) {
                // Control chars (C0/DEL/C1), line/paragraph separators, and unpaired surrogates
                // (including one split at the truncation boundary) are escaped, never emitted raw.
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        sb.append('"');
        if (s.length() > limit) {
            sb.append("...");
        }
        return sb.toString();
    }

    /**
     * Prepare and format a procedure call and its arguments.
     *
     * @param procedure - procedure to invoke
     * @param args - procedure arguments
     * @param kwargs - procedure output arguments
     * @return formatter procedure call
     */
    public static String prepareProcedure(String procedure, List<String> args, Map<String, List<String>> kwargs) {
        StringBuilder queryStringBuilder = new StringBuilder();
        queryStringBuilder.append("CALL ").append(procedure).append('(');
        int i = 0;
        for (; i < args.size() - 1; i++) {
            queryStringBuilder.append(quoteString(args.get(i))).append(',');
        }
        if (i == args.size() - 1) {
            queryStringBuilder.append(quoteString(args.get(i)));
        }
        queryStringBuilder.append(')');
        List<String> kwargsList = kwargs.getOrDefault("y", null);
        if (kwargsList != null && !kwargsList.isEmpty()) {
            i = 0;
            for (; i < kwargsList.size() - 1; i++) {
                queryStringBuilder.append(kwargsList.get(i)).append(',');
            }
            queryStringBuilder.append(kwargsList.get(i));
        }
        return queryStringBuilder.toString();
    }
}
