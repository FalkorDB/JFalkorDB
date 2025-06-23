package com.falkordb;

import java.net.URI;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class InstantiationTest {
    private GraphContextGenerator client;

    @Test
    public void createDefaultClient() {
        client = FalkorDB.driver().graph("g");
        ResultSet resultSet = client.query("CREATE ({name:'bsb'})");
        Assertions.assertEquals(1, resultSet.getStatistics().nodesCreated());
    }

    @Test
    public void createClientWithHostAndPort() {
        client = FalkorDB.driver("localhost", 6379).graph("g");
        ResultSet resultSet = client.query("CREATE ({name:'bsb'})");
        Assertions.assertEquals(1, resultSet.getStatistics().nodesCreated());
    }

    @Test
    public void createClientWithHostAndPortNoUser() {
        client = FalkorDB.driver("localhost", 6379, null, null).graph("g");
        ResultSet resultSet = client.query("CREATE ({name:'bsb'})");
        Assertions.assertEquals(1, resultSet.getStatistics().nodesCreated());
    }

    @Test
    public void createClientWithURL() {
        client = FalkorDB.driver(URI.create("redis://localhost:6379")).graph("g");
        ResultSet resultSet = client.query("CREATE ({name:'bsb'})");
        Assertions.assertEquals(1, resultSet.getStatistics().nodesCreated());
    }

    @AfterEach
    public void closeClient() {
        if (client != null) {
            client.deleteGraph();
            client.close();
        }
    }
}
