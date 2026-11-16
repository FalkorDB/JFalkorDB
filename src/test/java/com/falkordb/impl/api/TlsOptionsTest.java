package com.falkordb.impl.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Collections;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLParameters;
import org.junit.jupiter.api.Test;

/** {@link TlsOptions}: its validation, and that it keeps its own copy of the caller's {@link SSLParameters}. */
class TlsOptionsTest {

    private static final HostnameVerifier ACCEPT_ALL = (host, session) -> true;

    private static SSLParameters verifying() {
        SSLParameters parameters = new SSLParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        return parameters;
    }

    @Test
    void noSettingsMeansNoOptions() {
        assertNull(TlsOptions.of(null, null, null));
    }

    @Test
    void parametersWithoutHostnameVerificationNeedAVerifier() {
        assertThrows(IllegalArgumentException.class, () -> TlsOptions.of(null, new SSLParameters(), null));
        assertNotNull(TlsOptions.of(null, new SSLParameters(), ACCEPT_ALL));
    }

    @Test
    void changingTheCallersParametersAfterwardsChangesNothing() {
        SSLParameters parameters = verifying();
        parameters.setProtocols(new String[] {"TLSv1.2"});
        TlsOptions options = TlsOptions.of(null, parameters, null);

        // What build() checked must stay what every connection uses: switching hostname verification off now
        // would otherwise get round that check.
        parameters.setEndpointIdentificationAlgorithm(null);
        parameters.setProtocols(new String[] {"SSLv3"});

        SSLParameters kept = options.parameters();
        assertNotSame(parameters, kept);
        assertEquals("HTTPS", kept.getEndpointIdentificationAlgorithm());
        assertArrayEquals(new String[] {"TLSv1.2"}, kept.getProtocols());
    }

    @Test
    void theCopyKeepsEveryJava8Property() {
        SSLParameters source = verifying();
        source.setCipherSuites(new String[] {"TLS_AES_128_GCM_SHA256"});
        source.setProtocols(new String[] {"TLSv1.3"});
        source.setNeedClientAuth(true);
        source.setServerNames(Collections.<SNIServerName>singletonList(new SNIHostName("db.example.com")));
        source.setUseCipherSuitesOrder(true);

        SSLParameters copy = TlsOptions.copy(source);

        assertArrayEquals(source.getCipherSuites(), copy.getCipherSuites());
        assertArrayEquals(source.getProtocols(), copy.getProtocols());
        assertTrue(copy.getNeedClientAuth());
        assertEquals("HTTPS", copy.getEndpointIdentificationAlgorithm());
        assertEquals(source.getServerNames(), copy.getServerNames());
        assertTrue(copy.getUseCipherSuitesOrder());
        assertSame(source.getAlgorithmConstraints(), copy.getAlgorithmConstraints());
    }

    @Test
    void theCopyKeepsThePropertiesLaterJdksAdded() throws Exception {
        // The tests compile against the Java 8 API too, so these are reached by reflection, as the copy does.
        SSLParameters source = verifying();
        Method setProtocols = SSLParameters.class.getMethod("setApplicationProtocols", String[].class);
        Method getProtocols = SSLParameters.class.getMethod("getApplicationProtocols");
        Method setPacketSize = SSLParameters.class.getMethod("setMaximumPacketSize", int.class);
        Method getPacketSize = SSLParameters.class.getMethod("getMaximumPacketSize");
        setProtocols.invoke(source, (Object) new String[] {"resp3"});
        setPacketSize.invoke(source, 16_384);

        SSLParameters copy = TlsOptions.copy(source);

        assertArrayEquals(new String[] {"resp3"}, (String[]) getProtocols.invoke(copy));
        assertEquals(16_384, getPacketSize.invoke(copy));
    }
}
