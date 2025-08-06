package com.falkordb;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.falkordb.Statistics.Label;
import com.falkordb.exceptions.GraphException;
import com.falkordb.graph_entities.Edge;
import com.falkordb.graph_entities.Node;
import com.falkordb.graph_entities.Path;
import com.falkordb.graph_entities.Point;
import com.falkordb.graph_entities.Property;
import com.falkordb.test.utils.PathBuilder;

public class GraphAPITest {

    private GraphContextGenerator client;

    @BeforeEach
    public void createApi() {
        client = FalkorDB.driver().graph("social");
    }

    @AfterEach
    public void deleteGraph() {
        client.deleteGraph();
        client.close();
    }

    @Test
    public void testCreateNode() {
        // Create a node
        ResultSet resultSet = client.query("CREATE ({name:'roi',age:32})");

        Assertions.assertEquals(1, resultSet.getStatistics().nodesCreated());
        Assertions.assertNull(resultSet.getStatistics().getStringValue(Label.NODES_DELETED));
        Assertions.assertNull(resultSet.getStatistics().getStringValue(Label.RELATIONSHIPS_CREATED));
        Assertions.assertNull(resultSet.getStatistics().getStringValue(Label.RELATIONSHIPS_DELETED));
        Assertions.assertEquals(2, resultSet.getStatistics().propertiesSet());
        Assertions.assertNotNull(resultSet.getStatistics().getStringValue(Label.QUERY_INTERNAL_EXECUTION_TIME));

        Iterator<Record> iterator = resultSet.iterator();
        Assertions.assertFalse(iterator.hasNext());

        try {
            iterator.next();
            fail("Expected NoSuchElementException was not thrown.");
        } catch (NoSuchElementException ignored) {
        }
    }

    @Test
    public void testCreateLabeledNode() {
        // Create a node with a label
        ResultSet resultSet = client.query("CREATE (:human{name:'danny',age:12})");
        Assertions.assertFalse(resultSet.iterator().hasNext());
        Assertions.assertEquals("1", resultSet.getStatistics().getStringValue(Label.NODES_CREATED));
        Assertions.assertEquals("2", resultSet.getStatistics().getStringValue(Label.PROPERTIES_SET));
        Assertions.assertNotNull(resultSet.getStatistics().getStringValue(Label.QUERY_INTERNAL_EXECUTION_TIME));
    }

    @Test
    public void testConnectNodes() {
        // Create both source and destination nodes
        Assertions.assertNotNull(client.query("CREATE (:person{name:'roi',age:32})"));
        Assertions.assertNotNull(client.query("CREATE (:person{name:'amit',age:30})"));

        // Connect source and destination nodes.
        ResultSet resultSet = client.query(
                "MATCH (a:person), (b:person) WHERE (a.name = 'roi' AND b.name='amit')  CREATE (a)-[:knows]->(b)");

        Assertions.assertFalse(resultSet.iterator().hasNext());
        Assertions.assertNull(resultSet.getStatistics().getStringValue(Label.NODES_CREATED));
        Assertions.assertNull(resultSet.getStatistics().getStringValue(Label.PROPERTIES_SET));
        Assertions.assertEquals(1, resultSet.getStatistics().relationshipsCreated());
        Assertions.assertEquals(0, resultSet.getStatistics().relationshipsDeleted());
        Assertions.assertNotNull(resultSet.getStatistics().getStringValue(Label.QUERY_INTERNAL_EXECUTION_TIME));
    }

    @Test
    public void testDeleteNodes() {
        Assertions.assertNotNull(client.query("CREATE (:person{name:'roi',age:32})"));
        Assertions.assertNotNull(client.query("CREATE (:person{name:'amit',age:30})"));
        ResultSet deleteResult = client.query("MATCH (a:person) WHERE (a.name = 'roi') DELETE a");

        Assertions.assertFalse(deleteResult.iterator().hasNext());
        Assertions.assertNull(deleteResult.getStatistics().getStringValue(Label.NODES_CREATED));
        Assertions.assertNull(deleteResult.getStatistics().getStringValue(Label.PROPERTIES_SET));
        Assertions.assertNull(deleteResult.getStatistics().getStringValue(Label.NODES_CREATED));
        Assertions.assertNull(deleteResult.getStatistics().getStringValue(Label.RELATIONSHIPS_CREATED));
        Assertions.assertNull(deleteResult.getStatistics().getStringValue(Label.RELATIONSHIPS_DELETED));
        Assertions.assertEquals(1, deleteResult.getStatistics().nodesDeleted());
        Assertions.assertNotNull(deleteResult.getStatistics().getStringValue(Label.QUERY_INTERNAL_EXECUTION_TIME));

        Assertions.assertNotNull(client.query("CREATE (:person{name:'roi',age:32})"));
        Assertions.assertNotNull(client.query(
                "MATCH (a:person), (b:person) WHERE (a.name = 'roi' AND b.name='amit')  CREATE (a)-[:knows]->(b)"));
        deleteResult = client.query("MATCH (a:person) WHERE (a.name = 'roi') DELETE a");

        Assertions.assertFalse(deleteResult.iterator().hasNext());
        Assertions.assertNull(deleteResult.getStatistics().getStringValue(Label.NODES_CREATED));
        Assertions.assertNull(deleteResult.getStatistics().getStringValue(Label.PROPERTIES_SET));
        Assertions.assertNull(deleteResult.getStatistics().getStringValue(Label.NODES_CREATED));
        Assertions.assertNull(deleteResult.getStatistics().getStringValue(Label.RELATIONSHIPS_CREATED));
        Assertions.assertEquals(1, deleteResult.getStatistics().relationshipsDeleted());
        Assertions.assertEquals(1, deleteResult.getStatistics().nodesDeleted());

        Assertions.assertNotNull(deleteResult.getStatistics().getStringValue(Label.QUERY_INTERNAL_EXECUTION_TIME));

    }

