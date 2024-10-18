package com.falkordb.impl;

import static org.junit.Assert.assertThrows;

import java.util.Arrays;
import java.util.HashMap;
import java.util.IllegalFormatConversionException;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class UtilsTest {

    /**
    * Tests the Utils.prepareProcedure method with various input scenarios.
    * 
    * This test method verifies the behavior of the Utils.prepareProcedure method
    * under different conditions:
    * 1. With an empty parameter list
    * 2. With a non-empty parameter list
    * 3. With both parameters and keyword arguments
    * 4. With only keyword arguments
    * 
    * @throws AssertionError if any of the test cases fail
    */    @Test
    public void testPrepareProcedure() {
        Assert.assertEquals("CALL prc()",
                Utils.prepareProcedure("prc", Arrays.asList(new String[]{}), new HashMap<>()));

        Assert.assertEquals("CALL prc(\"a\",\"b\")",
                Utils.prepareProcedure("prc", Arrays.asList(new String[]{"a", "b"}), new HashMap<>()));

        Map<String, List<String>> kwargs = new HashMap<>();
        kwargs.put("y", Arrays.asList(new String[]{"ka", "kb"}));
        Assert.assertEquals("CALL prc(\"a\",\"b\")ka,kb",
                Utils.prepareProcedure("prc", Arrays.asList(new String[]{"a", "b"}), kwargs));

        Assert.assertEquals("CALL prc()ka,kb", Utils.prepareProcedure("prc", Arrays.asList(new String[]{}), kwargs));
    }

    /**
    * Tests the Utils.prepareQuery method for various parameter types and values.
    * 
    * This method verifies that the Utils.prepareQuery method correctly handles
    * different types of parameters, including strings, numbers, booleans, null values,
    * arrays, and lists. It tests the method's ability to properly escape special
    * characters and format different data types for use in a Cypher query.
    * 
    * @param <UNKNOWN> This method does not take any parameters as it is a JUnit test method.
    * @return void This method does not return a value as it is a JUnit test method.
    */
    @Test
    public void testParamsPrep() {
        Map<String, Object> params = new HashMap<>();
        params.put("param", "");
        Assert.assertEquals("CYPHER param=\"\" RETURN $param", Utils.prepareQuery("RETURN $param", params));
        params.put("param", "\"");
        Assert.assertEquals("CYPHER param=\"\\\"\" RETURN $param", Utils.prepareQuery("RETURN $param", params));
        params.put("param", "\"st");
        Assert.assertEquals("CYPHER param=\"\\\"st\" RETURN $param", Utils.prepareQuery("RETURN $param", params));
        params.put("param", 1);
        Assert.assertEquals("CYPHER param=1 RETURN $param", Utils.prepareQuery("RETURN $param", params));
        params.put("param", 2.3);
        Assert.assertEquals("CYPHER param=2.3 RETURN $param", Utils.prepareQuery("RETURN $param", params));
        params.put("param", true);
        Assert.assertEquals("CYPHER param=true RETURN $param", Utils.prepareQuery("RETURN $param", params));
        params.put("param", false);
        Assert.assertEquals("CYPHER param=false RETURN $param", Utils.prepareQuery("RETURN $param", params));
        params.put("param", null);
        Assert.assertEquals("CYPHER param=null RETURN $param", Utils.prepareQuery("RETURN $param", params));
        params.put("param", "str");
        Assert.assertEquals("CYPHER param=\"str\" RETURN $param", Utils.prepareQuery("RETURN $param", params));
        params.put("param", "s\"tr");
        Assert.assertEquals("CYPHER param=\"s\\\"tr\" RETURN $param", Utils.prepareQuery("RETURN $param", params));
        Integer arr[] = {1, 2, 3};
        params.put("param", arr);
        Assert.assertEquals("CYPHER param=[1, 2, 3] RETURN $param", Utils.prepareQuery("RETURN $param", params));
        List<Integer> list = Arrays.asList(1, 2, 3);
        params.put("param", list);
        Assert.assertEquals("CYPHER param=[1, 2, 3] RETURN $param", Utils.prepareQuery("RETURN $param", params));
        String strArr[] = {"1", "2", "3"};
        params.put("param", strArr);
        Assert.assertEquals("CYPHER param=[\"1\", \"2\", \"3\"] RETURN $param",
                Utils.prepareQuery("RETURN $param", params));
        List<String> stringList = Arrays.asList("1", "2", "3");
        params.put("param", stringList);
        Assert.assertEquals("CYPHER param=[\"1\", \"2\", \"3\"] RETURN $param",
                Utils.prepareQuery("RETURN $param", params));
    }

}
