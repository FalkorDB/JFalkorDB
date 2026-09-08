package com.falkordb.impl;

import java.util.regex.Pattern;

/**
 * Helpers for talking about connection URIs without leaking what is embedded in them.
 *
 * <p>A connection URI routinely carries a password in its userinfo segment, so any message that
 * quotes one back — an exception, a log line, a crash report — is a credential disclosure waiting to
 * be filed as a bug. This lives in one place because the redaction has to be identical everywhere:
 * two copies of a rule like this drift, and the copy that drifts is the one that leaks.
 */
public final class ConnectionUris {

    /**
     * Matches a {@code userinfo@} segment, anchored either at the start of the value or at the
     * {@code ://} after the scheme. Excluding {@code /?#} keeps the match inside the authority, so a
     * later {@code @} in a path or query cannot drag the rest of the URI into the redaction.
     */
    private static final Pattern USERINFO_PREFIX = Pattern.compile("(^|://)[^/?#@]*@");

    private ConnectionUris() {}

    /**
     * Replaces any credentials in a connection URI with a fixed placeholder.
     *
     * <p>Deliberately operates on the text rather than on {@link java.net.URI} accessors: the values
     * that most need redacting are the malformed ones, and a URI that Java cannot parse into an
     * authority still has the password sitting in plain sight in its string form.
     *
     * @param value a connection URI in string form, possibly malformed
     * @return the same value with any {@code userinfo@} segment replaced
     */
    public static String redact(String value) {
        return USERINFO_PREFIX.matcher(value).replaceAll("$1<redacted>@");
    }

    /**
     * Renders a connection URI for use in a message, with any credentials replaced.
     *
     * @param uri the URI to describe, possibly {@code null}
     * @return the URI in string form with any {@code userinfo@} segment replaced
     */
    public static String redact(java.net.URI uri) {
        return redact(String.valueOf(uri));
    }
}
