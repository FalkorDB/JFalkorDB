package com.falkordb;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * A thin sanity test confirming the public {@link FalkorDB#driver()} entry point is wired to {@link
 * DriverEnvironment}. The resolution logic itself (precedence, validation, error messages) is
 * covered in detail by {@link DriverEnvironmentTest}, which exercises {@link DriverEnvironment}
 * directly with a fake environment instead of the real process one.
 */
class FalkorDBTest {

    @Test
    void noArgDriverDefaultsToLocalhost6379() throws IOException {
        // Neither FALKORDB_URL, FALKORDB_HOST nor FALKORDB_PORT is set for this build (see
        // .github/workflows/maven.yml), so this exercises the unchanged localhost:6379 default.
        // Driver construction is lazy (see ConfigBuilderTest), so no I/O happens here.
        try (Driver driver = FalkorDB.driver()) {
            assertNotNull(driver);
        }
    }
}
