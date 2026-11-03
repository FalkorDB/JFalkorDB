package com.falkordb;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.exceptions.JedisDataException;

public class ConfigIT {

    private Driver driver;

    @BeforeEach
    public void setUp() {
        driver = TestServer.driver();
    }

    @AfterEach
    public void tearDown() throws IOException {
        driver.close();
    }

    @Test
    public void testConfigGet() {
        // RESULTSET_SIZE is a known FalkorDB configuration parameter
        String value = driver.configGet("RESULTSET_SIZE");
        Assertions.assertNotNull(value, "Config value should not be null");
    }

    @Test
    public void testConfigSetAndGet() {
        // Get the original value
        String originalValue = driver.configGet("RESULTSET_SIZE");
        Assertions.assertNotNull(originalValue);

        try {
            // Set a new value
            boolean success = driver.configSet("RESULTSET_SIZE", 100);
            Assertions.assertTrue(success, "Config set should return true");

            // Verify the new value
            String newValue = driver.configGet("RESULTSET_SIZE");
            Assertions.assertEquals("100", newValue, "Config value should be updated to 100");
        } finally {
            // Always restore original value
            driver.configSet("RESULTSET_SIZE", originalValue);
        }
    }

    @Test
    public void testConfigGetInvalidParameter() {
        // Attempting to get an invalid config parameter should throw a JedisDataException
        Assertions.assertThrows(JedisDataException.class, () -> {
            driver.configGet("INVALID_CONFIG_PARAM_XYZ");
        });
    }

    @Test
    public void testConfigGetRejectsTheWildcard() {
        // "*" names every parameter, which is what configGetAll() is for. configGet used to return a
        // garbled string for it, such as "[[B@3f99bd52, 0]".
        IllegalArgumentException e =
                Assertions.assertThrows(IllegalArgumentException.class, () -> driver.configGet("*"));
        Assertions.assertTrue(e.getMessage().contains("configGetAll()"), e.getMessage());
    }

    @Test
    public void testConfigGetAll() {
        Map<String, String> config = driver.configGetAll();

        Assertions.assertTrue(config.size() > 1, "GRAPH.CONFIG GET * lists every parameter: " + config);
        Assertions.assertTrue(config.containsKey("RESULTSET_SIZE"), config.toString());
        // These can only be set when the module loads, so nothing changes them between the two reads.
        // IMPORT_FOLDER is a string; the other two are integers.
        for (String name : Arrays.asList("THREAD_COUNT", "CACHE_SIZE", "IMPORT_FOLDER")) {
            Assertions.assertEquals(driver.configGet(name), config.get(name), name);
        }
        Assertions.assertThrows(UnsupportedOperationException.class, () -> config.put("RESULTSET_SIZE", "1"));
    }
}
