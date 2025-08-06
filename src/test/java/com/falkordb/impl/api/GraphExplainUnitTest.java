package com.falkordb.impl.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.falkordb.ResultSet;
import com.falkordb.impl.Utils;

import redis.clients.jedis.util.SafeEncoder;

/**
 * Unit tests for Graph explain functionality to ensure comprehensive coverage
 * without requiring a live database connection.
 */
public class GraphExplainUnitTest {

    private TestableAbstractGraph testGraph;

    @BeforeEach
    public void setUp() {
        testGraph = new TestableAbstractGraph();
    }

    @Test
    public void testExplainWithoutParameters() {
        String query = "MATCH (n:Person) RETURN n";
        
        // Set up mock response
        List<Object> mockResponse = Arrays.asList(
            SafeEncoder.encode("Results"),
            SafeEncoder.encode("    Project"),
            SafeEncoder.encode("        Filter"),
            SafeEncoder.encode("            NodeByLabelScan")
        );
        testGraph.setMockExplainResponse(mockResponse);

        List<String> result = testGraph.explain(query);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(4, result.size());
        Assertions.assertEquals("Results", result.get(0));
        Assertions.assertEquals("    Project", result.get(1));
        Assertions.assertEquals("        Filter", result.get(2));
        Assertions.assertEquals("            NodeByLabelScan", result.get(3));

        // Verify that query was properly prepared
        String expectedPreparedQuery = Utils.prepareQuery(query, new HashMap<>());
        Assertions.assertEquals(expectedPreparedQuery, testGraph.getLastPreparedQuery());
    }

    @Test
    public void testExplainWithParameters() {
        String query = "MATCH (p:Person) WHERE p.name = $name RETURN p";
        Map<String, Object> params = new HashMap<>();
        params.put("name", "Alice");
        
        // Set up mock response
        List<Object> mockResponse = Arrays.asList(
            SafeEncoder.encode("Results"),
            SafeEncoder.encode("    Project"),
            SafeEncoder.encode("        Filter"),
            SafeEncoder.encode("            NodeByLabelScan")
        );
        testGraph.setMockExplainResponse(mockResponse);

        List<String> result = testGraph.explain(query, params);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(4, result.size());
        Assertions.assertEquals("Results", result.get(0));

        // Verify that query was properly prepared with parameters
        String expectedPreparedQuery = Utils.prepareQuery(query, params);
        Assertions.assertEquals(expectedPreparedQuery, testGraph.getLastPreparedQuery());
        Assertions.assertTrue(testGraph.getLastPreparedQuery().contains("name=\"Alice\""));
    }

    @Test
    public void testExplainResponseParsingWithByteArrays() {
        // Test response parsing logic similar to GraphImpl.sendExplain
        List<Object> mockResponse = Arrays.asList(
            SafeEncoder.encode("Results"),
            SafeEncoder.encode("    Project")
        );
        
        List<String> result = parseExplainResponse(mockResponse);
        
        Assertions.assertNotNull(result);
        Assertions.assertEquals(2, result.size());
        Assertions.assertEquals("Results", result.get(0));
        Assertions.assertEquals("    Project", result.get(1));
    }

    @Test
    public void testExplainResponseParsingWithMixedTypes() {
        // Test mixed byte arrays and strings
        List<Object> mockResponse = Arrays.asList(
            SafeEncoder.encode("Results"),
            "    Project",  // String instead of byte array
            SafeEncoder.encode("        Filter")
        );
        
        List<String> result = parseExplainResponse(mockResponse);
        
        Assertions.assertNotNull(result);
        Assertions.assertEquals(3, result.size());
        Assertions.assertEquals("Results", result.get(0));
        Assertions.assertEquals("    Project", result.get(1));
        Assertions.assertEquals("        Filter", result.get(2));
    }

