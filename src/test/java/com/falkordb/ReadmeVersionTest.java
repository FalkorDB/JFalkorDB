package com.falkordb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Guards the version numbers users copy out of {@code README.md}, which nothing owned before and
 * which therefore drifted silently: at the time this test was written the release snippet was two
 * releases stale and the snapshot snippet one ahead of nothing at all.
 *
 * <p>The two snippets are owned differently, because release-please can only own one of them:
 *
 * <ul>
 *   <li><b>Snapshots</b> — carries an {@code x-release-please-version} marker, so release-please
 *       rewrites it. Verified here against {@code pom.xml} so a broken marker or a dropped {@code
 *       extra-files} entry fails loudly instead of silently resuming the drift.
 *   <li><b>Official Releases</b> — deliberately <em>not</em> marked. Because this project releases
 *       with {@code release-type: java}, release-please follows every release PR with a snapshot PR,
 *       and that snapshot PR re-applies the same {@code extra-files} updaters with the {@code
 *       -SNAPSHOT} version. A marker here would therefore leave the block users copy pointing at a
 *       version that was never published to Maven Central. It is checked against {@code
 *       CHANGELOG.md} instead — the release-time check the issue proposed as the alternative.
 * </ul>
 *
 * <p>So this test failing on a release PR is <em>the point</em>: it means the release snippet still
 * names the previous release and needs its one line updated before shipping.
 *
 * @see <a href="https://github.com/FalkorDB/JFalkorDB/issues/401">#401</a>
 */
class ReadmeVersionTest {

    /** Everything under a {@code ## Heading} up to the next one, so a match cannot leak sideways. */
    private static final String SECTION = "(?ms)^##\\s+%s\\b(.*?)(?=^##\\s|\\z)";

    /** The body of a fenced {@code ```xml} block. Reluctant, so it stops at that block's fence. */
    private static final Pattern FENCED_XML = Pattern.compile("(?s)```xml\\s(.*?)```");

    private static final Pattern XML_VERSION = Pattern.compile("<version>([^<]+)</version>");

    /** The marker that hands a line to release-please. */
    private static final String MARKER = "<!-- x-release-please-version -->";

    /** release-please writes the newest release first, as {@code ## [x.y.z](compare-link) (date)}. */
    private static final Pattern LATEST_CHANGELOG_VERSION = Pattern.compile("(?m)^##\\s+\\[([^\\]]+)\\]");

    /** The root project version, i.e. the first {@code <version>} after our own artifactId. */
    private static final Pattern PROJECT_VERSION =
            Pattern.compile("(?s)<artifactId>jfalkordb</artifactId>\\s*<version>([^<]+)</version>");

    @Test
    void officialReleasesSnippetNamesTheLatestRelease() throws IOException {
        String readmeVersion = versionUnderHeading("Official Releases");
        String released = firstMatch(LATEST_CHANGELOG_VERSION, read("CHANGELOG.md"), "latest CHANGELOG.md version");
        assertEquals(
                released,
                readmeVersion,
                "README.md's \"Official Releases\" snippet is the block users copy into their pom.xml, "
                        + "but it names a version that is not the latest release. Update it to " + released
                        + ". It is not auto-updated on purpose - see this test's Javadoc and issue #401.");
    }

    @Test
    void officialReleasesSnippetNeverNamesASnapshot() throws IOException {
        String readmeVersion = versionUnderHeading("Official Releases");
        assertTrue(
                !readmeVersion.contains("SNAPSHOT"),
                "README.md's \"Official Releases\" snippet names \"" + readmeVersion
                        + "\", which is not published to Maven Central, so the snippet cannot resolve. "
                        + "This is what happens if an x-release-please-version marker is added to that "
                        + "block: the snapshot PR that follows every release rewrites it. See issue #401.");
    }

    @Test
    void snapshotsSnippetTracksTheProjectVersion() throws IOException {
        String readmeVersion = versionUnderHeading("Snapshots");
        String projectVersion = firstMatch(PROJECT_VERSION, read("pom.xml"), "project version in pom.xml");
        assertEquals(
                projectVersion,
                readmeVersion,
                "README.md's \"Snapshots\" snippet should track pom.xml. It carries an "
                        + "x-release-please-version marker, so a mismatch means release-please stopped "
                        + "updating it - check the extra-files entry in release-please-config.json (#401).");
    }

