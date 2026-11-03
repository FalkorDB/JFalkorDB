package com.falkordb.impl.api;

import com.falkordb.exceptions.GraphException;
import redis.clients.jedis.exceptions.JedisRedirectionException;

/**
 * A command ran, but decoding its reply failed because a lookup it needed was redirected to another cluster
 * node. {@link DriverImpl#routed} reports this rather than retrying: retrying would run a write a second time.
 * The redirect is the direct cause, so {@link #getCause()} tells a {@code MOVED} from an {@code ASK}; the failed
 * lookup is attached as suppressed.
 */
final class ReplyDecodeException extends GraphException {
    private static final long serialVersionUID = 1L;

    ReplyDecodeException(GraphException failure, JedisRedirectionException redirect) {
        super("The command ran, but its reply could not be decoded: " + failure.getMessage(), redirect);
        addSuppressed(failure);
    }
}