    @Test
    public void testExplainResponseParsingWithEmptyList() {
        List<Object> mockResponse = new ArrayList<>();
        
        List<String> result = parseExplainResponse(mockResponse);
        
        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isEmpty());
    }

    @Test
    public void testExplainResponseParsingWithNonListResponse() {
        // Test fallback behavior when response is not a List
        byte[] singleResponse = SafeEncoder.encode("Single response");
        
        List<String> result = parseExplainResponseFallback(singleResponse);
        
        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals("Single response", result.get(0));
    }

    @Test
    public void testExplainWithComplexParameters() {
        String query = "MATCH (p:Person) WHERE p.age > $minAge AND p.city = $city RETURN p";
        Map<String, Object> params = new HashMap<>();
        params.put("minAge", 25);
        params.put("city", "New York");
        
        List<Object> mockResponse = Arrays.asList(
            SafeEncoder.encode("Results"),
            SafeEncoder.encode("    Project"),
            SafeEncoder.encode("        Filter"),
            SafeEncoder.encode("            NodeByLabelScan")
        );
        testGraph.setMockExplainResponse(mockResponse);

        List<String> result = testGraph.explain(query, params);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(4, result.size());
        
        String preparedQuery = testGraph.getLastPreparedQuery();
        Assertions.assertTrue(preparedQuery.contains("minAge=25"));
        Assertions.assertTrue(preparedQuery.contains("city=\"New York\""));
    }

    @Test
    public void testExplainWithSpecialCharacters() {
        String query = "MATCH (p:Person) WHERE p.name = $name RETURN p";
        Map<String, Object> params = new HashMap<>();
        params.put("name", "John \"The Rock\" Doe");
        
        List<Object> mockResponse = Arrays.asList(
            SafeEncoder.encode("Results")
        );
        testGraph.setMockExplainResponse(mockResponse);

        List<String> result = testGraph.explain(query, params);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.size());
        
        // Verify that special characters are properly escaped
        String preparedQuery = testGraph.getLastPreparedQuery();
        Assertions.assertTrue(preparedQuery.contains("name=\"John \\\"The Rock\\\" Doe\""));
    }

    /**
     * Helper method to test the response parsing logic similar to GraphImpl.sendExplain
     */
    private List<String> parseExplainResponse(List<Object> responseList) {
        List<String> result = new ArrayList<>(responseList.size());
        for (Object item : responseList) {
            if (item instanceof byte[]) {
                result.add(SafeEncoder.encode((byte[]) item));
            } else {
                result.add(item.toString());
            }
        }
        return result;
    }

    /**
     * Helper method to test the fallback response parsing logic
     */
    private List<String> parseExplainResponseFallback(byte[] response) {
        return Arrays.asList(SafeEncoder.encode(response));
    }

    /**
     * Testable implementation of AbstractGraph that doesn't require Redis connection
     */
    private static class TestableAbstractGraph extends AbstractGraph {
        private List<Object> mockExplainResponse;
        private String lastPreparedQuery;

        public void setMockExplainResponse(List<Object> response) {
            this.mockExplainResponse = response;
        }

        public String getLastPreparedQuery() {
            return lastPreparedQuery;
        }

        @Override
        protected ResultSet sendQuery(String preparedQuery) {
            throw new UnsupportedOperationException("Not implemented for unit test");
        }

        @Override
        protected ResultSet sendReadOnlyQuery(String preparedQuery) {
            throw new UnsupportedOperationException("Not implemented for unit test");
        }

        @Override
        protected ResultSet sendQuery(String preparedQuery, long timeout) {
            throw new UnsupportedOperationException("Not implemented for unit test");
        }

        @Override
        protected ResultSet sendReadOnlyQuery(String preparedQuery, long timeout) {
            throw new UnsupportedOperationException("Not implemented for unit test");
        }

        @Override
        protected List<String> sendExplain(String preparedQuery) {
            this.lastPreparedQuery = preparedQuery;
            
            // Simulate the parsing logic from GraphImpl.sendExplain
            if (mockExplainResponse instanceof List) {
                List<String> result = new ArrayList<>(mockExplainResponse.size());
                for (Object item : mockExplainResponse) {
                    if (item instanceof byte[]) {
                        result.add(SafeEncoder.encode((byte[]) item));
                    } else {
                        result.add(item.toString());
                    }
                }
                return result;
            } else {
                return Arrays.asList("Fallback response");
            }
        }

        @Override
        public ResultSet callProcedure(String procedure) {
            throw new UnsupportedOperationException("Not implemented for unit test");
        }

        @Override
        public ResultSet callProcedure(String procedure, List<String> args) {
            throw new UnsupportedOperationException("Not implemented for unit test");
        }

        @Override
        public ResultSet callProcedure(String procedure, List<String> args, Map<String, List<String>> kwargs) {
            throw new UnsupportedOperationException("Not implemented for unit test");
        }

        @Override
        public String copyGraph(String destinationGraphId) {
            throw new UnsupportedOperationException("Not implemented for unit test");
        }

        @Override
        public String deleteGraph() {
            throw new UnsupportedOperationException("Not implemented for unit test");
        }

        @Override
        public void close() {
            // No-op for unit test
        }
    }
}