#!/usr/bin/env python3
"""Print the FalkorDB image the server-backed suite runs against for a release line.

Lines:
  min   the pinned minimum supported version, the suite's default (prints nothing: FalkorDbImage.DEFAULT)
  4     the newest v4.x.y release
  6.0   the newest 6.0.z release
  6     the newest 6.x.y release (a concrete tag, so a future 7.0 `latest` can't silently move this line)
  edge  the unreleased edge build
  latest
        whatever `latest` points at

The 4.x tags carry a `v` prefix (v4.22.4) and the 6.x tags don't (6.2.1). Tags are read from the Docker Hub
API; when it can't be reached, a pinned fallback is printed with a warning, so an outage doesn't fail CI.
"""

import json
import re
import sys
import urllib.request

REPOSITORY = "falkordb/falkordb"
TAGS_URL = "https://hub.docker.com/v2/repositories/" + REPOSITORY + "/tags?name={prefix}&page_size=100"
MAX_PAGES = 20

# line -> (tag search prefix, tag pattern, fallback tag)
RELEASE_LINES = {
    "4": ("v4.", re.compile(r"^v4\.(\d+)\.(\d+)$"), "v4.22.5"),
    "6.0": ("6.0.", re.compile(r"^6\.0\.(\d+)$"), "6.0.4"),
    "6": ("6.", re.compile(r"^6\.(\d+)\.(\d+)$"), "6.2.1"),
}
FLOATING = {"edge": "edge", "latest": "latest"}


def newest(tags, pattern):
    """The newest tag matching `pattern`, compared by version numbers rather than as strings, or None.

    Tags with a suffix (-alpine, -x64, ...) never match the anchored patterns.
    """
    best = None
    best_key = None
    for tag in tags:
        match = pattern.match(tag)
        if not match:
            continue
        key = tuple(int(part) for part in match.groups())
        if best_key is None or key > best_key:
            best, best_key = tag, key
    return best


def fetch_tags(prefix):
    """Every tag of the repository whose name contains `prefix`, following the API's pages."""
    tags = []
    url = TAGS_URL.format(prefix=prefix)
    for _ in range(MAX_PAGES):
        request = urllib.request.Request(url, headers={"User-Agent": "falkordb-image-resolver"})
        with urllib.request.urlopen(request, timeout=15) as response:
            page = json.load(response)
        tags.extend(result["name"] for result in page.get("results", []))
        url = page.get("next")
        if not url:
            break
    return tags


def resolve(line, fetch=fetch_tags):
    """The image for `line`, or "" for the suite's pinned default."""
    if line == "min":
        return ""
    if line in FLOATING:
        return REPOSITORY + ":" + FLOATING[line]
    if line not in RELEASE_LINES:
        raise ValueError("unknown FalkorDB release line %r: use one of min, %s, %s"
                         % (line, ", ".join(RELEASE_LINES), ", ".join(FLOATING)))
    prefix, pattern, fallback = RELEASE_LINES[line]
    try:
        tag = newest(fetch(prefix), pattern)
        if tag is None:
            raise LookupError("no tag matches " + pattern.pattern)
    except Exception as error:  # noqa: BLE001 - any failure falls back to the pin
        print("warning: can't list %s tags on Docker Hub (%s); using the pinned %s"
              % (REPOSITORY, error, fallback), file=sys.stderr)
        tag = fallback
    return REPOSITORY + ":" + tag


def main(argv):
    if len(argv) != 2:
        print("usage: falkordb_image.py <min|4|6.0|6|edge|latest>", file=sys.stderr)
        return 2
    try:
        print(resolve(argv[1]))
    except ValueError as error:
        print("error: %s" % error, file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
