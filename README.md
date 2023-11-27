[![license](https://img.shields.io/github/license/FalkorDB/JFalkorDB.svg)](https://github.com/FalkorDB/JFalkorDB/blob/master/LICENSE)
[![Release](https://img.shields.io/github/release/FalkorDB/JFalkorDB.svg)](https://github.com/FalkorDB/JFalkorDB/releases/latest)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.falkordb/jfalkordb/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.falkordb/jfalkordb)
[![Javadocs](https://www.javadoc.io/badge/com.falkordb/jfalkordb.svg)](https://www.javadoc.io/doc/com.falkordb/jfalkordb)
[![Codecov](https://codecov.io/gh/FalkorDB/JFalkorDB/branch/master/graph/badge.svg)](https://codecov.io/gh/FalkorDB/JFalkorDB)
[![Known Vulnerabilities](https://snyk.io/test/github/FalkorDB/JFalkorDB/badge.svg?targetFile=pom.xml)](https://snyk.io/test/github/FalkorDB/JFalkorDB?targetFile=pom.xml)

# JFalkorDB
[![Discord](https://img.shields.io/discord/1146782921294884966?style=flat-square)](https://discord.gg/ErBEqN9E)

FalkorDB Java client

## Official Releases

```xml
  <dependencies>
    <dependency>
      <groupId>com.falkordb</groupId>
      <artifactId>jfalkordb</artifactId>
      <version>0.0.1</version>
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
      <version>1.0.0-SNAPSHOT</version>
    </dependency>
  </dependencies>
```

## Example: Using the Java Client

```java
package com.falkordb;

import com.falkordb.graph_entities.Edge;
import com.falkordb.graph_entities.Node;
import com.falkordb.graph_entities.Path;
import com.falkordb.impl.api.Graph;

import java.util.List;

public class GraphExample {
    public static void main(String[] args) {

        // general context api. Not bound to graph key or connection
        Graph graph = new Graph();

        Map<String, Object> params = new HashMap<>();
        params.put("age", 30);
        params.put("name", "amit");

        // send queries to a specific graph called "social"
        graph.query("social","CREATE (:person{name:'roi',age:32})");
        graph.query("social","CREATE (:person{name:$name,age:$age})", params);
        graph.query("social","MATCH (a:person), (b:person) WHERE (a.name = 'roi' AND b.name='amit') CREATE (a)-[:knows]->(b)");

        ResultSet resultSet = graph.query("social", "MATCH (a:person)-[r:knows]->(b:person) RETURN a, r, b");
        while(resultSet.hasNext()) {
            Record record = resultSet.next();
            // get values
            Node a = record.getValue("a");
            Edge r =  record.getValue("r");

            //print record
            System.out.println(record.toString());
        }

        resultSet = graph.query("social", "MATCH p = (:person)-[:knows]->(:person) RETURN p");
        while(resultSet.hasNext()) {
            Record record = resultSet.next();
            Path p = record.getValue("p");

            // More path API at Javadoc.
            System.out.println(p.nodeCount());
        }

        // delete graph
        graph.deleteGraph("social");

        // get connection context - closable object
        try(GraphContext context = graph.getContext()) {
            context.query("contextSocial","CREATE (:person{name:'roi',age:32})");
            context.query("social","CREATE (:person{name:$name,age:$age})", params);
            context.query("contextSocial", "MATCH (a:person), (b:person) WHERE (a.name = 'roi' AND b.name='amit') CREATE (a)-[:knows]->(b)");
            // WATCH/MULTI/EXEC
            context.watch("contextSocial");

            GraphTransaction t = context.multi();
            t.query("contextSocial", "MATCH (a:person)-[r:knows]->(b:person{name:$name,age:$age}) RETURN a, r, b", params);
            // support for Redis/Jedis native commands in transaction
            t.set("x", "1");
            t.get("x");
            // get multi/exec results
            List<Object> execResults =  t.exec();
            System.out.println(execResults.toString());

            context.deleteGraph("contextSocial");
        }
    }
}

```

## License

[![FOSSA Status](https://app.fossa.io/api/projects/git%2Bgithub.com%2FFalkorDB%2FJFalkorDB.svg?type=large)](https://app.fossa.io/projects/git%2Bgithub.com%2FFalkorDB%2FJFalkorDB?ref=badge_large)
