package com.falkordb;

import java.net.URI;

import org.junit.After;
import org.junit.Assert;

public class InstantiationTest {
    private GraphContextGenerator client;

    public void createDefaultClient() {
        client = FalkorDB.driver().graph("g");
        ResultSet resultSet = client.query("CREATE ({name:'bsb'})");
        Assert.assertEquals(1, resultSet.getStatistics().nodesCreated());
    }

    public void createClientWithHostAndPort() {
        client = FalkorDB.driver("localhost", 6379).graph("g");
        ResultSet resultSet = client.query("CREATE ({name:'bsb'})");
        Assert.assertEquals(1, resultSet.getStatistics().nodesCreated());
    }

    public void createClientWithHostAndPortNoUser() {
        client = FalkorDB.driver("localhost", 6379, null, null).graph("g");
        ResultSet resultSet = client.query("CREATE ({name:'bsb'})");
        Assert.assertEquals(1, resultSet.getStatistics().nodesCreated());
    }

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
