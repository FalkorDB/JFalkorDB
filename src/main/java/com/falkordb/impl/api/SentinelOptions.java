package com.falkordb.impl.api;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import redis.clients.jedis.HostAndPort;

/**
 * Resolved Redis Sentinel settings for a driver, as gathered by {@code com.falkordb.FalkorDB}.
 *
 * <p>Internal wiring: it exists so {@link DriverImpl#create} keeps a readable signature instead of
 * growing five more positional parameters, and it is public only because the {@code com.falkordb}
 * factories live in another package. Users configure these through {@code FalkorDB.builder()}.
 *
 * <p>A value is in exactly one of three states:
 *
 * <ul>
 *   <li>{@linkplain #explicit explicit} — the deployment is named up front, so no probing happens and
 *       several Sentinels can be listed.
 *   <li>{@linkplain #autoDetect() auto-detect} — the default: connect to whatever the caller gave,
 *       and switch to Sentinel only if that endpoint turns out to be one.
 *   <li>{@linkplain #disabled() disabled} — always connect directly, never probe.
 * </ul>
 *
 * <p>Instances are immutable; {@link #withCredentials} returns a copy.
 */
public final class SentinelOptions {

    private final boolean autoDetect;
    private final @Nullable String masterName;
    private final Set<HostAndPort> addresses;
    private final @Nullable String user;
    private final @Nullable String password;

    private SentinelOptions(
            boolean autoDetect,
            @Nullable String masterName,
            Set<HostAndPort> addresses,
            @Nullable String user,
            @Nullable String password) {
        this.autoDetect = autoDetect;
        this.masterName = masterName;
        this.addresses = addresses;
        this.user = user;
        this.password = password;
    }

    /**
     * Probe the endpoint the caller asked for and use Sentinel only if it turns out to be one. This is
     * the default, and matches the other FalkorDB clients.
     *
     * @return auto-detecting options
     */
    public static SentinelOptions autoDetect() {
        // A fresh instance rather than a shared constant: the class is immutable, but SpotBugs cannot
        // see that through the Set field and flags handing the constant out (MS_EXPOSE_REP). Allocating
        // once per driver is not worth suppressing a warning over.
        return new SentinelOptions(true, null, Collections.<HostAndPort>emptySet(), null, null);
    }

    /**
     * Never probe: connect directly to the endpoint the caller asked for.
     *
     * @return options with Sentinel support switched off
     */
    public static SentinelOptions disabled() {
        return new SentinelOptions(false, null, Collections.<HostAndPort>emptySet(), null, null);
    }

    /**
     * Connect through the named Sentinel deployment, without probing.
     *
     * <p>The addresses are parsed here rather than when the pool is eventually built, so a typo is
     * reported by the call that contains it instead of by the first query minutes later. Only the
     * network probe is deferred; validating static configuration needs no I/O and so is done eagerly.
     *
     * @param masterName name of the monitored master, as given to {@code sentinel monitor}
     * @param addresses  the Sentinel endpoints in {@code host:port} form; must not be empty
     * @return explicit Sentinel options
     * @throws IllegalArgumentException if {@code masterName} is null or blank, or {@code addresses} is
     *     null, empty, or contains an entry that is not a valid {@code host:port}
     */
    public static SentinelOptions explicit(@Nullable String masterName, @Nullable Collection<String> addresses) {
        if (masterName == null || masterName.trim().isEmpty()) {
            throw new IllegalArgumentException("sentinel master name must not be null or blank");
        }
        return new SentinelOptions(
                false, masterName.trim(), Collections.unmodifiableSet(Sentinels.parseAddresses(addresses)), null, null);
    }

    /**
     * Returns a copy authenticating to the Sentinels themselves with the given credentials. Sentinels
     * commonly carry their own ACLs, separate from the data nodes'; when this is left unset the data
     * credentials are reused, as falkordb-go does.
     *
     * @param user     Sentinel username, or {@code null} for password-only authentication
     * @param password Sentinel password, or {@code null} for none
     * @return a copy carrying these Sentinel credentials
     */
    public SentinelOptions withCredentials(@Nullable String user, @Nullable String password) {
        return new SentinelOptions(autoDetect, masterName, addresses, user, password);
    }

    /**
     * Returns whether an unrecognised endpoint should be probed for Sentinel mode.
     *
     * @return whether an unrecognised endpoint should be probed for Sentinel mode
     */
    boolean isAutoDetect() {
        return autoDetect;
    }

    /**
     * Returns whether the master and Sentinel addresses were configured explicitly.
     *
     * @return whether the master and Sentinel addresses were configured explicitly
     */
    boolean isExplicit() {
        return masterName != null;
    }

    /**
     * Returns the configured master name.
     *
     * @return the master name, or {@code null} when not {@linkplain #isExplicit explicit}
     */
    @Nullable
    String masterName() {
        return masterName;
    }

    /**
     * Returns the parsed Sentinel endpoints.
     *
     * @return the Sentinel endpoints, empty when not {@linkplain #isExplicit explicit}
     */
    Set<HostAndPort> addresses() {
        return addresses;
    }

    /**
     * Returns the username to authenticate with against the Sentinels.
     *
     * @return the Sentinel username, or {@code null} to reuse the data credentials
     */
    @Nullable
    String user() {
        return user;
    }

    /**
     * Returns the password to authenticate with against the Sentinels.
     *
     * @return the Sentinel password, or {@code null} to reuse the data credentials
     */
    @Nullable
    String password() {
        return password;
    }

    /**
     * Returns whether Sentinel-specific credentials were set.
     *
     * @return whether Sentinel-specific credentials were set
     */
    boolean hasCredentials() {
        return user != null || password != null;
    }
}
