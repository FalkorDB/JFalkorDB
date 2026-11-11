package com.falkordb.impl.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.falkordb.Driver;
import com.falkordb.GraphContext;
import com.falkordb.GraphPipeline;
import com.falkordb.GraphTransaction;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * {@link GraphPipeline} and {@link GraphTransaction} also queue the Redis commands they inherit from Jedis, and a
 * command with a nil reply, such as {@code GET} of a missing key, puts {@code null} in the list of results. Their
 * result lists are therefore declared with {@code @Nullable} elements; this test pins the annotation and the
 * behaviour together, against an in-process fake server.
 */
class PipelineNullRepliesTest {

    private static final String NIL = "$-1\r\n";

    @Test
    void syncAndReturnAllHoldsNullForANilReply() throws Exception {
        assertTrue(
                elementIsNullable(
                        GraphPipeline.class.getMethod("syncAndReturnAll").getAnnotatedReturnType()),
                "syncAndReturnAll() should return List<@Nullable Object>");

        List<Object> results = withContext(
                command -> FakeRedisServer.startsWith(command, "GET") ? NIL : FakeRedisServer.ok(), context -> {
                    GraphPipeline pipeline = context.pipelined();
                    pipeline.get("missing");
                    return pipeline.syncAndReturnAll();
                });

        assertEquals(Collections.singletonList(null), results);
    }

    @Test
    void execHoldsNullForANilReply() throws Exception {
        AnnotatedType exec = GraphTransaction.class.getMethod("exec").getAnnotatedReturnType();
        assertTrue(exec.isAnnotationPresent(Nullable.class), "exec() itself should be @Nullable");
        assertTrue(elementIsNullable(exec), "exec() should return a List<@Nullable Object>");

        List<Object> results = withContext(PipelineNullRepliesTest::transactionReply, context -> {
            GraphTransaction transaction = context.multi();
            transaction.get("missing");
            return transaction.exec();
        });

        assertEquals(Collections.singletonList(null), results);
    }

    @Test
    void execReturnsNullWhenTheTransactionIsAborted() {
        // A WATCHed key changed before EXEC: the server answers EXEC with a nil array and runs nothing.
        List<Object> results = withContext(
                command -> FakeRedisServer.startsWith(command, "EXEC") ? "*-1\r\n" : transactionReply(command),
                context -> {
                    context.watch("watched");
                    GraphTransaction transaction = context.multi();
                    transaction.get("watched");
                    return transaction.exec();
                });

        assertNull(results);
    }

    private static String transactionReply(List<String> command) {
        if (FakeRedisServer.startsWith(command, "GET")) {
            return "+QUEUED\r\n";
        }
        if (FakeRedisServer.startsWith(command, "EXEC")) {
            return FakeRedisServer.array(NIL);
        }
        return FakeRedisServer.ok();
    }

    private static <T> T withContext(Function<List<String>, String> handler, Function<GraphContext, T> body) {
        try (FakeRedisServer server = new FakeRedisServer(handler);
                Driver driver = DriverImpl.connect(
                        "127.0.0.1", server.port(), null, null, SentinelOptions.disabled(), ClusterOptions.disabled());
                GraphContext context = driver.graph("g").getContext()) {
            return body.apply(context);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean elementIsNullable(AnnotatedType listType) {
        return ((AnnotatedParameterizedType) listType)
                .getAnnotatedActualTypeArguments()[0].isAnnotationPresent(Nullable.class);
    }
}