    @Test
    public void testDeleteRelationship() {

        Assertions.assertNotNull(client.query("CREATE (:person{name:'roi',age:32})"));
        Assertions.assertNotNull(client.query("CREATE (:person{name:'amit',age:30})"));
        Assertions.assertNotNull(client.query(
                "MATCH (a:person), (b:person) WHERE (a.name = 'roi' AND b.name='amit')  CREATE (a)-[:knows]->(a)"));
        ResultSet deleteResult = client.query("MATCH (a:person)-[e]->() WHERE (a.name = 'roi') DELETE e");

        Assertions.assertFalse(deleteResult.iterator().hasNext());
        Assertions.assertNull(deleteResult.getStatistics().getStringValue(Label.NODES_CREATED));
        Assertions.assertNull(deleteResult.getStatistics().getStringValue(Label.PROPERTIES_SET));
        Assertions.assertNull(deleteResult.getStatistics().getStringValue(Label.NODES_CREATED));
        Assertions.assertNull(deleteResult.getStatistics().getStringValue(Label.RELATIONSHIPS_CREATED));
        Assertions.assertNull(deleteResult.getStatistics().getStringValue(Label.NODES_DELETED));
        Assertions.assertEquals(1, deleteResult.getStatistics().relationshipsDeleted());

        Assertions.assertNotNull(deleteResult.getStatistics().getStringValue(Label.QUERY_INTERNAL_EXECUTION_TIME));

    }

    @Test
    public void testIndex() {
        // Create both source and destination nodes
        Assertions.assertNotNull(client.query("CREATE (:person{name:'roi',age:32})"));

        ResultSet createIndexResult = client.query("CREATE INDEX ON :person(age)");
        Assertions.assertFalse(createIndexResult.iterator().hasNext());
        Assertions.assertEquals(1, createIndexResult.getStatistics().indicesAdded());

        // since RediSearch as index, those action are allowed
        ResultSet createNonExistingIndexResult = client.query("CREATE INDEX ON :person(age1)");
        Assertions.assertFalse(createNonExistingIndexResult.iterator().hasNext());
        Assertions.assertNotNull(createNonExistingIndexResult.getStatistics().getStringValue(Label.INDICES_ADDED));
        Assertions.assertEquals(1, createNonExistingIndexResult.getStatistics().indicesAdded());

        try {
            client.query("CREATE INDEX ON :person(age)");
            fail("Expected Exception was not thrown.");
        } catch (Exception e) {
        }

        ResultSet deleteExistingIndexResult = client.query("DROP INDEX ON :person(age)");
        Assertions.assertFalse(deleteExistingIndexResult.iterator().hasNext());
        Assertions.assertNotNull(deleteExistingIndexResult.getStatistics().getStringValue(Label.INDICES_DELETED));
        Assertions.assertEquals(1, deleteExistingIndexResult.getStatistics().indicesDeleted());

    }

    @Test
    public void testHeader() {

        Assertions.assertNotNull(client.query("CREATE (:person{name:'roi',age:32})"));
        Assertions.assertNotNull(client.query("CREATE (:person{name:'amit',age:30})"));
        Assertions.assertNotNull(client.query(
                "MATCH (a:person), (b:person) WHERE (a.name = 'roi' AND b.name='amit')  CREATE (a)-[:knows]->(a)"));

        ResultSet queryResult = client.query("MATCH (a:person)-[r:knows]->(b:person) RETURN a,r, a.age");

        Header header = queryResult.getHeader();
        Assertions.assertNotNull(header);
        Assertions.assertEquals("HeaderImpl{"
                + "schemaTypes=[COLUMN_SCALAR, COLUMN_SCALAR, COLUMN_SCALAR], "
                + "schemaNames=[a, r, a.age]}", header.toString());

        List<String> schemaNames = header.getSchemaNames();

        Assertions.assertNotNull(schemaNames);
        Assertions.assertEquals(3, schemaNames.size());
        Assertions.assertEquals("a", schemaNames.get(0));
        Assertions.assertEquals("r", schemaNames.get(1));
        Assertions.assertEquals("a.age", schemaNames.get(2));

    }

