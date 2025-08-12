package com.falkordb.test;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

public class BaseTestIT {
    private static final Logger log = LoggerFactory.getLogger(BaseTestIT.class);
    public static final DockerImageName FALOKRDB_IMAGE = DockerImageName.parse("falkordb/falkordb:latest");

    @Container
    protected static GenericContainer<?> continerFalkordb;
    private static final int falkordbPort = 6379; // Default port for Falkordb, adjust if necessary

    @BeforeAll
    public static void setUpContainer() {
        continerFalkordb = new GenericContainer<>(FALOKRDB_IMAGE)
                .withExposedPorts(falkordbPort)
                .withLogConsumer(new Slf4jLogConsumer(log)); // Replace 6379 with Falkordb's default port if different
        continerFalkordb.start();
    }

    @AfterAll
    public static void tearDownContainer() {
        if (continerFalkordb != null) {
            continerFalkordb.stop();
        }
    }

    protected int getFalkordbPort() {
        return continerFalkordb.getFirstMappedPort();
    }

    protected String getFalkordbHost() {
        return continerFalkordb.getHost();
    }
}
