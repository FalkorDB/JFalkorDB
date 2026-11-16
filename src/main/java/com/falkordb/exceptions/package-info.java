/**
 * Exceptions thrown by the FalkorDB client: {@link com.falkordb.exceptions.GraphException} when the server
 * rejects a command, and {@link com.falkordb.exceptions.FalkorDBConnectionException} when the client can't
 * talk to the server.
 *
 * <p>Both still extend the Jedis exceptions earlier versions threw in their place ({@code JedisDataException}
 * and {@code JedisConnectionException}), so existing {@code catch} clauses keep matching. A later release
 * roots them in a type of the client's own instead; catching these two types keeps working across that
 * change.
 *
 * <p>This package is {@link org.jspecify.annotations.NullMarked}: an unannotated type usage is
 * non-null, and the nullable ones are explicitly annotated {@link org.jspecify.annotations.Nullable}.
 */
@NullMarked
package com.falkordb.exceptions;

import org.jspecify.annotations.NullMarked;
