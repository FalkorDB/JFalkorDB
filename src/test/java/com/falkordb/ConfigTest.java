package com.falkordb;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import redis.clients.jedis.exceptions.JedisDataException;

public class ConfigTest {

    private Driver driver;

    @BeforeEach
    public void setUp() {
        driver = FalkorDB.driver();
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
}