    @Test
    public void testRecord() {
        String name = "roi";
        int age = 32;
        double doubleValue = 3.14;
        boolean boolValue = true;

        String place = "TLV";
        int since = 2000;

        Property<String> nameProperty = new Property<>("name", name);
        Property<Integer> ageProperty = new Property<>("age", age);
        Property<Double> doubleProperty = new Property<>("doubleValue", doubleValue);
        Property<Boolean> trueBooleanProperty = new Property<>("boolValue", true);
        Property<Boolean> falseBooleanProperty = new Property<>("boolValue", false);

        Node expectedNode = new Node();
        expectedNode.setId(0);
        expectedNode.addLabel("person");
        expectedNode.addProperty(nameProperty);
        expectedNode.addProperty(ageProperty);
        expectedNode.addProperty(doubleProperty);
        expectedNode.addProperty(trueBooleanProperty);
        Assertions.assertEquals(
                "Node{labels=[person], id=0, "
                        + "propertyMap={name=Property{name='name', value=roi}, "
                        + "boolValue=Property{name='boolValue', value=true}, "
                        + "doubleValue=Property{name='doubleValue', value=3.14}, "
                        + "age=Property{name='age', value=32}}}",
                expectedNode.toString());
        Assertions.assertEquals(4, expectedNode.getNumberOfProperties());

        Edge expectedEdge = new Edge();
        expectedEdge.setId(0);
        expectedEdge.setSource(0);
        expectedEdge.setDestination(1);
        expectedEdge.setRelationshipType("knows");
        expectedEdge.addProperty("place", place);
        expectedEdge.addProperty("since", since);
        expectedEdge.addProperty(doubleProperty);
        expectedEdge.addProperty(falseBooleanProperty);
        Assertions.assertEquals("Edge{relationshipType='knows', source=0, destination=1, id=0, "
                + "propertyMap={boolValue=Property{name='boolValue', value=false}, "
                + "place=Property{name='place', value=TLV}, "
                + "doubleValue=Property{name='doubleValue', value=3.14}, "
                + "since=Property{name='since', value=2000}}}", expectedEdge.toString());
        Assertions.assertEquals(4, expectedEdge.getNumberOfProperties());

        Map<String, Object> params = new HashMap<>();
        params.put("name", name);
        params.put("age", age);
        params.put("boolValue", boolValue);
        params.put("doubleValue", doubleValue);

        Assertions.assertNotNull(client.query(
                "CREATE (:person{name:$name,age:$age, doubleValue:$doubleValue, boolValue:$boolValue})", params));
        Assertions.assertNotNull(client.query("CREATE (:person{name:'amit',age:30})"));
        Assertions.assertNotNull(
                client.query("MATCH (a:person), (b:person) WHERE (a.name = 'roi' AND b.name='amit')  " +
                        "CREATE (a)-[:knows{place:'TLV', since:2000,doubleValue:3.14, boolValue:false}]->(b)"));

        ResultSet resultSet = client.query("MATCH (a:person)-[r:knows]->(b:person) RETURN a,r, " +
                "a.name, a.age, a.doubleValue, a.boolValue, " +
                "r.place, r.since, r.doubleValue, r.boolValue");
        Assertions.assertNotNull(resultSet);

        Assertions.assertEquals(0, resultSet.getStatistics().nodesCreated());
        Assertions.assertEquals(0, resultSet.getStatistics().nodesDeleted());
        Assertions.assertEquals(0, resultSet.getStatistics().labelsAdded());
        Assertions.assertEquals(0, resultSet.getStatistics().propertiesSet());
        Assertions.assertEquals(0, resultSet.getStatistics().relationshipsCreated());
        Assertions.assertEquals(0, resultSet.getStatistics().relationshipsDeleted());
        Assertions.assertNotNull(resultSet.getStatistics().getStringValue(Label.QUERY_INTERNAL_EXECUTION_TIME));

        Assertions.assertEquals(1, resultSet.size());

        Iterator<Record> iterator = resultSet.iterator();
        Assertions.assertTrue(iterator.hasNext());
        Record record = iterator.next();
        Assertions.assertFalse(iterator.hasNext());

        Node node = record.getValue(0);
        Assertions.assertNotNull(node);

        Assertions.assertEquals(expectedNode, node);

        node = record.getValue("a");
        Assertions.assertEquals(expectedNode, node);

        Edge edge = record.getValue(1);
        Assertions.assertNotNull(edge);
        Assertions.assertEquals(expectedEdge, edge);

        edge = record.getValue("r");
        Assertions.assertEquals(expectedEdge, edge);

        Assertions.assertEquals(Arrays.asList("a", "r", "a.name", "a.age", "a.doubleValue", "a.boolValue",
                "r.place", "r.since", "r.doubleValue", "r.boolValue"), record.keys());

        Assertions.assertEquals(Arrays.asList(expectedNode, expectedEdge,
                name, (long) age, doubleValue, true,
                place, (long) since, doubleValue, false),
                record.values());

        Node a = record.getValue("a");
        for (String propertyName : expectedNode.getEntityPropertyNames()) {
            Assertions.assertEquals(expectedNode.getProperty(propertyName), a.getProperty(propertyName));
        }

        Assertions.assertEquals("roi", record.getString(2));
        Assertions.assertEquals("32", record.getString(3));
        Assertions.assertEquals(32L, ((Long) record.getValue(3)).longValue());
        Assertions.assertEquals(32L, ((Long) record.getValue("a.age")).longValue());
        Assertions.assertEquals("roi", record.getString("a.name"));
        Assertions.assertEquals("32", record.getString("a.age"));

    }

    @Test
    public void testMultiThread() {

        Assertions.assertNotNull(
                client.query("CREATE (:person {name:'roi', age:32})-[:knows]->(:person {name:'amit',age:30}) "));

        List<ResultSet> resultSets = IntStream.range(0, 16).parallel()
                .mapToObj(i -> client.query("MATCH (a:person)-[r:knows]->(b:person) RETURN a,r, a.age"))
                .collect(Collectors.toList());

        Property<String> nameProperty = new Property<>("name", "roi");
        Property<Integer> ageProperty = new Property<>("age", 32);
        Property<String> lastNameProperty = new Property<>("lastName", "a");

        Node expectedNode = new Node();
        expectedNode.setId(0);
        expectedNode.addLabel("person");
        expectedNode.addProperty(nameProperty);
        expectedNode.addProperty(ageProperty);

        Edge expectedEdge = new Edge();
        expectedEdge.setId(0);
        expectedEdge.setSource(0);
        expectedEdge.setDestination(1);
        expectedEdge.setRelationshipType("knows");

        for (ResultSet resultSet : resultSets) {
            Assertions.assertNotNull(resultSet.getHeader());
            Header header = resultSet.getHeader();
            List<String> schemaNames = header.getSchemaNames();
            Assertions.assertNotNull(schemaNames);
            Assertions.assertEquals(3, schemaNames.size());
            Assertions.assertEquals("a", schemaNames.get(0));
            Assertions.assertEquals("r", schemaNames.get(1));
            Assertions.assertEquals("a.age", schemaNames.get(2));
            Assertions.assertEquals(1, resultSet.size());

            Iterator<Record> iterator = resultSet.iterator();
            Assertions.assertTrue(iterator.hasNext());
            Record record = iterator.next();
            Assertions.assertFalse(iterator.hasNext());
            Assertions.assertEquals(Arrays.asList("a", "r", "a.age"), record.keys());
            Assertions.assertEquals(Arrays.asList(expectedNode, expectedEdge, 32L), record.values());
        }

        // test for update in local cache
        expectedNode.removeProperty("name");
        expectedNode.removeProperty("age");
        expectedNode.addProperty(lastNameProperty);
        expectedNode.removeLabel("person");
        expectedNode.addLabel("worker");
        expectedNode.setId(2);

        expectedEdge.setRelationshipType("worksWith");
        expectedEdge.setSource(2);
        expectedEdge.setDestination(3);
        expectedEdge.setId(1);

        Assertions.assertNotNull(client.query("CREATE (:worker{lastName:'a'})"));
        Assertions.assertNotNull(client.query("CREATE (:worker{lastName:'b'})"));
        Assertions.assertNotNull(client.query(
                "MATCH (a:worker), (b:worker) WHERE (a.lastName = 'a' AND b.lastName='b')  CREATE (a)-[:worksWith]->(b)"));

        resultSets = IntStream.range(0, 16).parallel()
                .mapToObj(i -> client.query("MATCH (a:worker)-[r:worksWith]->(b:worker) RETURN a,r"))
                .collect(Collectors.toList());

        for (ResultSet resultSet : resultSets) {
            Assertions.assertNotNull(resultSet.getHeader());
            Header header = resultSet.getHeader();
            List<String> schemaNames = header.getSchemaNames();
            Assertions.assertNotNull(schemaNames);
            Assertions.assertEquals(2, schemaNames.size());
            Assertions.assertEquals("a", schemaNames.get(0));
            Assertions.assertEquals("r", schemaNames.get(1));
            Assertions.assertEquals(1, resultSet.size());

            Iterator<Record> iterator = resultSet.iterator();
            Assertions.assertTrue(iterator.hasNext());
            Record record = iterator.next();
            Assertions.assertFalse(iterator.hasNext());
            Assertions.assertEquals(Arrays.asList("a", "r"), record.keys());
            Assertions.assertEquals(Arrays.asList(expectedNode, expectedEdge), record.values());
        }
    }

