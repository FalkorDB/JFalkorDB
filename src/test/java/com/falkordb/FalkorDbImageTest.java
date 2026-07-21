package com.falkordb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FalkorDbImage#resolve} — the {@code FALKORDB_IMAGE} override resolution. These
 * only parse image names, so they run without Docker (and without loading {@link TestServer}, whose
 * static initializer would start a container).
 */
class FalkorDbImageTest {

    @Test
    void defaultsWhenUnsetOrBlank() {
        assertEquals(FalkorDbImage.DEFAULT, FalkorDbImage.resolve(null).asCanonicalNameString());
        assertEquals(FalkorDbImage.DEFAULT, FalkorDbImage.resolve("").asCanonicalNameString());
        assertEquals(FalkorDbImage.DEFAULT, FalkorDbImage.resolve("   ").asCanonicalNameString());
    }

    @Test
    void usesTrimmedOverride() {
        assertEquals(
                "falkordb/falkordb:edge",
                FalkorDbImage.resolve("falkordb/falkordb:edge").asCanonicalNameString());
        assertEquals(
                "falkordb/falkordb:v4.20.1",
                FalkorDbImage.resolve("  falkordb/falkordb:v4.20.1  ").asCanonicalNameString());
    }

    @Test
    void rejectsMalformedOverride() {
        assertThrows(IllegalStateException.class, () -> FalkorDbImage.resolve("bad image::"));
    }

    @Test
    void pickOverridePrefersNonBlankProperty() {
        assertEquals("prop", FalkorDbImage.pickOverride("prop", () -> "env"));
        assertEquals("  prop  ", FalkorDbImage.pickOverride("  prop  ", () -> null));
    }

    @Test
    void pickOverrideFallsBackToEnvWhenPropertyBlankOrNull() {
        assertEquals("env", FalkorDbImage.pickOverride(null, () -> "env"));
        assertEquals("env", FalkorDbImage.pickOverride("", () -> "env"));
        assertEquals("env", FalkorDbImage.pickOverride("   ", () -> "env"));
        assertNull(FalkorDbImage.pickOverride(null, () -> null));
    }

    @Test
    void pickOverrideDoesNotReadEnvWhenPropertyIsSet() {
        // The env supplier must be consulted lazily — not at all when the property is non-blank.
        assertEquals("prop", FalkorDbImage.pickOverride("prop", () -> {
            throw new AssertionError("env supplier must not be read when the property is set");
        }));
    }

    @Test
    void blankPropertyDoesNotShadowEnvImage() {
        // Regression: -DFALKORDB_IMAGE= (blank) must not override a real env image and fall back to the
        // default; the non-blank env value wins.
        assertEquals(
                "falkordb/falkordb:edge",
                FalkorDbImage.resolve(FalkorDbImage.pickOverride("", () -> "falkordb/falkordb:edge"))
                        .asCanonicalNameString());
    }
}
