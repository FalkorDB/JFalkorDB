package com.falkordb;

import org.testcontainers.utility.DockerImageName;

/**
 * Resolves the FalkorDB container image used by {@link TestServer}.
 *
 * <p>Extracted from {@code TestServer} so the resolution logic is unit-testable without triggering
 * {@code TestServer}'s static initializer (which starts a container). The image can be overridden with
 * the {@code FALKORDB_IMAGE} system property or environment variable (e.g. {@code
 * falkordb/falkordb:edge}) to matrix the suite over FalkorDB versions; unset/blank uses the pinned
 * {@link #DEFAULT} digest.
 */
final class FalkorDbImage {

    /** Default pinned digest (v4.20.1) for a reproducible suite; matches the {@code smoke-jdk8} CI image. */
    static final String DEFAULT =
            "falkordb/falkordb@sha256:9042fdc4e53f5390ca5a3993aa71506523970efb40ffb9a98e6a4b1a9a4f8862";

    private FalkorDbImage() {}

    /**
     * Picks the effective override from its two sources: the system {@code property} if non-blank,
     * otherwise the {@code env} value (which may itself be null/blank). A blank system property (e.g.
     * {@code -DFALKORDB_IMAGE=}) therefore does not shadow a non-blank environment variable.
     *
     * @param property the {@code FALKORDB_IMAGE} system property value; may be null
     * @param env the {@code FALKORDB_IMAGE} environment value; may be null
     * @return the value to hand to {@link #resolve(String)}
     */
    static String pickOverride(String property, String env) {
        return (property != null && !property.trim().isEmpty()) ? property : env;
    }

    /**
     * Resolves the container image: the trimmed {@code override} if non-blank, otherwise
     * {@link #DEFAULT}. The result is marked as a compatible substitute for {@code falkordb/falkordb}
     * so Testcontainers accepts a custom tag or digest.
     *
     * @param override the raw {@code FALKORDB_IMAGE} value (system property or env var); may be null
     * @return the image to run
     * @throws IllegalStateException if {@code override} is not a well-formed Docker image reference
     */
    static DockerImageName resolve(String override) {
        String image = (override == null || override.trim().isEmpty()) ? DEFAULT : override.trim();
        try {
            return DockerImageName.parse(image).asCompatibleSubstituteFor("falkordb/falkordb");
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "FALKORDB_IMAGE is not a valid Docker image reference: \"" + image + "\"", e);
        }
    }
}
