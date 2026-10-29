package com.falkordb.impl.api;

import com.falkordb.exceptions.GraphException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.util.SafeEncoder;

/**
 * Unit tests for the explain reply parser, {@link ExplainPlans}, that GraphPipelineImpl, GraphTransactionImpl,
 * GraphContextImpl and GraphImpl share. These tests used to exercise private copies of the parsing logic.
 */
public class GraphExplainBuilderUnitTest {

    @Test
    public void testExplainBuilderWithListResponse() {
        // Test the Builder logic from GraphPipelineImpl/GraphTransactionImpl explain methods
        List<Object> mockResponse = Arrays.asList(
                SafeEncoder.encode("Results"),
                SafeEncoder.encode("    Project"),
                SafeEncoder.encode("        Filter"),
                SafeEncoder.encode("            NodeByLabelScan"));

        List<String> result = buildExplainResponse(mockResponse);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(4, result.size());
        Assertions.assertEquals("Results", result.get(0));
        Assertions.assertEquals("    Project", result.get(1));
        Assertions.assertEquals("        Filter", result.get(2));
        Assertions.assertEquals("            NodeByLabelScan", result.get(3));
    }

    @Test
    public void testExplainBuilderWithMixedTypeResponse() {
        // Test Builder logic with mixed byte arrays and strings
        List<Object> mockResponse = Arrays.asList(
                SafeEncoder.encode("Results"),
                "    Project", // String instead of byte array
                SafeEncoder.encode("        Filter"),
                "            NodeByLabelScan" // Another string
                );

        List<String> result = buildExplainResponse(mockResponse);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(4, result.size());
        Assertions.assertEquals("Results", result.get(0));
        Assertions.assertEquals("    Project", result.get(1));
        Assertions.assertEquals("        Filter", result.get(2));
        Assertions.assertEquals("            NodeByLabelScan", result.get(3));
    }

    @Test
    public void testExplainBuilderWithEmptyListResponse() {
        // Test Builder logic with empty response
        List<Object> emptyResponse = new ArrayList<>();

        List<String> result = buildExplainResponse(emptyResponse);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isEmpty());
    }

    @Test
    public void testExplainBuilderWithSingleItemResponse() {
        // Test Builder logic with single item response
        List<Object> singleItemResponse = Collections.<Object>singletonList(SafeEncoder.encode("Single result"));

        List<String> result = buildExplainResponse(singleItemResponse);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals("Single result", result.get(0));
    }

    @Test
    public void testExplainBuilderWithNullItemsInResponse() {
        // Test Builder logic with null items in response (edge case)
        List<Object> responseWithNulls = Arrays.asList(
                SafeEncoder.encode("Valid line"),
                null, // null item
                SafeEncoder.encode("Another valid line"),
                null // another null item
                );

        List<String> result = buildExplainResponseSafely(responseWithNulls);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(4, result.size());
        Assertions.assertEquals("Valid line", result.get(0));
        Assertions.assertEquals("null", result.get(1)); // null.toString() -> "null"
        Assertions.assertEquals("Another valid line", result.get(2));
        Assertions.assertEquals("null", result.get(3));
    }

    @Test
    public void testExplainBuilderFallbackWithByteArrayResponse() {
        // Test Builder fallback logic when response is not a List (byte array)
        byte[] singleByteResponse = SafeEncoder.encode("Fallback response");

        List<String> result = buildExplainResponseFallback(singleByteResponse);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals("Fallback response", result.get(0));
    }

    @Test
    public void testExplainBuilderFallbackWithStringResponse() {
        // Test Builder fallback logic when response is not a List (string)
        String singleStringResponse = "String fallback response";

        List<String> result = buildExplainResponseFallback(singleStringResponse);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals("String fallback response", result.get(0));
    }

    @Test
    public void testExplainBuilderWithComplexStrings() {
        // Test Builder logic with complex strings containing special characters
        List<Object> complexResponse = Arrays.asList(
                SafeEncoder.encode("Results with unicode: 🔍"),
                SafeEncoder.encode("    Project with quotes: \"value\""),
                SafeEncoder.encode("        Filter with newlines:\n    nested content"),
                SafeEncoder.encode("            Scan with tabs:\ttabbed content"));

        List<String> result = buildExplainResponse(complexResponse);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(4, result.size());
        Assertions.assertEquals("Results with unicode: 🔍", result.get(0));
        Assertions.assertEquals("    Project with quotes: \"value\"", result.get(1));
        Assertions.assertTrue(result.get(2).contains("newlines"));
        Assertions.assertTrue(result.get(3).contains("tabbed"));
    }

    @Test
    public void testExplainBuilderWithNumericStrings() {
        // Test Builder logic when response contains numeric values as strings
        List<Object> numericResponse = Arrays.asList(
                SafeEncoder.encode("Cost: 1.23"),
                SafeEncoder.encode("Rows: 1000"),
                "Numeric object: 456" // Direct numeric string
                );

        List<String> result = buildExplainResponse(numericResponse);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(3, result.size());
        Assertions.assertEquals("Cost: 1.23", result.get(0));
        Assertions.assertEquals("Rows: 1000", result.get(1));
        Assertions.assertEquals("Numeric object: 456", result.get(2));
    }

    @Test
    public void testExplainBuilderWithLongResponse() {
        // Test Builder logic with a large number of items
        List<Object> longResponse = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            longResponse.add(SafeEncoder.encode("Line " + i));
        }

        List<String> result = buildExplainResponse(longResponse);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(100, result.size());
        Assertions.assertEquals("Line 0", result.get(0));
        Assertions.assertEquals("Line 99", result.get(99));
    }

    @Test
    public void testExplainBuilderWithNoReply() {
        Assertions.assertTrue(buildExplainResponse(null).isEmpty());
    }

    @Test
    public void testExplainBuilderThrowsAnErrorInsideTheReply() {
        // Jedis puts an error element of an array reply into the list as an exception. It is not a plan
        // line: turning it into one with toString() would hide the error.
        JedisDataException error = new JedisDataException("ERR something failed");
        List<Object> response = Arrays.asList(SafeEncoder.encode("Results"), error);

        GraphException e = Assertions.assertThrows(GraphException.class, () -> buildExplainResponse(response));
        Assertions.assertSame(error, e.getCause());
    }

    /** Runs the builder that GraphPipelineImpl and GraphTransactionImpl attach to an explain command. */
    private static List<String> buildExplainResponse(Object o) {
        return ExplainPlans.BUILDER.build(o);
    }

    /** The same builder: a null line is converted with {@code String.valueOf}, like any other non-text line. */
    private static List<String> buildExplainResponseSafely(Object o) {
        return ExplainPlans.BUILDER.build(o);
    }

    /** The same builder, given a reply that is not an array. */
    private static List<String> buildExplainResponseFallback(Object o) {
        return ExplainPlans.BUILDER.build(o);
    }
}
