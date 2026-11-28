"""Tests for falkordb_image.py: `python3 -m unittest discover -s scripts` (or `just falkordb-image-test`)."""

import unittest

import falkordb_image as image

TAGS = [
    "v4.9.0", "v4.10.0", "v4.22.4", "v4.22.3", "v4.22.4-alpine", "v4.22.4-x64", "v4.99", "4.30.0",
    "6.0.4", "6.0.10", "6.0.9-alpine", "6.2.1", "6.10.0", "6.2.1-arm64", "60.0.0",
    "edge", "latest",
]


class NewestTest(unittest.TestCase):

    def test_compares_by_version_not_as_strings(self):
        # "v4.9.0" > "v4.10.0" as strings; as versions 4.22.4 is the newest
        self.assertEqual("v4.22.4", image.newest(TAGS, image.RELEASE_LINES["4"][1]))

    def test_each_line_keeps_to_its_own_tags(self):
        self.assertEqual("6.0.10", image.newest(TAGS, image.RELEASE_LINES["6.0"][1]))
        self.assertEqual("6.10.0", image.newest(TAGS, image.RELEASE_LINES["6"][1]))

    def test_suffixed_and_unprefixed_tags_never_match(self):
        pattern = image.RELEASE_LINES["4"][1]
        for tag in ["v4.22.4-alpine", "v4.22.4-x64", "v4.99", "4.30.0"]:
            self.assertIsNone(pattern.match(tag), tag)

    def test_no_match_is_none(self):
        self.assertIsNone(image.newest(["edge", "latest"], image.RELEASE_LINES["6"][1]))


class ResolveTest(unittest.TestCase):

    def test_release_lines_resolve_to_concrete_tags(self):
        fetch = lambda prefix: TAGS  # noqa: E731
        self.assertEqual("falkordb/falkordb:v4.22.4", image.resolve("4", fetch))
        self.assertEqual("falkordb/falkordb:6.0.10", image.resolve("6.0", fetch))
        self.assertEqual("falkordb/falkordb:6.10.0", image.resolve("6", fetch))

    def test_min_is_the_suites_pinned_default(self):
        self.assertEqual("", image.resolve("min", None))

    def test_floating_tags_need_no_lookup(self):
        self.assertEqual("falkordb/falkordb:edge", image.resolve("edge", None))
        self.assertEqual("falkordb/falkordb:latest", image.resolve("latest", None))

    def test_an_unreachable_registry_falls_back_to_the_pin(self):
        def unreachable(prefix):
            raise OSError("network is down")
        self.assertEqual("falkordb/falkordb:v4.22.5", image.resolve("4", unreachable))
        self.assertEqual("falkordb/falkordb:6.2.1", image.resolve("6", lambda prefix: ["edge"]))

    def test_an_unknown_line_is_an_error(self):
        with self.assertRaises(ValueError):
            image.resolve("7", None)


if __name__ == "__main__":
    unittest.main()