    @Test
    public void testAdditionToProcedures() {

        Assertions.assertNotNull(client.query("CREATE (:person{name:'roi',age:32})"));
        Assertions.assertNotNull(client.query("CREATE (:person{name:'amit',age:30})"));
        Assertions.assertNotNull(client.query(
                "MATCH (a:person), (b:person) WHERE (a.name = 'roi' AND b.name='amit')  CREATE (a)-[:knows]->(b)"));

        // expected objects init
        Property<String> nameProperty = new Property<>("name", "roi");
        Property<Integer> ageProperty = new Property<>("age", 32);
        Property<String> lastNameProperty = new Property<>("lastName", "a");

        Node expectedNode = new Node();
        expectedNode.setId(0);
        expectedNode.addLabel("person");
        expectedNode.addProperty(nameProperty);
        expectedNode.addProperty(ageProperty);

        Edge expectedEdge = new Edge();
        expectedEdge.setId(0);
        expectedEdge.setSource(0);
        expectedEdge.setDestination(1);
        expectedEdge.setRelationshipType("knows");

        ResultSet resultSet = client.query("MATCH (a:person)-[r:knows]->(b:person) RETURN a,r");
        Assertions.assertNotNull(resultSet.getHeader());
        Header header = resultSet.getHeader();
        List<String> schemaNames = header.getSchemaNames();
        Assertions.assertNotNull(schemaNames);
        Assertions.assertEquals(2, schemaNames.size());
        Assertions.assertEquals("a", schemaNames.get(0));
        Assertions.assertEquals("r", schemaNames.get(1));
        Assertions.assertEquals(1, resultSet.size());

        Iterator<Record> iterator = resultSet.iterator();
        Assertions.assertTrue(iterator.hasNext());
        Record record = iterator.next();
        Assertions.assertFalse(iterator.hasNext());
        Assertions.assertEquals(Arrays.asList("a", "r"), record.keys());
        Assertions.assertEquals(Arrays.asList(expectedNode, expectedEdge), record.values());

        // test for local cache updates

        expectedNode.removeProperty("name");
        expectedNode.removeProperty("age");
        expectedNode.addProperty(lastNameProperty);
        expectedNode.removeLabel("person");
        expectedNode.addLabel("worker");
        expectedNode.setId(2);
        expectedEdge.setRelationshipType("worksWith");
        expectedEdge.setSource(2);
        expectedEdge.setDestination(3);
        expectedEdge.setId(1);
        Assertions.assertNotNull(client.query("CREATE (:worker{lastName:'a'})"));
        Assertions.assertNotNull(client.query("CREATE (:worker{lastName:'b'})"));
        Assertions.assertNotNull(client.query(
                "MATCH (a:worker), (b:worker) WHERE (a.lastName = 'a' AND b.lastName='b')  CREATE (a)-[:worksWith]->(b)"));
        resultSet = client.query("MATCH (a:worker)-[r:worksWith]->(b:worker) RETURN a,r");
        Assertions.assertNotNull(resultSet.getHeader());
        header = resultSet.getHeader();
        schemaNames = header.getSchemaNames();
        Assertions.assertNotNull(schemaNames);
        Assertions.assertEquals(2, schemaNames.size());
        Assertions.assertEquals("a", schemaNames.get(0));
        Assertions.assertEquals("r", schemaNames.get(1));
        Assertions.assertEquals(1, resultSet.size());

        iterator = resultSet.iterator();
        Assertions.assertTrue(iterator.hasNext());
        record = iterator.next();
        Assertions.assertFalse(iterator.hasNext());
        Assertions.assertEquals(Arrays.asList("a", "r"), record.keys());
        Assertions.assertEquals(Arrays.asList(expectedNode, expectedEdge), record.values());

    }

