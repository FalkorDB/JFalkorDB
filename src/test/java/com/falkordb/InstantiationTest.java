package com.falkordb;

import com.falkordb.test.BaseTestContainerTestIT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URI;

public class InstantiationTest extends BaseTestContainerTestIT {
    private GraphContextGenerator client;

    @Test
    public void createDefaultClient() {

        client = FalkorDB.driver(getFalkordbHost(), getFalkordbPort()).graph("g");
        ResultSet resultSet = client.query("CREATE ({name:'bsb'})");
        Assertions.assertEquals(1, resultSet.getStatistics().nodesCreated());
    }

    @Test
    public void createClientWithHostAndPort() {
        client = FalkorDB.driver(getFalkordbHost(), getFalkordbPort()).graph("g");
        ResultSet resultSet = client.query("CREATE ({name:'bsb'})");
        Assertions.assertEquals(1, resultSet.getStatistics().nodesCreated());
    }

    @Test
    public void createClientWithHostAndPortNoUser() {
        client = FalkorDB.driver(getFalkordbHost(), getFalkordbPort(), null, null).graph("g");
        ResultSet resultSet = client.query("CREATE ({name:'bsb'})");
        Assertions.assertEquals(1, resultSet.getStatistics().nodesCreated());
    }

    @Test
    public void createClientWithURL() {
        client = FalkorDB.driver(URI.create(String.format("redis://%s:%d",
                getFalkordbHost(), getFalkordbPort()))).graph("g");
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
