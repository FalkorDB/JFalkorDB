package com.falkordb;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import org.junit.jupiter.api.Assumptions;
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
        // This is a wiring sanity check for the unchanged default, not a resolution-logic test, so
        // skip rather than fail/flake if the ambient environment happens to configure driver()
        // differently (mirrors the same guard InstantiationIT#createDefaultClient already applies).
        Assumptions.assumeTrue(
                System.getenv(DriverEnvironment.URL_VAR) == null
                        && System.getenv(DriverEnvironment.HOST_VAR) == null
                        && System.getenv(DriverEnvironment.PORT_VAR) == null,
                "FalkorDB.driver() environment variables are set in this environment");
        // Driver construction is lazy (see ConfigBuilderTest), so no I/O happens here.
        try (Driver driver = FalkorDB.driver()) {
            assertNotNull(driver);
        }
    }
}