    @Test
    public void testEscapedQuery() {
        Map<String, Object> params1 = new HashMap<String, Object>();
        params1.put("s1", "S\"'");
        params1.put("s2", "S'\"");
        Assertions.assertNotNull(client.query("CREATE (:escaped{s1:$s1,s2:$s2})", params1));

        Map<String, Object> params2 = new HashMap<String, Object>();
        params2.put("s1", "S\"'");
        params2.put("s2", "S'\"");
        Assertions.assertNotNull(client.query("MATCH (n) where n.s1=$s1 and n.s2=$s2 RETURN n", params2));

        Assertions.assertNotNull(client.query("MATCH (n) where n.s1='S\"' RETURN n"));

    }

    @Test
    public void testContextedAPI() {

        String name = "roi";
        int age = 32;
        double doubleValue = 3.14;
        boolean boolValue = true;

        String place = "TLV";
        int since = 2000;

        Property<String> nameProperty = new Property<>("name", name);
        Property<Integer> ageProperty = new Property<>("age", age);
        Property<Double> doubleProperty = new Property<>("doubleValue", doubleValue);
        Property<Boolean> trueBooleanProperty = new Property<>("boolValue", true);
        Property<Boolean> falseBooleanProperty = new Property<>("boolValue", false);

        Property<String> placeProperty = new Property<>("place", place);
        Property<Integer> sinceProperty = new Property<>("since", since);

        Node expectedNode = new Node();
        expectedNode.setId(0);
        expectedNode.addLabel("person");
        expectedNode.addProperty(nameProperty);
        expectedNode.addProperty(ageProperty);
        expectedNode.addProperty(doubleProperty);
        expectedNode.addProperty(trueBooleanProperty);

        Edge expectedEdge = new Edge();
        expectedEdge.setId(0);
        expectedEdge.setSource(0);
        expectedEdge.setDestination(1);
        expectedEdge.setRelationshipType("knows");
        expectedEdge.addProperty(placeProperty);
        expectedEdge.addProperty(sinceProperty);
        expectedEdge.addProperty(doubleProperty);
        expectedEdge.addProperty(falseBooleanProperty);

        Map<String, Object> params = new HashMap<>();
        params.put("name", name);
        params.put("age", age);
        params.put("boolValue", boolValue);
        params.put("doubleValue", doubleValue);
        try (GraphContext c = client.getContext()) {
            Assertions.assertNotNull(c.query(
                    "CREATE (:person{name:$name, age:$age, doubleValue:$doubleValue, boolValue:$boolValue})", params));
            Assertions.assertNotNull(c.query("CREATE (:person{name:'amit',age:30})"));
            Assertions.assertNotNull(
                    c.query("MATCH (a:person), (b:person) WHERE (a.name = 'roi' AND b.name='amit')  " +
                            "CREATE (a)-[:knows{place:'TLV', since:2000,doubleValue:3.14, boolValue:false}]->(b)"));

            ResultSet resultSet = c.query("MATCH (a:person)-[r:knows]->(b:person) RETURN a,r, " +
                    "a.name, a.age, a.doubleValue, a.boolValue, " +
                    "r.place, r.since, r.doubleValue, r.boolValue");
            Assertions.assertNotNull(resultSet);

            Assertions.assertEquals(0, resultSet.getStatistics().nodesCreated());
            Assertions.assertEquals(0, resultSet.getStatistics().nodesDeleted());
            Assertions.assertEquals(0, resultSet.getStatistics().labelsAdded());
            Assertions.assertEquals(0, resultSet.getStatistics().propertiesSet());
            Assertions.assertEquals(0, resultSet.getStatistics().relationshipsCreated());
            Assertions.assertEquals(0, resultSet.getStatistics().relationshipsDeleted());
            Assertions.assertNotNull(resultSet.getStatistics().getStringValue(Label.QUERY_INTERNAL_EXECUTION_TIME));

            Assertions.assertEquals(1, resultSet.size());

            Iterator<Record> iterator = resultSet.iterator();
            Assertions.assertTrue(iterator.hasNext());
            Record record = iterator.next();
            Assertions.assertFalse(iterator.hasNext());

            Node node = record.getValue(0);
            Assertions.assertNotNull(node);

            Assertions.assertEquals(expectedNode, node);

            node = record.getValue("a");
            Assertions.assertEquals(expectedNode, node);

            Edge edge = record.getValue(1);
            Assertions.assertNotNull(edge);
            Assertions.assertEquals(expectedEdge, edge);

            edge = record.getValue("r");
            Assertions.assertEquals(expectedEdge, edge);

            Assertions.assertEquals(Arrays.asList("a", "r", "a.name", "a.age", "a.doubleValue", "a.boolValue",
                    "r.place", "r.since", "r.doubleValue", "r.boolValue"), record.keys());

            Assertions.assertEquals(Arrays.asList(expectedNode, expectedEdge,
                    name, (long) age, doubleValue, true,
                    place, (long) since, doubleValue, false),
                    record.values());

            Node a = record.getValue("a");
            for (String propertyName : expectedNode.getEntityPropertyNames()) {
                Assertions.assertEquals(expectedNode.getProperty(propertyName), a.getProperty(propertyName));
            }

            Assertions.assertEquals("roi", record.getString(2));
            Assertions.assertEquals("32", record.getString(3));
            Assertions.assertEquals(32L, ((Long) (record.getValue(3))).longValue());
            Assertions.assertEquals(32L, ((Long) record.getValue("a.age")).longValue());
            Assertions.assertEquals("roi", record.getString("a.name"));
            Assertions.assertEquals("32", record.getString("a.age"));

            // Test profile functionality in contexted API
            ResultSet profileResult = c.profile("MATCH (a:person) WHERE (a.name = 'roi') RETURN a.age");
            Assertions.assertNotNull(profileResult);
        }
    }

