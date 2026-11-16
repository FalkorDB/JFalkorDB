package com.falkordb.impl.api;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Decides which commands {@code sendCommand} and {@code sendBinaryCommand} of a {@code GraphContext.Pipeline} or
 * {@code GraphContext.Transaction} refuse. A refused command is never sent.
 *
 * <p>Command names are compared the way Redis compares them: ASCII letters in any case, nothing else folded, and
 * no locale involved.
 */
final class CommandGuard {

    private CommandGuard() {}

    private static final String OWNED = "is run by this API: ";
    private static final String CONNECTION_STATE =
            "changes the state of the connection, which the pool lends to the next borrower after the context, or"
                    + " how the replies on it are framed";
    private static final String PUSH =
            "takes the connection out of request and reply, so replies can't be paired" + " with commands any more";
    private static final String BLOCKING = "blocks: in a pipeline it holds the connection, and closing the context"
            + " waits for it, while inside MULTI the server doesn't block at all";
    private static final String DELETES_GRAPH = "deletes or replaces the graph's key behind the client's schema"
            + " cache, which would then decode a new graph's replies with the old graph's names: use deleteGraph()";
    private static final String DELETES_EVERY_GRAPH = "deletes every key, the graph's included, behind the client's"
            + " schema cache, which would then decode a new graph's replies with the old graph's names";

    /** Why each command (upper case) refused whatever its arguments is refused, except WAIT. */
    private static final Map<String, String> REFUSED = refused();

    private static Map<String, String> refused() {
        Map<String, String> refused = new HashMap<>();
        refused.put("MULTI", OWNED + "use GraphContext.transaction()");
        refused.put("EXEC", OWNED + "use Transaction.exec()");
        refused.put("DISCARD", OWNED + "use Transaction.discard()");
        refused.put("WATCH", OWNED + "call GraphContext.watch(...) before GraphContext.transaction()");
        refused.put("UNWATCH", OWNED + "use GraphContext.unwatch()");
        for (String command : Arrays.asList(
                "AUTH", "HELLO", "SELECT", "CLIENT", "RESET", "QUIT", "READONLY", "READWRITE", "ASKING")) {
            refused.put(command, CONNECTION_STATE);
        }
        for (String command : Arrays.asList(
                "SUBSCRIBE",
                "PSUBSCRIBE",
                "SSUBSCRIBE",
                "UNSUBSCRIBE",
                "PUNSUBSCRIBE",
                "SUNSUBSCRIBE",
                "MONITOR",
                "SYNC",
                "PSYNC",
                "REPLCONF")) {
            refused.put(command, PUSH);
        }
        for (String command : Arrays.asList(
                "BLPOP", "BRPOP", "BRPOPLPUSH", "BLMOVE", "BLMPOP", "BZPOPMIN", "BZPOPMAX", "BZMPOP", "WAITAOF")) {
            refused.put(command, BLOCKING);
        }
        for (String command : Arrays.asList("FLUSHALL", "FLUSHDB", "SWAPDB")) {
            refused.put(command, DELETES_EVERY_GRAPH);
        }
        return Collections.unmodifiableMap(refused);
    }

    /**
     * Checks a command before it is queued.
     *
     * @param command the command's name
     * @param arguments its arguments, as {@code String}s or {@code byte[]}s
     * @param graphId the name of the context's graph, which is its key
     * @param transaction whether the command is for a transaction rather than a pipeline
     * @throws IllegalArgumentException if the command is refused
     */
    static void check(String command, List<?> arguments, String graphId, boolean transaction) {
        String reason = reason(upperAscii(command), arguments, graphId, transaction);
        if (reason != null) {
            throw new IllegalArgumentException(command + " " + reason);
        }
    }

