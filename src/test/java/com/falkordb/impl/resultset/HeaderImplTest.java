package com.falkordb.impl.resultset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.falkordb.Header;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.util.SafeEncoder;

public class HeaderImplTest {

    private static List<Object> column(long type, String name) {
        return Arrays.asList((Object) type, SafeEncoder.encode(name));
    }

    @Test
    public void equalsHashCodeContract() {
        // equals/hashCode compare the parsed schema, which is now the entire state (the raw source list
        // is no longer retained). The lists are always constructor-initialized, so NULL_FIELDS is safe.
        EqualsVerifier.forClass(HeaderImpl.class)
                .suppress(Warning.NONFINAL_FIELDS, Warning.STRICT_INHERITANCE, Warning.NULL_FIELDS)
                .verify();
    }

    @Test
    public void parsesColumnNamesAndTypes() {
        // Ordinals: 0 COLUMN_UNKNOWN, 1 COLUMN_SCALAR, 2 COLUMN_NODE, 3 COLUMN_RELATION.
        HeaderImpl header = new HeaderImpl(Arrays.asList(column(2L, "n"), column(3L, "r")));

        assertEquals(Arrays.asList("n", "r"), header.getSchemaNames());
        assertEquals(
                Arrays.asList(Header.ResultSetColumnTypes.COLUMN_NODE, Header.ResultSetColumnTypes.COLUMN_RELATION),
                header.getSchemaTypes());
    }

    @Test
    public void unknownColumnTypeIsReportedAsAProtocolError() {
        // A newer server sending a column-type ordinal this client does not know must produce the same
        // graceful error ResultSetScalarTypes.getValue gives for scalars, not a bare
        // ArrayIndexOutOfBoundsException from indexing values() with an unvalidated ordinal. The schema
        // is parsed in the constructor, so this now fails fast there rather than on first access.
        List<List<Object>> raw = Collections.singletonList(column(99L, "future"));

        assertThrows(JedisDataException.class, () -> new HeaderImpl(raw));
    }

    @Test
    public void concurrentFirstAccessBuildsTheSchemaExactlyOnce() throws Exception {
        // AsyncGraph hands ResultSets to many threads. Building the schema lazily without
        // synchronization let two threads both see an empty list and both append to it, duplicating
        // every column. Run enough racing rounds that an unsynchronized build reliably double-populates.
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int round = 0; round < 200; round++) {
                HeaderImpl header = new HeaderImpl(Collections.singletonList(column(2L, "n")));
                CyclicBarrier start = new CyclicBarrier(threads);
                List<Callable<List<String>>> tasks = new ArrayList<>(threads);
                for (int t = 0; t < threads; t++) {
                    tasks.add(() -> {
                        start.await(5, TimeUnit.SECONDS);
                        return header.getSchemaNames();
                    });
                }
                for (Future<List<String>> f : pool.invokeAll(tasks)) {
                    assertEquals(Collections.singletonList("n"), f.get(), "schema duplicated under concurrent access");
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
