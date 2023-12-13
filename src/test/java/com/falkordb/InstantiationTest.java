package com.falkordb;

import java.net.URI;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class InstantiationTest {
    private GraphContextGenerator client;

    @Test
    public void createDefaultClient() {
        client = FalkorDB.driver().graph("g");
        ResultSet resultSet = client.query("CREATE ({name:'bsb'})");
        Assert.assertEquals(1, resultSet.getStatistics().nodesCreated());
    }

    @Test
    public void createClientWithHostAndPort() {
        client = FalkorDB.driver("localhost", 6379).graph("g");
        ResultSet resultSet = client.query("CREATE ({name:'bsb'})");
        Assert.assertEquals(1, resultSet.getStatistics().nodesCreated());
    }

    @Test
    public void createClientWithHostAndPortNoUser() {
        client = FalkorDB.driver("localhost", 6379, null, null).graph("g");
        ResultSet resultSet = client.query("CREATE ({name:'bsb'})");
        Assert.assertEquals(1, resultSet.getStatistics().nodesCreated());
    }

    @Test
    public void createClientWithURL() {
        client = FalkorDB.driver(URI.create("redis://localhost:6379")).graph("g");
        ResultSet resultSet = client.query("CREATE ({name:'bsb'})");
        Assert.assertEquals(1, resultSet.getStatistics().nodesCreated());
    }

    @After
    public void closeClient() {
        if (client != null) {
            client.deleteGraph();
            client.close();
        }
    }
}
