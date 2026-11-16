package com.falkordb.impl.api;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import org.jspecify.annotations.Nullable;

/**
 * Custom TLS settings for a driver, as gathered by {@code com.falkordb.FalkorDB.builder()}: an {@link
 * SSLContext}, {@link SSLParameters} and a {@link HostnameVerifier}, each optional. Only JDK types, so the
 * public builder that feeds this names no Jedis type.
 *
 * <p>Internal wiring, like {@link SentinelOptions}, public only because the {@code com.falkordb} factories
 * live in another package. Plain {@code ssl(true)}, with none of these, is not represented here: it keeps
 * Jedis' {@code SslOptions.defaults()}, exactly as before.
 *
 * <p>Instances are immutable and valid: {@link #of} rejects {@code SSLParameters} that would silently
 * switch off hostname verification, and keeps a copy of the ones it accepts, so changing the caller's object
 * afterwards can't get round that check. The {@code SSLContext} and {@code HostnameVerifier} are kept as given:
 * neither is a value that can be copied, and the JDK's own {@code HttpClient} keeps them the same way.
 */
public final class TlsOptions {

    private final @Nullable SSLContext context;
    private final @Nullable SSLParameters parameters;
    private final @Nullable HostnameVerifier hostnameVerifier;

    private TlsOptions(
            @Nullable SSLContext context,
            @Nullable SSLParameters parameters,
            @Nullable HostnameVerifier hostnameVerifier) {
        this.context = context;
        this.parameters = parameters;
        this.hostnameVerifier = hostnameVerifier;
    }

    /**
     * Captures custom TLS settings, or returns {@code null} when none is given.
     *
     * <p>{@code SSLParameters} are used as given, and a fresh {@code new SSLParameters()} has no endpoint
     * identification algorithm, which means the server's hostname is not checked at all. That is easy to do
     * by accident while only meaning to restrict protocols or cipher suites, so it is rejected unless a
     * {@code HostnameVerifier} is set to do the checking: set {@code "HTTPS"} to verify the hostname, or
     * {@code ""} to skip that check knowingly.
     *
     * @param context          supplies the socket factory (trust and key material), or {@code null} for the
     *                         JVM default
     * @param parameters       protocols, cipher suites, SNI and hostname verification, or {@code null} for
     *                         the defaults, which verify the hostname
     * @param hostnameVerifier an extra check run after the handshake, or {@code null} for none
     * @return the options, or {@code null} if all three are {@code null}
     * @throws IllegalArgumentException if {@code parameters} has no endpoint identification algorithm and no
     *     {@code hostnameVerifier} is given
     */
    public static @Nullable TlsOptions of(
            @Nullable SSLContext context,
            @Nullable SSLParameters parameters,
            @Nullable HostnameVerifier hostnameVerifier) {
        if (context == null && parameters == null && hostnameVerifier == null) {
            return null;
        }
        if (parameters != null && parameters.getEndpointIdentificationAlgorithm() == null && hostnameVerifier == null) {
            throw new IllegalArgumentException("sslParameters has no endpoint identification algorithm, so the"
                    + " server's hostname would not be verified: call setEndpointIdentificationAlgorithm(\"HTTPS\")"
                    + " to verify it, or setEndpointIdentificationAlgorithm(\"\") to skip the check deliberately,"
                    + " or set an sslHostnameVerifier");
        }
        return new TlsOptions(context, parameters == null ? null : copy(parameters), hostnameVerifier);
    }

    /**
     * Newer {@link SSLParameters} properties, copied by reflection where the running JDK has them: the library
     * compiles against the Java 8 API, which doesn't declare them.
     */
    private static final String[] LATER_PROPERTIES = {
        "ApplicationProtocols", // Java 9
        "EnableRetransmissions", // Java 9
        "MaximumPacketSize", // Java 9
        "SignatureSchemes", // Java 19
        "NamedGroups" // Java 20
    };

    /**
     * Copies every property of {@code source}, including those added after Java 8 when the running JDK has them.
     *
     * @param source the parameters to copy
     * @return an independent copy
     */
    static SSLParameters copy(SSLParameters source) {
        SSLParameters copy = new SSLParameters();
        copy.setCipherSuites(source.getCipherSuites());
        copy.setProtocols(source.getProtocols());
        if (source.getNeedClientAuth()) {
            copy.setNeedClientAuth(true);
        } else {
            copy.setWantClientAuth(source.getWantClientAuth());
        }
        copy.setAlgorithmConstraints(source.getAlgorithmConstraints());
        copy.setEndpointIdentificationAlgorithm(source.getEndpointIdentificationAlgorithm());
        copy.setServerNames(source.getServerNames());
        copy.setSNIMatchers(source.getSNIMatchers());
        copy.setUseCipherSuitesOrder(source.getUseCipherSuitesOrder());
        for (String property : LATER_PROPERTIES) {
            copyLaterProperty(source, copy, property);
        }
        return copy;
    }

    private static void copyLaterProperty(SSLParameters source, SSLParameters copy, String property) {
        Method getter = publicMethod("get" + property);
        if (getter == null) {
            return; // a JDK older than the property
        }
        Method setter = publicMethod("set" + property, getter.getReturnType());
        if (setter == null) {
            return;
        }
        try {
            setter.invoke(copy, getter.invoke(source));
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("cannot copy SSLParameters." + property, e);
        } catch (InvocationTargetException e) {
            // The value came from a valid SSLParameters, so its setter rejecting it is a JDK inconsistency.
            throw new IllegalArgumentException("cannot copy SSLParameters." + property, e.getCause());
        }
    }

    private static @Nullable Method publicMethod(String name, Class<?>... parameterTypes) {
        try {
            return SSLParameters.class.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * Returns the context supplying the socket factory, or {@code null} for the JVM default.
     *
     * @return the context supplying the socket factory, or {@code null} for the JVM default
     */
    public @Nullable SSLContext context() {
        return context;
    }

    /**
     * Returns the parameters to apply to each socket, or {@code null} for the defaults. They are this instance's
     * own copy: don't change them.
     *
     * @return the parameters to apply to each socket, or {@code null} for the defaults
     */
    public @Nullable SSLParameters parameters() {
        return parameters;
    }

    /**
     * Returns the extra hostname check, or {@code null} for none.
     *
     * @return the extra hostname check, or {@code null} for none
     */
    public @Nullable HostnameVerifier hostnameVerifier() {
        return hostnameVerifier;
    }

    /**
     * Whether these settings change the socket factory or its parameters. Only then must the client config
     * take Jedis' legacy {@code sslSocketFactory}/{@code sslParameters} path: a verifier on its own goes on
     * top of {@code SslOptions.defaults()}, so setting one doesn't change where key and trust material come
     * from.
     *
     * @return {@code true} if a context or parameters are set
     */
    boolean replacesSocketFactory() {
        return context != null || parameters != null;
    }
}