    @Test
    public void testArraySupport() {

        Node expectedANode = new Node();
        expectedANode.setId(0);
        expectedANode.addLabel("person");
        Property<String> aNameProperty = new Property<>("name", "a");
        Property<Integer> aAgeProperty = new Property<>("age", 32);
        Property<List<Long>> aListProperty = new Property<>("array", Arrays.asList(0L, 1L, 2L));
        expectedANode.addProperty(aNameProperty);
        expectedANode.addProperty(aAgeProperty);
        expectedANode.addProperty(aListProperty);

        Node expectedBNode = new Node();
        expectedBNode.setId(1);
        expectedBNode.addLabel("person");
        Property<String> bNameProperty = new Property<>("name", "b");
        Property<Integer> bAgeProperty = new Property<>("age", 30);
        Property<List<Long>> bListProperty = new Property<>("array", Arrays.asList(3L, 4L, 5L));
        expectedBNode.addProperty(bNameProperty);
        expectedBNode.addProperty(bAgeProperty);
        expectedBNode.addProperty(bListProperty);

        Assertions.assertNotNull(client.query("CREATE (:person{name:'a',age:32,array:[0,1,2]})"));
        Assertions.assertNotNull(client.query("CREATE (:person{name:'b',age:30,array:[3,4,5]})"));

        // test array

        ResultSet resultSet = client.query("WITH [0,1,2] as x return x");

        // check header
        Assertions.assertNotNull(resultSet.getHeader());
        Header header = resultSet.getHeader();

        List<String> schemaNames = header.getSchemaNames();
        Assertions.assertNotNull(schemaNames);
        Assertions.assertEquals(1, schemaNames.size());
        Assertions.assertEquals("x", schemaNames.get(0));

        // check record
        Assertions.assertEquals(1, resultSet.size());

        Iterator<Record> iterator = resultSet.iterator();
        Assertions.assertTrue(iterator.hasNext());
        Record record = iterator.next();
        Assertions.assertFalse(iterator.hasNext());
        Assertions.assertEquals(Arrays.asList("x"), record.keys());

        List<Long> x = record.getValue("x");
        Assertions.assertEquals(Arrays.asList(0L, 1L, 2L), x);

        // test collect
        resultSet = client.query("MATCH(n) return collect(n) as x");

        Assertions.assertNotNull(resultSet.getHeader());
        header = resultSet.getHeader();

        schemaNames = header.getSchemaNames();
        Assertions.assertNotNull(schemaNames);
        Assertions.assertEquals(1, schemaNames.size());
        Assertions.assertEquals("x", schemaNames.get(0));

        // check record
        Assertions.assertEquals(1, resultSet.size());

        iterator = resultSet.iterator();
        Assertions.assertTrue(iterator.hasNext());
        record = iterator.next();
        Assertions.assertFalse(iterator.hasNext());
        Assertions.assertEquals(Arrays.asList("x"), record.keys());
        x = record.getValue("x");
        Assertions.assertEquals(Arrays.asList(expectedANode, expectedBNode), x);

        // test unwind
        resultSet = client.query("unwind([0,1,2]) as x return x");

        Assertions.assertNotNull(resultSet.getHeader());
        header = resultSet.getHeader();

        schemaNames = header.getSchemaNames();
        Assertions.assertNotNull(schemaNames);
        Assertions.assertEquals(1, schemaNames.size());
        Assertions.assertEquals("x", schemaNames.get(0));

        // check record
        Assertions.assertEquals(3, resultSet.size());

        iterator = resultSet.iterator();
        for (long i = 0; i < 3; i++) {
            Assertions.assertTrue(iterator.hasNext());
            record = iterator.next();
            Assertions.assertEquals(Arrays.asList("x"), record.keys());
            Assertions.assertEquals(i, (long) record.getValue("x"));

        }

    }

    @Test
    public void testPath() {
        List<Node> nodes = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            Node node = new Node();
            node.setId(i);
            node.addLabel("L1");
            nodes.add(node);
        }

        List<Edge> edges = new ArrayList<>(2);
        for (int i = 0; i < 2; i++) {
            Edge edge = new Edge();
            edge.setId(i);
            edge.setRelationshipType("R1");
            edge.setSource(i);
            edge.setDestination(i + 1);
            edges.add(edge);
        }

        Set<Path> expectedPaths = new HashSet<>();

        Path path01 = new PathBuilder(2).append(nodes.get(0)).append(edges.get(0)).append(nodes.get(1)).build();
        Path path12 = new PathBuilder(2).append(nodes.get(1)).append(edges.get(1)).append(nodes.get(2)).build();
        Path path02 = new PathBuilder(3).append(nodes.get(0)).append(edges.get(0)).append(nodes.get(1))
                .append(edges.get(1)).append(nodes.get(2)).build();

        expectedPaths.add(path01);
        expectedPaths.add(path12);
        expectedPaths.add(path02);

        client.query("CREATE (:L1)-[:R1]->(:L1)-[:R1]->(:L1)");

        ResultSet resultSet = client.query("MATCH p = (:L1)-[:R1*]->(:L1) RETURN p");

