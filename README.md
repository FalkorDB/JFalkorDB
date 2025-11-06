[![license](https://img.shields.io/github/license/FalkorDB/JFalkorDB.svg)](https://github.com/FalkorDB/JFalkorDB/blob/master/LICENSE)
[![Release](https://img.shields.io/github/release/FalkorDB/JFalkorDB.svg)](https://github.com/FalkorDB/JFalkorDB/releases/latest)
[![Maven Central Version](https://img.shields.io/maven-central/v/com.falkordb/jfalkordb)](https://central.sonatype.com/artifact/com.falkordb/jfalkordb)
[![Javadocs](https://www.javadoc.io/badge/com.falkordb/jfalkordb.svg)](https://www.javadoc.io/doc/com.falkordb/jfalkordb)
[![Codecov](https://codecov.io/gh/FalkorDB/JFalkorDB/branch/master/graph/badge.svg)](https://codecov.io/gh/FalkorDB/JFalkorDB)
[![Known Vulnerabilities](https://snyk.io/test/github/FalkorDB/JFalkorDB/badge.svg?targetFile=pom.xml)](https://snyk.io/test/github/FalkorDB/JFalkorDB?targetFile=pom.xml)

[![Discord](https://img.shields.io/discord/1146782921294884966?style=flat-square)](https://discord.gg/ErBEqN9E)
[![Discuss the project](https://img.shields.io/badge/discussions-FalkorDB-brightgreen.svg)](https://github.com/FalkorDB/FalkorDB/discussions)

# JFalkorDB

[![Try Free](https://img.shields.io/badge/Try%20Free-FalkorDB%20Cloud-FF8101?labelColor=FDE900&style=for-the-badge&link=https://app.falkordb.cloud)](https://app.falkordb.cloud)

FalkorDB Java client

## Official Releases

```xml
  <dependencies>
    <dependency>
      <groupId>com.falkordb</groupId>
      <artifactId>jfalkordb</artifactId>
      <version>0.5.1</version>
    </dependency>
  </dependencies>
```

## Snapshots

```xml
  <repositories>
    <repository>
      <id>snapshots-repo</id>
      <url>https://oss.sonatype.org/content/repositories/snapshots</url>
    </repository>
  </repositories>
```

and

```xml
  <dependencies>
    <dependency>
      <groupId>com.falkordb</groupId>
      <artifactId>jfalkordb</artifactId>
      <version>0.6.0-SNAPSHOT</version>
    </dependency>
  </dependencies>
```

## Example: Using the Java Client

```java
package com.falkordb;

import com.falkordb.graph_entities.Edge;
import com.falkordb.graph_entities.Node;
import com.falkordb.graph_entities.Path;
import com.falkordb.Graph;
import com.falkordb.GraphContext;
import com.falkordb.GraphContextGenerator;
import com.falkordb.GraphTransaction;
import com.falkordb.Record;
import com.falkordb.ResultSet;
import com.falkordb.FalkorDB;
import com.falkordb.Driver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GraphExample {
    public static void main(String[] args) {

        // general context api. Not bound to graph key or connection
        Driver driver = FalkorDB.driver();
        Graph graph = driver.graph("social");

        Map<String, Object> params = new HashMap<>();
        params.put("age", 30);
        params.put("name", "amit");

        // send queries to a specific graph called "social"
        graph.query("CREATE (:person{name:'roi',age:32})");
        graph.query("CREATE (:person{name:$name,age:$age})", params);
        graph.query("MATCH (a:person), (b:person) WHERE (a.name = 'roi' AND b.name='amit') CREATE (a)-[:knows]->(b)");

        ResultSet resultSet = graph.query("MATCH (a:person)-[r:knows]->(b:person) RETURN a, r, b");

        // iterate over result set       
        for(Record record: resultSet) {
            // get values
            Node n = record.getValue("a");
            Edge e =  record.getValue("r");

            //print record
            System.out.println("Node: " + n + ", Edge: " + e);
        }

        resultSet = graph.query("MATCH p = (:person)-[:knows]->(:person) RETURN p");
        for(Record record: resultSet) {
            Path p = record.getValue("p");

            // More path API at Javadoc.
            System.out.println(p.nodeCount());
        }

        // delete graph
        graph.deleteGraph();

        GraphContextGenerator contextGraph = driver.graph("contextSocial");
        // get connection context - closable object
        try(GraphContext context = contextGraph.getContext()) {
            context.query("CREATE (:person{name:'roi',age:32})");
            context.query("MATCH (a:person), (b:person) WHERE (a.name = 'roi' AND b.name='amit') CREATE (a)-[:knows]->(b)");
            // WATCH/MULTI/EXEC
            context.watch("contextSocial");

            GraphTransaction t = context.multi();
            t.query("MATCH (a:person)-[r:knows]->(b:person{name:$name,age:$age}) RETURN a, r, b", params);
            // support for Redis/Jedis native commands in transaction
            t.set("x", "1");
            t.get("x");
            // get multi/exec results
            List<Object> execResults =  t.exec();
            System.out.println(execResults.toString());

            context.deleteGraph();
        }
    }
}
```

## Configuring Connection Pool

You can customize the connection pool to optimize performance and resource usage. JFalkorDB uses [Jedis](https://github.com/redis/jedis) internally, which provides comprehensive pool configuration options.

### Basic Connection Pool Configuration

```java
import com.falkordb.Driver;
import com.falkordb.impl.api.DriverImpl;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import java.time.Duration;

// Create a custom pool configuration
JedisPoolConfig poolConfig = new JedisPoolConfig();

// Maximum number of connections in the pool
poolConfig.setMaxTotal(128);

// Maximum number of idle connections in the pool
// Recommended: Set to maxTotal / 4 or less for optimal resource usage
poolConfig.setMaxIdle(32);

// Minimum number of idle connections in the pool
poolConfig.setMinIdle(8);

// Maximum time to wait for a connection (ms)
poolConfig.setMaxWait(Duration.ofSeconds(30));

// Test connections before borrowing from pool
poolConfig.setTestOnBorrow(true);

// Test connections when returning to pool
poolConfig.setTestOnReturn(true);

// Test idle connections in the pool
poolConfig.setTestWhileIdle(true);

// Create a JedisPool with custom configuration
JedisPool jedisPool = new JedisPool(poolConfig, "localhost", 6379);

// Create the driver with the custom pool
Driver driver = new DriverImpl(jedisPool);

// Use the driver
// ... your code here ...

// Don't forget to close the driver when done
driver.close();
```

### Connection Pool Configuration with Authentication

```java
import com.falkordb.Driver;
import com.falkordb.impl.api.DriverImpl;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;

JedisPoolConfig poolConfig = new JedisPoolConfig();
poolConfig.setMaxTotal(64);
poolConfig.setMaxIdle(32);
poolConfig.setMinIdle(8);

// Configure authentication
JedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
    .user("default")        // username
    .password("your-password") // password
    .build();

// Create pool with authentication and custom pool config
HostAndPort hostAndPort = new HostAndPort("localhost", 6379);
JedisPool jedisPool = new JedisPool(poolConfig, hostAndPort, clientConfig);

Driver driver = new DriverImpl(jedisPool);
```

### Advanced Pool Configuration with Timeouts

```java
import com.falkordb.Driver;
import com.falkordb.impl.api.DriverImpl;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import java.time.Duration;

JedisPoolConfig poolConfig = new JedisPoolConfig();
poolConfig.setMaxTotal(128);
poolConfig.setMaxIdle(64);
poolConfig.setMinIdle(16);
poolConfig.setMaxWait(Duration.ofSeconds(30));

// Configure connection and socket timeouts
JedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
    .connectionTimeoutMillis(2000)  // Connection timeout
    .socketTimeoutMillis(5000)      // Socket/read timeout
    .user("default")                // Username (if needed)
    .password("your-password")      // Password (if needed)
    .ssl(false)                     // Enable SSL if needed
    .build();

// Create pool with advanced configuration
HostAndPort hostAndPort = new HostAndPort("localhost", 6379);
JedisPool jedisPool = new JedisPool(poolConfig, hostAndPort, clientConfig);

Driver driver = new DriverImpl(jedisPool);
```

### Connection Pool Configuration with SSL/TLS

```java
import com.falkordb.Driver;
import com.falkordb.impl.api.DriverImpl;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import javax.net.ssl.SSLSocketFactory;

JedisPoolConfig poolConfig = new JedisPoolConfig();
poolConfig.setMaxTotal(64);
poolConfig.setMaxIdle(32);
poolConfig.setMinIdle(8);

// Configure SSL connection
JedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
    .ssl(true)
    .sslSocketFactory((SSLSocketFactory) SSLSocketFactory.getDefault())
    .user("default")
    .password("your-password")
    .build();

HostAndPort hostAndPort = new HostAndPort("your-server.com", 6380);
JedisPool jedisPool = new JedisPool(poolConfig, hostAndPort, clientConfig);

Driver driver = new DriverImpl(jedisPool);
```

For more information about Jedis pool configuration options, see the [Jedis documentation](https://github.com/redis/jedis).

## License

JFalkorDB is licensed under the [BSD-3-Clause license ](https://github.com/FalkorDB/JFalkorDB/blob/master/LICENSE).
