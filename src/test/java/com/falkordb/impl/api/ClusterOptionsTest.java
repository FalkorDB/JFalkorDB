package com.falkordb.impl.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.HostAndPort;

class ClusterOptionsTest {

    @Test
    void autoDetectProbesButNamesNoNodes() {
        ClusterOptions options = ClusterOptions.autoDetect();
        assertTrue(options.isAutoDetect());
        assertFalse(options.isExplicit());
        assertTrue(options.startupNodes().isEmpty());
    }

    @Test
    void disabledNeitherProbesNorNamesNodes() {
        ClusterOptions options = ClusterOptions.disabled();
        assertFalse(options.isAutoDetect());
        assertFalse(options.isExplicit());
    }

    @Test
    void explicitKeepsTheNodesInTheOrderGiven() {
        ClusterOptions options = ClusterOptions.explicit(Arrays.asList("b:2", "a:1", "c:3"));
        assertTrue(options.isExplicit());
        assertFalse(options.isAutoDetect());
        assertEquals(
                Arrays.asList(new HostAndPort("b", 2), new HostAndPort("a", 1), new HostAndPort("c", 3)),
                new ArrayList<>(options.startupNodes()));
    }

    @Test
    void explicitDropsDuplicates() {
        assertEquals(
                1,
                ClusterOptions.explicit(Arrays.asList("a:1", " a:1 "))
                        .startupNodes()
                        .size());
    }

    @Test
    void explicitNodesCannotBeModifiedAfterwards() {
        List<String> given = new ArrayList<>(Collections.singletonList("a:1"));
        ClusterOptions options = ClusterOptions.explicit(given);
        given.add("b:2");
        assertEquals(1, options.startupNodes().size());
        assertThrows(UnsupportedOperationException.class, () -> options.startupNodes()
                .add(new HostAndPort("c", 3)));
    }

    @Test
    void explicitRejectsAnEmptyOrMissingList() {
        assertThrows(IllegalArgumentException.class, () -> ClusterOptions.explicit(null));
        assertThrows(IllegalArgumentException.class, () -> ClusterOptions.explicit(Collections.<String>emptyList()));
    }

    @Test
    void explicitRejectsAMalformedAddressAtOnce() {
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class, () -> ClusterOptions.explicit(Arrays.asList("a:1", "typo")));
        assertTrue(e.getMessage().contains("typo"), e.getMessage());
    }
}