    private static @Nullable String reason(String name, List<?> arguments, String graphId, boolean transaction) {
        String reason = REFUSED.get(name);
        if (reason != null) {
            return reason;
        }
        if (name.startsWith("GRAPH.")) {
            return OWNED + "use the graph methods, which decode the reply with the client's schema cache and keep"
                    + " that cache up to date";
        }
        switch (name) {
            case "WAIT":
                return transaction ? BLOCKING : OWNED + "use Pipeline.waitReplicas(int, long)";
            case "XREAD":
            case "XREADGROUP":
                return blocks(arguments, name.equals("XREADGROUP")) ? "with BLOCK " + BLOCKING : null;
            case "DEL":
            case "UNLINK":
                return names(arguments, 0, arguments.size(), graphId) ? DELETES_GRAPH : null;
            case "RENAME":
            case "RENAMENX":
                return names(arguments, 0, 2, graphId) ? DELETES_GRAPH : null;
            case "GETDEL":
            case "MOVE":
            case "RESTORE":
                return names(arguments, 0, 1, graphId) ? DELETES_GRAPH : null;
            case "COPY":
                return names(arguments, 1, 2, graphId) ? DELETES_GRAPH : null; // the destination
            case "MIGRATE":
                // MIGRATE host port key|"" db timeout [COPY] [REPLACE] [AUTH ...] [KEYS key ...]
                return names(arguments, 2, 3, graphId) || afterKeys(arguments, graphId) ? DELETES_GRAPH : null;
            case "SCRIPT":
                // SCRIPT DEBUG YES|SYNC makes the connection's next EVAL start the Lua debugger
                return !arguments.isEmpty() && word(arguments.get(0)).equals("DEBUG") ? CONNECTION_STATE : null;
            default:
                return null;
        }
    }

    /** Whether one of the arguments from {@code from} to {@code to} (exclusive, clamped) is the graph's key. */
    private static boolean names(List<?> arguments, int from, int to, String graphId) {
        byte[] key = graphId.getBytes(StandardCharsets.UTF_8);
        for (int i = from; i < Math.min(to, arguments.size()); i++) {
            Object argument = arguments.get(i);
            byte[] bytes = argument instanceof byte[]
                    ? (byte[]) argument
                    : ((String) argument).getBytes(StandardCharsets.UTF_8);
            if (Arrays.equals(bytes, key)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the graph's key is among the keys after {@code MIGRATE}'s {@code KEYS} option. */
    private static boolean afterKeys(List<?> arguments, String graphId) {
        for (int i = 5; i < arguments.size(); i++) {
            String option = word(arguments.get(i));
            if (option.equals("AUTH")) {
                i++; // the password, which may be KEYS
            } else if (option.equals("AUTH2")) {
                i += 2; // the user and the password
            } else if (option.equals("KEYS")) {
                return names(arguments, i + 1, arguments.size(), graphId);
            }
        }
        return false;
    }

    /**
     * Whether an {@code XREAD} or {@code XREADGROUP} has a {@code BLOCK} option. Options come before {@code STREAMS}:
     * {@code GROUP group consumer} first for {@code XREADGROUP}, then {@code COUNT n}, {@code BLOCK ms},
     * {@code CLAIM ms} and {@code NOACK} in any order. A word that is none of these can't be parsed, and then the
     * command counts as blocking if {@code BLOCK} is one of its arguments before the last {@code STREAMS}.
     */
    private static boolean blocks(List<?> arguments, boolean group) {
        int i = 0;
        if (group) {
            if (arguments.isEmpty() || !word(arguments.get(0)).equals("GROUP")) {
                return mentionsBlock(arguments);
            }
            i = 3; // GROUP, the group and the consumer, both free names
        }
        for (; i < arguments.size(); i++) {
            switch (word(arguments.get(i))) {
                case "STREAMS":
                    return false; // keys and ids follow
                case "BLOCK":
                    return true;
                case "COUNT":
                case "CLAIM":
                    i++; // the value
                    break;
                case "NOACK":
                    break;
                default:
                    return mentionsBlock(arguments);
            }
        }
        return false;
    }

    /** Whether {@code BLOCK} is one of the arguments before the last {@code STREAMS}, or of all of them if none. */
    private static boolean mentionsBlock(List<?> arguments) {
        int end = arguments.size();
        for (int i = arguments.size() - 1; i >= 0; i--) {
            if (word(arguments.get(i)).equals("STREAMS")) {
                end = i;
                break;
            }
        }
        for (int i = 0; i < end; i++) {
            if (word(arguments.get(i)).equals("BLOCK")) {
                return true;
            }
        }
        return false;
    }

    private static String word(Object argument) {
        return upperAscii(
                argument instanceof byte[]
                        ? new String((byte[]) argument, StandardCharsets.ISO_8859_1)
                        : (String) argument);
    }

    /** Upper-cases ASCII letters only, as Redis does when it looks a command up; no locale applies. */
    static String upperAscii(String s) {
        char[] chars = s.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c >= 'a' && c <= 'z') {
                chars[i] = (char) (c - ('a' - 'A'));
            }
        }
        return new String(chars);
    }
}