    /**
     * The sibling modules build against the client through a {@code jfalkordb.version} property whose
     * default drifted for the same reason the README did. Each carries the same marker, and each is
     * only overridden by {@code just}, so a stale default silently builds an example, a benchmark or
     * a JDK-8 smoke test against an old client instead of the one in the working tree.
     */
    @Test
    void siblingModulesTrackTheProjectVersion() throws IOException {
        String projectVersion = firstMatch(PROJECT_VERSION, read("pom.xml"), "project version in pom.xml");
        for (String module : new String[] {"examples", "smoke-test", "pin-check", "benchmarks"}) {
            String line = lineDeclaring(
                    "jfalkordb.version", read(module + "/pom.xml"), "jfalkordb.version in " + module + "/pom.xml");
            String declared = firstMatch(
                    Pattern.compile("<jfalkordb\\.version>([^<]+)</jfalkordb\\.version>"),
                    line,
                    "jfalkordb.version in " + module + "/pom.xml");
            assertEquals(projectVersion, declared, module + "/pom.xml's jfalkordb.version default is stale (#401)");
            assertTrue(
                    line.contains(MARKER),
                    module + "/pom.xml lost its x-release-please-version marker, so its default will "
                            + "drift again - check release-please-config.json (#401)");
        }
    }

    /**
     * The three tests above compare versions, so they only notice drift once it has happened. This
     * one asserts the machinery that prevents it: every file carrying a marker must also be listed in
     * {@code release-please-config.json}, and every marker must still be there. Delete either half
     * and the versions still agree today, but the next release silently resumes the drift #401 was
     * filed about.
     */
    @Test
    void releasePleaseStillOwnsEveryVersionOutsideTheRootPom() throws IOException {
        String config = read("release-please-config.json");
        String extraFiles = firstMatch(
                Pattern.compile("(?s)\"extra-files\"\\s*:\\s*\\[(.*?)]"),
                config,
                "extra-files in " + "release-please-config.json");

        for (String path : new String[] {
            "README.md", "examples/pom.xml", "smoke-test/pom.xml", "pin-check/pom.xml", "benchmarks/pom.xml"
        }) {
            String entry = firstMatch(
                    Pattern.compile("(\\{[^{}]*\"path\"\\s*:\\s*\"" + Pattern.quote(path) + "\"[^{}]*})"),
                    extraFiles,
                    "\"" + path + "\" among the extra-files in release-please-config.json");
            assertTrue(
                    Pattern.compile("\"type\"\\s*:\\s*\"generic\"")
                            .matcher(entry)
                            .find(),
                    "release-please-config.json lists " + path + " but not with \"type\": \"generic\", so its "
                            + "x-release-please-version markers may stop being honoured (#401)");
        }

        assertTrue(
                lineDeclaring("version", xmlBlockUnderHeading("Snapshots"), "README.md's snapshot <version>")
                        .contains(MARKER),
                "README.md's \"Snapshots\" snippet lost its x-release-please-version marker, so release-please "
                        + "will stop updating it and it will go stale again (#401)");
    }

    private static String versionUnderHeading(String heading) throws IOException {
        return firstMatch(
                XML_VERSION,
                xmlBlockUnderHeading(heading),
                "<version> in the ```xml block under README.md heading \"" + heading + "\"");
    }

    /** The fenced {@code ```xml} block carrying the version that a README heading advertises. */
    private static String xmlBlockUnderHeading(String heading) throws IOException {
        String section = firstMatch(
                Pattern.compile(String.format(SECTION, Pattern.quote(heading))),
                read("README.md"),
                "\"" + heading + "\" section of README.md");
        return versionedXmlBlockIn(section, heading);
    }

    /**
     * The whole line declaring {@code <tag>}, because release-please's {@code Generic} updater is
     * line-scoped: it rewrites the first semver-looking string on any line carrying the marker.
     * Neither the marker's position on that line nor the whitespace around it matters to it, so
     * nothing here should assert either.
     */
    private static String lineDeclaring(String tag, String content, String what) {
        return firstMatch(Pattern.compile("(?m)^(.*<" + Pattern.quote(tag) + ">.*)$"), content, what);
    }

    /**
     * The first fenced {@code ```xml} block of the section that declares a {@code <version>}. Not
     * simply the first block: the "Snapshots" section opens with a {@code <repositories>} snippet.
     */
    private static String versionedXmlBlockIn(String section, String heading) {
        Matcher blocks = FENCED_XML.matcher(section);
        while (blocks.find()) {
            if (XML_VERSION.matcher(blocks.group(1)).find()) {
                return blocks.group(1);
            }
        }
        throw new AssertionError("README.md's \"" + heading + "\" section has no ```xml block declaring a <version>; "
                + "has the file's layout changed?");
    }

    private static String firstMatch(Pattern pattern, String haystack, String what) {
        Matcher matcher = pattern.matcher(haystack);
        assertTrue(matcher.find(), "could not find the " + what + "; has the file's layout changed?");
        return matcher.group(1).trim();
    }

    /** Reads a file relative to the project root, which is Surefire's working directory. */
    private static String read(String relativePath) throws IOException {
        Path path = Paths.get(relativePath);
        assertTrue(Files.isRegularFile(path), "expected to find " + relativePath + " at " + path.toAbsolutePath());
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
