package com.falkordb.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ConnectionUris}. This redaction is the only thing standing between a
 * mistyped connection string and a password in a bug report, so the boundaries are worth pinning
 * directly rather than only through the callers that happen to use it today.
 */
class ConnectionUrisTest {

    @Test
    void redactsCredentialsAfterTheScheme() {
        assertEquals(
                "redis://<redacted>@localhost:6379", ConnectionUris.redact("redis://someone:hunter2@localhost:6379"));
    }

    @Test
    void redactsCredentialsWithNoScheme() {
        // A value that never made it as far as a scheme is still a value someone typed a password
        // into, so the anchor has to cover the start of the string as well as "://".
        assertEquals("<redacted>@localhost:6379", ConnectionUris.redact("someone:hunter2@localhost:6379"));
    }

    @Test
    void leavesAUriWithoutCredentialsAlone() {
        assertEquals("redis://localhost:6379/0", ConnectionUris.redact("redis://localhost:6379/0"));
    }

    @Test
    void doesNotSwallowTheUriBecauseOfAnAtSignAfterTheAuthority() {
        // Excluding /?# from the match keeps a later '@' from anchoring a redaction that would eat the
        // host too, turning a helpful message into an unreadable one.
        assertEquals("redis://localhost:6379/graph@2", ConnectionUris.redact("redis://localhost:6379/graph@2"));
        assertEquals("redis://localhost:6379?tag=a@b", ConnectionUris.redact("redis://localhost:6379?tag=a@b"));
    }

    @Test
    void redactsTheAuthorityEvenWhenAPathAlsoContainsAnAtSign() {
        assertEquals(
                "redis://<redacted>@localhost:6379/graph@2",
                ConnectionUris.redact("redis://someone:hunter2@localhost:6379/graph@2"));
    }

    @Test
    void redactsAMalformedValueThatNoUriParserWouldAccept() {
        // The malformed ones matter most: they are the values that get rejected, and rejection is what
        // produces the message that would carry the password.
        assertEquals("redis://<redacted>@:::not a uri", ConnectionUris.redact("redis://someone:hunter2@:::not a uri"));
    }

    @Test
    void redactsAPasswordContainingAnUnescapedAtSign() {
        // The leak this guards: matching only to the *first* '@' ends the redaction inside the
        // password and republishes everything after it. A password with a literal '@' is also exactly
        // the value a URI parser rejects, so it is the one most likely to reach an error message.
        assertEquals("redis://<redacted>@localhost:6379", ConnectionUris.redact("redis://someone:p@ss@localhost:6379"));
        assertEquals(
                "redis://<redacted>@localhost:6379", ConnectionUris.redact("redis://someone:s3c@ret@localhost:6379"));
    }

    @Test
    void redactsEveryAtSignInTheAuthorityNotJustTheFirstTwo() {
        assertEquals(
                "rediss://<redacted>@localhost:6379/2", ConnectionUris.redact("rediss://u:p@w@d@localhost:6379/2"));
    }

    @Test
    void redactsAnAtSignRiddledPasswordWithNoScheme() {
        assertEquals("<redacted>@localhost:6379", ConnectionUris.redact("someone:p@ss@localhost:6379"));
    }

    @Test
    void stopsAtTheAuthorityEvenWhenThePasswordAlsoContainsAnAtSign() {
        // The greedy match must still not cross into the path: /?# bound it, so the last '@' it can
        // reach is the one ending the authority.
        assertEquals(
                "redis://<redacted>@localhost:6379/graph@2",
                ConnectionUris.redact("redis://someone:p@ss@localhost:6379/graph@2"));
    }

    @Test
    void redactsAUriObject() {
        assertEquals(
                "redis://<redacted>@localhost:6379",
                ConnectionUris.redact(URI.create("redis://someone:hunter2@localhost:6379")));
    }

    @Test
    void describesANullUriWithoutThrowing() {
        // Redaction runs on failure paths; throwing a NullPointerException while building an error
        // message would replace a useful diagnostic with a useless one.
        assertEquals("null", ConnectionUris.redact((URI) null));
    }

    @Test
    void describesANullStringWithoutThrowing() {
        // Same reasoning, and the two overloads have to agree: an overload that throws where its twin
        // returns is a trap for the next caller, who will not check which one they picked.
        assertEquals("null", ConnectionUris.redact((String) null));
    }
}
