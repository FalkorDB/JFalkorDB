package com.falkordb;

import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * System tests for {@link FalkorDB#builder()} against a real FalkorDB server (via {@link TestServer}).
 */
public class ConfigBuilderIT {

    private Driver driver;
    private GraphContextGenerator client;

    @Test
    public void buildAndQuery() {
        driver = FalkorDB.builder()
                .host(TestServer.host())
                .port(TestServer.port())
                .build();
        client = driver.graph("g");
        ResultSet resultSet = client.query("CREATE ({name:'bsb'})");
        Assertions.assertEquals(1, resultSet.getStatistics().nodesCreated());
    }

    @Test
    public void buildWithCustomPoolAndTimeouts() {
        driver = FalkorDB.builder()
                .host(TestServer.host())
                .port(TestServer.port())
                .poolMaxTotal(4)
                .poolMaxIdle(2)
                .connectionTimeout(java.time.Duration.ofSeconds(2))
                .build();
        client = driver.graph("g");
        ResultSet resultSet = client.query("CREATE ({name:'bsb'})");
        Assertions.assertEquals(1, resultSet.getStatistics().nodesCreated());
    }

    @AfterEach
    public void closeClient() throws IOException {
        try {
            if (client != null) {
                client.deleteGraph();
            }
        } finally {
            if (driver != null) {
                driver.close();
            }
        }
    }
}
