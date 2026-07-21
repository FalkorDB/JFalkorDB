package com.falkordb;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
