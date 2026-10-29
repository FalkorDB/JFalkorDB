package com.falkordb.impl.api;

import com.falkordb.exceptions.GraphException;

/**
 * A command ran, but decoding its reply failed because a lookup it needed was redirected to another cluster
 * node. {@link DriverImpl#routed} reports this rather than retrying: retrying would run a write a second time.
 * The redirect stays the cause.
 */
final class ReplyDecodeException extends GraphException {
    private static final long serialVersionUID = 1L;

    ReplyDecodeException(GraphException cause) {
        super("The command ran, but its reply could not be decoded: " + cause.getMessage(), cause);
    }
}
