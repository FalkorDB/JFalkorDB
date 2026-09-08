package com.falkordb.impl.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.HostAndPort;

/**
 * Unit tests for {@link SentinelOptions}: the three states it can express, their validation, and the
 * immutability {@link SentinelOptions#withCredentials} relies on.
 */
class SentinelOptionsTest {

    @Test
    void autoDetectProbesButIsNotExplicit() {
        SentinelOptions options = SentinelOptions.autoDetect();

        assertTrue(options.isAutoDetect());
        assertFalse(options.isExplicit());
        assertNull(options.masterName());
        assertTrue(options.addresses().isEmpty());
        assertFalse(options.hasCredentials());
    }

    @Test
    void disabledNeitherProbesNorNamesADeployment() {
        SentinelOptions options = SentinelOptions.disabled();

        assertFalse(options.isAutoDetect());
        assertFalse(options.isExplicit());
    }

    @Test
    void explicitNamesTheDeploymentAndSuppressesProbing() {
        SentinelOptions options = SentinelOptions.explicit("mymaster", Arrays.asList("a:26379", "b:26379"));

        assertTrue(options.isExplicit());
        assertFalse(options.isAutoDetect(), "an explicitly named deployment needs no probe");
        assertEquals("mymaster", options.masterName());
        assertEquals(endpoints("a:26379", "b:26379"), options.addresses());
    }

    @Test
    void explicitTrimsTheMasterName() {
        assertEquals(
                "mymaster",
                SentinelOptions.explicit("  mymaster  ", Collections.singletonList("a:26379"))
                        .masterName());
    }

    @Test
    void explicitRejectsAMissingMasterName() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SentinelOptions.explicit(null, Collections.singletonList("a:26379")));
        assertThrows(
                IllegalArgumentException.class,
                () -> SentinelOptions.explicit("   ", Collections.singletonList("a:26379")));
    }

    @Test
    void explicitRejectsAMissingAddressList() {
        assertThrows(IllegalArgumentException.class, () -> SentinelOptions.explicit("mymaster", null));
        assertThrows(
                IllegalArgumentException.class,
                () -> SentinelOptions.explicit("mymaster", Collections.<String>emptyList()));
    }

    @Test
    void explicitRejectsAMalformedAddressUpFront() {
        // Parsing here rather than at pool-construction time is what lets a typo surface from the call
        // that contains it, instead of from the first query long after the driver was built.
        assertThrows(
                IllegalArgumentException.class,
                () -> SentinelOptions.explicit("mymaster", Arrays.asList("a:26379", "b-without-a-port")));
    }

    @Test
    void explicitCopiesTheCallersAddressList() {
        // The builder hands over a list it still holds; mutating it afterwards must not change us.
        java.util.List<String> mutable = new java.util.ArrayList<>(Collections.singletonList("a:26379"));
        SentinelOptions options = SentinelOptions.explicit("mymaster", mutable);
        mutable.add("b:26379");

        assertEquals(endpoints("a:26379"), options.addresses());
    }

    @Test
    void withCredentialsReturnsACopyPreservingTheRest() {
        SentinelOptions explicit = SentinelOptions.explicit("mymaster", Collections.singletonList("a:26379"));
        SentinelOptions authenticated = explicit.withCredentials("sentinel-user", "sentinel-password");

        assertFalse(explicit.hasCredentials(), "the original must be untouched");
        assertTrue(authenticated.hasCredentials());
        assertEquals("sentinel-user", authenticated.user());
        assertEquals("sentinel-password", authenticated.password());
        assertEquals("mymaster", authenticated.masterName());
        assertEquals(endpoints("a:26379"), authenticated.addresses());
    }

    @Test
    void passwordOnlySentinelCredentialsCount() {
        assertTrue(SentinelOptions.autoDetect().withCredentials(null, "p").hasCredentials());
        assertFalse(SentinelOptions.autoDetect().withCredentials(null, null).hasCredentials());
    }

    private static Set<HostAndPort> endpoints(String... addresses) {
        Set<HostAndPort> expected = new LinkedHashSet<>();
        for (String address : addresses) {
            expected.add(HostAndPort.from(address));
        }
        return expected;
    }
}
