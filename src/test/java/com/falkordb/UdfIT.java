package com.falkordb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.util.SafeEncoder;

public class UdfIT {

    private Driver driver;

    @BeforeEach
    public void setUp() {
        driver = TestServer.driver();
        // Clean up any existing UDF libraries before each test
        try {
            driver.udfFlush();
        } catch (Exception e) {
            // Ignore errors if no libraries are loaded
        }
    }

    @AfterEach
    public void tearDown() {
        // Clean up after tests
        try {
            driver.udfFlush();
        } catch (Exception e) {
            // Ignore errors
        }
        try {
            driver.close();
        } catch (Exception e) {
            // Ignore errors
        }
    }

    @Test
    public void testUdfLoad() {
        String libraryName = "TestLib";
        String script = "function testFunc() { return 'hello'; } falkor.register('testFunc', testFunc);";

        boolean result = driver.udfLoad(libraryName, script, false);
        Assertions.assertTrue(result, "UDF library should be loaded successfully");

        // Verify the function can be called
        Graph graph = driver.graph("udf_test_graph");
        try {
            ResultSet rs = graph.query("RETURN TestLib.testFunc() AS result");
            Assertions.assertEquals(1, rs.size(), "Query should return exactly one result");

            for (Record record : rs) {
                String value = record.getString("result");
                Assertions.assertEquals("hello", value, "UDF should return 'hello'");
            }
        } finally {
            graph.deleteGraph();
            graph.close();
        }
    }

    @Test
    public void testUdfLoadWithReplace() {
        String libraryName = "TestLibReplace";
        String script1 = "function testFunc1() { return 'hello'; } falkor.register('testFunc1', testFunc1);";
        String script2 = "function testFunc2() { return 'world'; } falkor.register('testFunc2', testFunc2);";

        // Load the library initially
        boolean result1 = driver.udfLoad(libraryName, script1, false);
        Assertions.assertTrue(result1, "First UDF library should be loaded successfully");

        // Verify the first function works
        Graph graph = driver.graph("udf_test_replace_graph");
        try {
            ResultSet rs1 = graph.query("RETURN TestLibReplace.testFunc1() AS result");
            Assertions.assertEquals(1, rs1.size(), "Query should return exactly one result");

            for (Record record : rs1) {
                String value = record.getString("result");
                Assertions.assertEquals("hello", value, "First UDF should return 'hello'");
            }

            // Replace the library
            boolean result2 = driver.udfLoad(libraryName, script2, true);
            Assertions.assertTrue(result2, "UDF library should be replaced successfully");

            // Verify the new function works
            ResultSet rs2 = graph.query("RETURN TestLibReplace.testFunc2() AS result");
            Assertions.assertEquals(1, rs2.size(), "Query should return exactly one result");

            for (Record record : rs2) {
                String value = record.getString("result");
                Assertions.assertEquals("world", value, "Replaced UDF should return 'world'");
            }
        } finally {
            graph.deleteGraph();
            graph.close();
        }
    }

    @Test
    public void testUdfList() {
        String libraryName = "TestLibList";
        String script = "function testFunc() { return 'hello'; } falkor.register('testFunc', testFunc);";

        // Load a UDF library
        driver.udfLoad(libraryName, script, false);

        // List all UDF libraries
        List<Object> libraries = driver.udfList();
        Assertions.assertNotNull(libraries, "UDF list should not be null");
        Assertions.assertTrue(libraryNames(libraries).contains(libraryName), "UDF list should name " + libraryName);
    }

    @Test
    public void testUdfListWithLibraryName() {
        String libraryName = "TestLibSpecific";
        String script = "function testFunc() { return 'hello'; } falkor.register('testFunc', testFunc);";

        // Load a UDF library, and another one the filter must leave out
        driver.udfLoad(libraryName, script, false);
        driver.udfLoad("TestLibOther", script, false);

        // List specific UDF library
        List<Object> libraries = driver.udfList(libraryName, false);
        Assertions.assertNotNull(libraries, "UDF list should not be null");
        Assertions.assertEquals(
                Collections.singletonList(libraryName),
                libraryNames(libraries),
                "UDF list filtered by name should list only that library");
    }

