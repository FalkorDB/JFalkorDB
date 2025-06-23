package com.falkordb.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.HashMap;
import java.util.IllegalFormatConversionException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class UtilsTest {

    @Test
    public void testPrepareProcedure() {
        Assertions.assertEquals("CALL prc()",
                Utils.prepareProcedure("prc", Arrays.asList(new String[]{}), new HashMap<>()));

        Assertions.assertEquals("CALL prc(\"a\",\"b\")",
                Utils.prepareProcedure("prc", Arrays.asList(new String[]{"a", "b"}), new HashMap<>()));

        Map<String, List<String>> kwargs = new HashMap<>();
        kwargs.put("y", Arrays.asList(new String[]{"ka", "kb"}));
        Assertions.assertEquals("CALL prc(\"a\",\"b\")ka,kb",
                Utils.prepareProcedure("prc", Arrays.asList(new String[]{"a", "b"}), kwargs));

        Assertions.assertEquals("CALL prc()ka,kb", Utils.prepareProcedure("prc", Arrays.asList(new String[]{}), kwargs));
    }

    @Test
    public void testParamsPrep() {
        Map<String, Object> params = new HashMap<>();
        params.put("param", "");
        Assertions.assertEquals("CYPHER param=\"\" RETURN $param", Utils.prepareQuery("RETURN $param", params));
        params.put("param", "\"");
        Assertions.assertEquals("CYPHER param=\"\\\"\" RETURN $param", Utils.prepareQuery("RETURN $param", params));
        params.put("param", "\"st");
        Assertions.assertEquals("CYPHER param=\"\\\"st\" RETURN $param", Utils.prepareQuery("RETURN $param", params));
        params.put("param", 1);
        Assertions.assertEquals("CYPHER param=1 RETURN $param", Utils.prepareQuery("RETURN $param", params));
        params.put("param", 2.3);
        Assertions.assertEquals("CYPHER param=2.3 RETURN $param", Utils.prepareQuery("RETURN $param", params));
        params.put("param", true);
        Assertions.assertEquals("CYPHER param=true RETURN $param", Utils.prepareQuery("RETURN $param", params));
        params.put("param", false);
        Assertions.assertEquals("CYPHER param=false RETURN $param", Utils.prepareQuery("RETURN $param", params));
        params.put("param", null);
        Assertions.assertEquals("CYPHER param=null RETURN $param", Utils.prepareQuery("RETURN $param", params));
        params.put("param", "str");
        Assertions.assertEquals("CYPHER param=\"str\" RETURN $param", Utils.prepareQuery("RETURN $param", params));
        params.put("param", "s\"tr");
        Assertions.assertEquals("CYPHER param=\"s\\\"tr\" RETURN $param", Utils.prepareQuery("RETURN $param", params));
        Integer arr[] = {1, 2, 3};
        params.put("param", arr);
        Assertions.assertEquals("CYPHER param=[1, 2, 3] RETURN $param", Utils.prepareQuery("RETURN $param", params));
        List<Integer> list = Arrays.asList(1, 2, 3);
        params.put("param", list);
        Assertions.assertEquals("CYPHER param=[1, 2, 3] RETURN $param", Utils.prepareQuery("RETURN $param", params));
        String strArr[] = {"1", "2", "3"};
        params.put("param", strArr);
        Assertions.assertEquals("CYPHER param=[\"1\", \"2\", \"3\"] RETURN $param",
                Utils.prepareQuery("RETURN $param", params));
        List<String> stringList = Arrays.asList("1", "2", "3");
        params.put("param", stringList);
        Assertions.assertEquals("CYPHER param=[\"1\", \"2\", \"3\"] RETURN $param",
                Utils.prepareQuery("RETURN $param", params));
    }

}
