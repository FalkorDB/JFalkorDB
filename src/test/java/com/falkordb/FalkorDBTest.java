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
        // Blank values are deliberately checked through DriverEnvironment.trimToNull so "set but
        // blank" counts as unset here exactly as it does during resolution — skipping on a blank
        // value would drop this coverage for no reason.
        Assumptions.assumeTrue(
                isUnset(DriverEnvironment.URL_VAR)
                        && isUnset(DriverEnvironment.HOST_VAR)
                        && isUnset(DriverEnvironment.PORT_VAR),
                "FalkorDB.driver() environment variables are set in this environment");
        // Driver construction is lazy (see ConfigBuilderTest), so no I/O happens here.
        try (Driver driver = FalkorDB.driver()) {
            assertNotNull(driver);
        }
    }

    /** Whether {@code name} is absent from the environment, as {@link DriverEnvironment} judges it. */
    private static boolean isUnset(String name) {
        return DriverEnvironment.trimToNull(System.getenv(name)) == null;
    }
}