    @Test
    public void testUdfListWithCode() {
        String libraryName = "TestLibWithCode";
        String script = "function testFunc() { return 'hello'; } falkor.register('testFunc', testFunc);";

        // Load a UDF library
        driver.udfLoad(libraryName, script, false);

        // List UDF libraries with code
        List<Object> libraries = driver.udfList(null, true);
        Assertions.assertNotNull(libraries, "UDF list should not be null");
        Assertions.assertTrue(libraryNames(libraries).contains(libraryName), "UDF list should name " + libraryName);
        Assertions.assertTrue(strings(libraries).contains(script), "UDF list WITHCODE should include the code");
    }

    @Test
    public void testUdfFlush() {
        String libraryName = "TestLibFlush";
        String script = "function testFunc() { return 'hello'; } falkor.register('testFunc', testFunc);";

        // Load a UDF library
        driver.udfLoad(libraryName, script, false);

        // Flush all libraries
        boolean result = driver.udfFlush();
        Assertions.assertTrue(result, "UDF flush should succeed");

        // Verify libraries are flushed
        List<Object> libraries = driver.udfList();
        Assertions.assertNotNull(libraries, "UDF list should not be null");
        Assertions.assertEquals(
                Collections.emptyList(), libraryNames(libraries), "no UDF library should be left after a flush");
    }

    @Test
    public void testUdfDelete() {
        String libraryName = "TestLibDelete";
        String script = "function testFunc() { return 'hello'; } falkor.register('testFunc', testFunc);";

        // Load a UDF library
        driver.udfLoad(libraryName, script, false);
        Assertions.assertTrue(libraryNames(driver.udfList()).contains(libraryName), "the library should be loaded");

        // Delete the library
        boolean result = driver.udfDelete(libraryName);
        Assertions.assertTrue(result, "UDF library should be deleted successfully");
        Assertions.assertFalse(
                libraryNames(driver.udfList()).contains(libraryName), "the deleted library should no longer be listed");
    }

    @Test
    public void testUdfLoadMultipleLibraries() {
        String lib1Name = "TestLib1";
        String lib2Name = "TestLib2";
        String script1 = "function testFunc1() { return 'hello'; } falkor.register('testFunc1', testFunc1);";
        String script2 = "function testFunc2() { return 'world'; } falkor.register('testFunc2', testFunc2);";

        // Load multiple libraries
        boolean result1 = driver.udfLoad(lib1Name, script1, false);
        boolean result2 = driver.udfLoad(lib2Name, script2, false);

        Assertions.assertTrue(result1, "First UDF library should be loaded successfully");
        Assertions.assertTrue(result2, "Second UDF library should be loaded successfully");

        // List all libraries
        List<Object> libraries = driver.udfList();
        Assertions.assertNotNull(libraries, "UDF list should not be null");
        Assertions.assertTrue(
                libraryNames(libraries).containsAll(Arrays.asList(lib1Name, lib2Name)),
                "UDF list should name both libraries: " + libraryNames(libraries));
    }

    /**
     * The library names in a {@code GRAPH.UDF LIST} reply. Each library is a flat list of field/value pairs, one of
     * them {@code library_name}.
     */
    private static List<String> libraryNames(List<Object> libraries) {
        List<String> names = new ArrayList<>();
        for (Object library : libraries) {
            List<String> fields = strings(library);
            int index = fields.indexOf("library_name");
            Assertions.assertTrue(index >= 0 && index + 1 < fields.size(), "library without a name: " + fields);
            names.add(fields.get(index + 1));
        }
        return names;
    }

    /** Every scalar in a reply, depth first, with bulk strings decoded. */
    private static List<String> strings(Object reply) {
        List<String> strings = new ArrayList<>();
        if (reply instanceof List<?>) {
            for (Object element : (List<?>) reply) {
                strings.addAll(strings(element));
            }
        } else if (reply instanceof byte[]) {
            strings.add(SafeEncoder.encode((byte[]) reply));
        } else if (reply != null) {
            strings.add(reply.toString());
        }
        return strings;
    }
}
