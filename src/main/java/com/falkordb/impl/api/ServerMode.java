package com.falkordb.impl.api;

import org.jspecify.annotations.Nullable;

/**
 * Reads the server's mode ({@code standalone}, {@code cluster} or {@code sentinel}) out of an {@code
 * INFO server} reply, for the Sentinel and cluster auto-detection.
 *
 * <p>Redis reports the mode as {@code redis_mode:<mode>}. Valkey reports it as {@code
 * server_mode:<mode>} unless {@code extended-redis-compatibility} is switched on (it is off by
 * default), and FalkorDB images run on either server, so both field names are accepted.
 */
final class ServerMode {

    /** The field names a server may report its mode under: Redis' first, then Valkey's. */
    private static final String[] MODE_FIELDS = {"redis_mode:", "server_mode:"};

    private ServerMode() {}

    /**
     * Returns the mode an {@code INFO server} reply reports.
     *
     * <p>The reply is parsed line-wise rather than searched, so a value merely appearing somewhere
     * else in it — a {@code config_file} path containing {@code server_mode:cluster}, say — cannot be
     * mistaken for the mode. The first line naming a mode wins. Redis separates INFO lines with CRLF;
     * LF and CR alone are accepted too, and surrounding whitespace is ignored.
     *
     * @param info an {@code INFO server} reply, or {@code null}
     * @return the reported mode, trimmed but in the server's own case, or {@code null} if the reply
     *     names none
     */
    static @Nullable String of(@Nullable String info) {
        if (info == null) {
            return null;
        }
        for (String line : info.split("\r\n|\n|\r", -1)) {
            String trimmed = line.trim();
            for (String field : MODE_FIELDS) {
                if (trimmed.startsWith(field)) {
                    return trimmed.substring(field.length()).trim();
                }
            }
        }
        return null;
    }
}
