package com.falkordb;

import java.net.URI;
import java.util.UUID;

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
    public void createClientWithUserAndPassword() {
        // Unique username so the test never overwrites or deletes a pre-existing
        // ACL user when run against a shared/dev server.
        String username = "jfalkordb_test_" + UUID.randomUUID().toString().replace("-", "");
        try (redis.clients.jedis.Jedis admin = new redis.clients.jedis.Jedis("localhost", 6379)) {
            admin.aclSetUser(username, "on", ">testpass", "~*", "+@all");
            try {
                client = FalkorDB.driver("localhost", 6379, username, "testpass").graph("g");
                ResultSet resultSet = client.query("CREATE ({name:'bsb'})");
                Assertions.assertEquals(1, resultSet.getStatistics().nodesCreated());
                client.deleteGraph();
                client.close();
                client = null;
            } finally {
                admin.aclDelUser(username);
            }
        }
    }

    @Test
    public void createClientSurvivesServerStallLongerThanDefaultTimeout() {
        // Regression guard for the socket-timeout fix: Jedis' default 2000ms socket
        // read timeout would abort a read that takes longer than 2s. With the driver
        // disabling the client-side socket timeout, a command issued while the server
        // is paused for >2s must still succeed instead of failing with a read timeout.
        client = FalkorDB.driver("localhost", 6379).graph("g");
        client.query("RETURN 1"); // establish a pooled connection before pausing the server
        try (redis.clients.jedis.Jedis admin = new redis.clients.jedis.Jedis("localhost", 6379)) {
            admin.clientPause(2500L); // pause command processing for 2.5s (> old 2000ms timeout)
        }
        long start = System.currentTimeMillis();
        ResultSet resultSet = client.query("CREATE ({name:'bsb'})");
        long elapsedMillis = System.currentTimeMillis() - start;
        Assertions.assertEquals(1, resultSet.getStatistics().nodesCreated());
        Assertions.assertTrue(elapsedMillis >= 2000L,
                "query should have blocked through the >2s server pause instead of timing out, but took "
                        + elapsedMillis + "ms");
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
