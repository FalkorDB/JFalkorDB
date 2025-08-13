package com.falkordb.test;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

public class BaseTestContainerTestIT {
    private static final Logger log = LoggerFactory.getLogger(BaseTestContainerTestIT.class);
    public static final DockerImageName FALKORDB_IMAGE = DockerImageName.parse("falkordb/falkordb:latest");
    public static final int FALKORDB_PORT = 6379;

    private static GenericContainer<?> containerFalkorDB;
    private static final int falkordbPort = 6379; // Default port for Falkordb, adjust if necessary

    @BeforeAll
    public static void setUpContainer() {
        // allow overriding image via -Dfalkordb.image=repo/image:tag
        DockerImageName image = DockerImageName.parse(
                System.getProperty("falkordb.image", FALKORDB_IMAGE.asCanonicalNameString())
        );
        containerFalkorDB = new GenericContainer<>(image)
                .withExposedPorts(FALKORDB_PORT)
                .withLogConsumer(new Slf4jLogConsumer(log))
                .waitingFor(Wait.forListeningPort())
                .withStartupTimeout(Duration.ofSeconds(90));
        containerFalkorDB.start();
    }

    @AfterAll
    public static void tearDownContainer() {
        if (containerFalkorDB != null) {
            containerFalkorDB.stop();
        }
    }

    protected int getFalkordbPort() {
        return containerFalkorDB.getFirstMappedPort();
    }

    protected String getFalkordbHost() {
        return containerFalkorDB.getHost();
    }
}