        Assertions.assertEquals(expectedPaths.size(), resultSet.size());
        for (Record record : resultSet) {
            Path p = record.getValue("p");
            Assertions.assertTrue(expectedPaths.contains(p));
            expectedPaths.remove(p);
        }

    }

    @Test
    public void testNullGraphEntities() {
        // Create two nodes connected by a single outgoing edge.
        Assertions.assertNotNull(client.query("CREATE (:L)-[:E]->(:L2)"));
        // Test a query that produces 1 record with 3 null values.
        ResultSet resultSet = client.query("OPTIONAL MATCH (a:NONEXISTENT)-[e]->(b) RETURN a, e, b");
        Assertions.assertEquals(1, resultSet.size());

        Iterator<Record> iterator = resultSet.iterator();
        Assertions.assertTrue(iterator.hasNext());
        Record record = iterator.next();
        Assertions.assertFalse(iterator.hasNext());
        Assertions.assertEquals(Arrays.asList(null, null, null), record.values());

        // Test a query that produces 2 records, with 2 null values in the second.
        resultSet = client.query("MATCH (a) OPTIONAL MATCH (a)-[e]->(b) RETURN a, e, b ORDER BY ID(a)");
        Assertions.assertEquals(2, resultSet.size());

        iterator = resultSet.iterator();
        record = iterator.next();
        Assertions.assertEquals(3, record.size());

        Assertions.assertNotNull(record.getValue(0));
        Assertions.assertNotNull(record.getValue(1));
        Assertions.assertNotNull(record.getValue(2));

        record = iterator.next();
        Assertions.assertEquals(3, record.size());

        Assertions.assertNotNull(record.getValue(0));
        Assertions.assertNull(record.getValue(1));
        Assertions.assertNull(record.getValue(2));

        // Test a query that produces 2 records, the first containing a path and the
        // second containing a null value.
        resultSet = client.query("MATCH (a) OPTIONAL MATCH p = (a)-[e]->(b) RETURN p");
        Assertions.assertEquals(2, resultSet.size());

        iterator = resultSet.iterator();
        record = iterator.next();
        Assertions.assertEquals(1, record.size());
        Assertions.assertNotNull(record.getValue(0));

        record = iterator.next();
        Assertions.assertEquals(1, record.size());
        Assertions.assertNull(record.getValue(0));
    }

    @Test
    public void test64bitnumber() {
        long value = 1 << 40;
        Map<String, Object> params = new HashMap<>();
        params.put("val", value);
        ResultSet resultSet = client.query("CREATE (n {val:$val}) RETURN n.val", params);
        Assertions.assertEquals(1, resultSet.size());
        Record r = resultSet.iterator().next();
        Assertions.assertEquals(Long.valueOf(value), r.getValue(0));
    }

    @Test
    public void testVecf32() {
        ResultSet resultSet = client.query("RETURN vecf32([2.1, -0.82, 1.3, 4.5]) AS vector");
        Assertions.assertEquals(1, resultSet.size());
        Record r = resultSet.iterator().next();
        List<Object> vector = r.getValue(0);
        Assertions.assertEquals(4, vector.size());
        Object res = vector.get(0);

        // The result can be either Double or Float depending on the server version
        if ( res instanceof Double) {
            List<Double> v = r.getValue(0);
            Assertions.assertEquals(2.1, v.get(0), 0.01);
            Assertions.assertEquals(-0.82, v.get(1), 0.01);
            Assertions.assertEquals(1.3, v.get(2), 0.01);
            Assertions.assertEquals(4.5, v.get(3), 0.01);
        } else {
            List<Float> v = r.getValue(0);
            Assertions.assertEquals(2.1f, v.get(0), 0.01);
            Assertions.assertEquals(-0.82f, v.get(1), 0.01);
            Assertions.assertEquals(1.3f, v.get(2), 0.01);
            Assertions.assertEquals(4.5f, v.get(3), 0.01);
        }
    }

    @Test
    public void testCachedExecution() {
        client.query("CREATE (:N {val:1}), (:N {val:2})");

        // First time should not be loaded from execution cache
        Map<String, Object> params = new HashMap<>();
        params.put("val", 1L);
        ResultSet resultSet = client.query("MATCH (n:N {val:$val}) RETURN n.val", params);
        Assertions.assertEquals(1, resultSet.size());
        Record r = resultSet.iterator().next();
        Assertions.assertEquals(params.get("val"), r.getValue(0));
        Assertions.assertFalse(resultSet.getStatistics().cachedExecution());

        // Run in loop many times to make sure the query will be loaded
        // from cache at least once
        for (int i = 0; i < 64; i++) {
            resultSet = client.query("MATCH (n:N {val:$val}) RETURN n.val", params);
        }
        Assertions.assertEquals(1, resultSet.size());
        r = resultSet.iterator().next();
        Assertions.assertEquals(params.get("val"), r.getValue(0));
        Assertions.assertTrue(resultSet.getStatistics().cachedExecution());
    }

    @Test
    public void testMapDataType() {
        Map<String, Object> expected = new HashMap<>();
        expected.put("a", (long) 1);
        expected.put("b", "str");
        expected.put("c", null);
        List<Object> d = new ArrayList<>();
        d.add((long) 1);
        d.add((long) 2);
        d.add((long) 3);
        expected.put("d", d);
        expected.put("e", true);
        Map<String, Object> f = new HashMap<>();
        f.put("x", (long) 1);
        f.put("y", (long) 2);
        expected.put("f", f);
        ResultSet res = client.query("RETURN {a:1, b:'str', c:NULL, d:[1,2,3], e:True, f:{x:1, y:2}}");
        Assertions.assertEquals(1, res.size());
        Record r = res.iterator().next();
        Map<String, Object> actual = r.getValue(0);
        Assertions.assertEquals(expected, actual);
    }

    @Test
    public void testGeoPointLatLon() {
        ResultSet rs = client.query("CREATE (:restaurant"
                + " {location: point({latitude:30.27822306, longitude:-97.75134723})})");
        Assertions.assertEquals(1, rs.getStatistics().nodesCreated());
        Assertions.assertEquals(1, rs.getStatistics().propertiesSet());

        assertTestGeoPoint();
    }

    @Test
    public void testGeoPointLonLat() {
        ResultSet rs = client.query("CREATE (:restaurant"
                + " {location: point({longitude:-97.75134723, latitude:30.27822306})})");
        Assertions.assertEquals(1, rs.getStatistics().nodesCreated());
        Assertions.assertEquals(1, rs.getStatistics().propertiesSet());

        assertTestGeoPoint();
    }

    private void assertTestGeoPoint() {
        ResultSet results = client.query("MATCH (restaurant) RETURN restaurant");
        Assertions.assertEquals(1, results.size());
        Record record = results.iterator().next();
        Assertions.assertEquals(1, record.size());
        Assertions.assertEquals(Collections.singletonList("restaurant"), record.keys());
        Node node = record.getValue(0);
        Property<?> property = node.getProperty("location");
        Point result = (Point) property.getValue();

        Point point = new Point(30.27822306, -97.75134723);
        Assertions.assertEquals(point, result);
        Assertions.assertEquals(30.27822306, result.getLatitude(), 0.01);
        Assertions.assertEquals(-97.75134723, result.getLongitude(), 0.01);
        Assertions.assertEquals("Point{latitude=30.2782230377197, longitude=-97.751350402832}", result.toString());
        Assertions.assertEquals(-132320535, result.hashCode());
    }

    @Test
    public void timeoutArgument() {
        ResultSet rs = client.query("UNWIND range(0,100) AS x WITH x AS x WHERE x = 100 RETURN x", 1L);
        Assertions.assertEquals(1, rs.size());
        Record r = rs.iterator().next();
        Assertions.assertEquals(Long.valueOf(100), r.getValue(0));
    }

    @Test
    public void testCachedExecutionReadOnly() {
        client.query("CREATE (:N {val:1}), (:N {val:2})");

        // First time should not be loaded from execution cache
        Map<String, Object> params = new HashMap<>();
        params.put("val", 1L);
        ResultSet resultSet = client.readOnlyQuery("MATCH (n:N {val:$val}) RETURN n.val", params);
        Assertions.assertEquals(1, resultSet.size());
        Record r = resultSet.iterator().next();
        Assertions.assertEquals(params.get("val"), r.getValue(0));
        Assertions.assertFalse(resultSet.getStatistics().cachedExecution());

        // Run in loop many times to make sure the query will be loaded
        // from cache at least once
        for (int i = 0; i < 64; i++) {
            resultSet = client.readOnlyQuery("MATCH (n:N {val:$val}) RETURN n.val", params);
        }
        Assertions.assertEquals(1, resultSet.size());
        r = resultSet.iterator().next();
        Assertions.assertEquals(params.get("val"), r.getValue(0));
        Assertions.assertTrue(resultSet.getStatistics().cachedExecution());
    }

    @Test
    public void testSimpleReadOnly() {
        client.query("CREATE (:person{name:'filipe',age:30})");
        ResultSet rsRo = client.readOnlyQuery("MATCH (a:person) WHERE (a.name = 'filipe') RETURN a.age");
        Assertions.assertEquals(1, rsRo.size());
        Record r = rsRo.iterator().next();
        Assertions.assertEquals(Long.valueOf(30), r.getValue(0));
    }

    @Test
    public void testSimpleReadOnlyWithTimeOut() {
        client.query("CREATE (:person{name:'filipe',age:30})");
        try {
            client.readOnlyQuery(
                    "WITH 1000000 as n RETURN reduce(f = 1, x IN range(1, n) | f * x) AS result",
                    1L);

            fail("Expected Timeout Exception was not thrown.");
        } catch (GraphException e) {
            Assertions.assertTrue(e.getMessage().contains("Query timed out"));
        }
    }

    @Test
    public void testProfile() {
        // Create sample data for profiling
        client.query("CREATE (:person{name:'alice',age:30})");
        
        // Test basic profile
        ResultSet profileResult = client.profile("MATCH (a:person) WHERE (a.name = 'alice') RETURN a.age");
        Assertions.assertNotNull(profileResult);
        
        // Test profile with parameters
        Map<String, Object> params = new HashMap<>();
        params.put("name", "alice");
        ResultSet profileResultWithParams = client.profile("MATCH (a:person) WHERE (a.name = $name) RETURN a.age", params);
        Assertions.assertNotNull(profileResultWithParams);
    }

    @Test
    public void testGraphCopy() {
        // Create sample data
        client.query("CREATE (:person{name:'roi',age:32})-[:knows]->(:person{name:'amit',age:30})");
        ResultSet originalResultSet = client.query("MATCH (p:person)-[rel:knows]->(p2:person) RETURN p,rel,p2");
        Iterator<Record> originalResultSetIterator = originalResultSet.iterator();

        // Copy the graph
        client.copyGraph("social-copied");

        GraphContextGenerator client2 = FalkorDB.driver().graph("social-copied");
        try {
            // Compare graph contents
            ResultSet copiedResultSet = client2.query("MATCH (p:person)-[rel:knows]->(p2:person) RETURN p,rel,p2");
            Iterator<Record> copiedResultSetIterator = copiedResultSet.iterator();
            while (originalResultSetIterator.hasNext()) {
                Assertions.assertTrue(copiedResultSetIterator.hasNext());
                Assertions.assertEquals(originalResultSetIterator.next(), copiedResultSetIterator.next());
            }
        } finally {
            // Cleanup
            client2.deleteGraph();
            client2.close();
        }
    }

    @Test
    public void testGraphCopyContextedAPI() {
        Iterator<Record> originalResultSetIterator;
        try (GraphContext c = client.getContext()) {
            // Create sample data
            c.query("CREATE (:person{name:'roi',age:32})-[:knows]->(:person{name:'amit',age:30})");
            ResultSet originalResultSet = c.query("MATCH (p:person)-[rel:knows]->(p2:person) RETURN p,rel,p2");
            originalResultSetIterator = originalResultSet.iterator();

            // Copy the graph
            c.copyGraph("social-copied");
        }

        GraphContextGenerator client2 = FalkorDB.driver().graph("social-copied");
        try {
            // Compare graph contents
            ResultSet copiedResultSet = client2.query("MATCH (p:person)-[rel:knows]->(p2:person) RETURN p,rel,p2");
            Iterator<Record> copiedResultSetIterator = copiedResultSet.iterator();
            while (originalResultSetIterator.hasNext()) {
                Assertions.assertTrue(copiedResultSetIterator.hasNext());
                Assertions.assertEquals(originalResultSetIterator.next(), copiedResultSetIterator.next());
            }
        } finally {
            // Cleanup
            client2.deleteGraph();
            client2.close();
        }
    }
}
