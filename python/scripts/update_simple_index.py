#!/usr/bin/env python3
###############################################################################
# Copyright (C) 2026, Leo Galambos
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
#
# 1. Redistributions of source code must retain the above copyright notice,
#    this list of conditions and the following disclaimer.
#
# 2. Redistributions in binary form must reproduce the above copyright notice,
#    this list of conditions and the following disclaimer in the documentation
#    and/or other materials provided with the distribution.
#
# 3. Neither the name of the copyright holder nor the names of its contributors
#    may be used to endorse or promote products derived from this software
#    without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
# ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
# LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
# CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
# SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
# INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
# CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
# ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
# POSSIBILITY OF SUCH DAMAGE.
###############################################################################

"""Update Radixor's static PEP 503 index with verified GitHub Release assets."""

from __future__ import annotations

import argparse
import hashlib
import html
import re
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import quote

PACKAGES = ("radixor", "radixor-c", "radixor-models-standard")
REQUIRES_PYTHON = {
    "radixor": ">=3.10",
    "radixor-c": ">=3.10",
    "radixor-models-standard": ">=3.9",
}
REPOSITORY = re.compile(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+\Z")
SHA256 = re.compile(r"[0-9a-f]{64}\Z")
VERSION = re.compile(r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\Z")


class _AnchorParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.anchors: dict[str, str] = {}
        self._href: str | None = None

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag == "a":
            self._href = dict(attrs).get("href")

    def handle_data(self, data: str) -> None:
        if self._href is not None and data.strip():
            self.anchors[data.strip()] = self._href

    def handle_endtag(self, tag: str) -> None:
        if tag == "a":
            self._href = None


def _existing_links(path: Path, expected_prefix: str) -> dict[str, str]:
    if not path.exists():
        return {}
    parser = _AnchorParser()
    parser.feed(path.read_text(encoding="utf-8"))
    for filename, href in parser.anchors.items():
        base, separator, digest = href.rpartition("#sha256=")
        if (
            not separator
            or not base.startswith(expected_prefix)
            or SHA256.fullmatch(digest) is None
            or base.rsplit("/", 1)[-1] != quote(filename, safe="._-")
        ):
            raise ValueError(f"Existing package-index link is unsafe: {href!r}")
    return parser.anchors


def _render_root(root: Path) -> None:
    links = "\n".join(
        f'    <a href="{html.escape(package)}/">{html.escape(package)}</a><br>'
        for package in PACKAGES
    )
    (root / "index.html").write_text(
        '<!doctype html>\n<html><head><meta name="pypi:repository-version" '
        'content="1.0"><title>Radixor Python packages</title></head>\n'
        f"<body>\n{links}\n</body></html>\n",
        encoding="utf-8",
        newline="\n",
    )


def _render_project(path: Path, package: str, links: dict[str, str]) -> None:
    requires_python = html.escape(REQUIRES_PYTHON[package], quote=True)
    anchors = "\n".join(
        f'    <a href="{html.escape(href, quote=True)}" '
        f'data-requires-python="{requires_python}">{html.escape(filename)}</a><br>'
        for filename, href in sorted(links.items())
    )
    path.write_text(
        '<!doctype html>\n<html><head><meta name="pypi:repository-version" '
        f'content="1.0"><title>Links for {html.escape(package)}</title></head>\n'
        f"<body>\n{anchors}\n</body></html>\n",
        encoding="utf-8",
        newline="\n",
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", required=True, type=Path)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--package", required=True, choices=PACKAGES)
    parser.add_argument("--version", required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--artifacts", required=True, type=Path)
    args = parser.parse_args()

    if REPOSITORY.fullmatch(args.repository) is None:
        raise SystemExit("Invalid GitHub repository identity")
    if VERSION.fullmatch(args.version) is None:
        raise SystemExit("Invalid stable release version")
    tag_prefixes = {
        "radixor": "python",
        "radixor-c": "python-c",
        "radixor-models-standard": "python-models-standard",
    }
    expected_tag = f"{tag_prefixes[args.package]}@{args.version}"
    if args.tag != expected_tag:
        raise SystemExit(f"Tag {args.tag!r} does not match {expected_tag!r}")

    root = args.root.resolve()
    if root.name != "simple" or root.parent.name != "python":
        raise SystemExit("The package index must end in python/simple")
    root.mkdir(parents=True, exist_ok=True)
    project = root / args.package
    project.mkdir(exist_ok=True)
    page = project / "index.html"
    prefix = f"https://github.com/{args.repository}/releases/download/"
    links = _existing_links(page, prefix)
    release_prefix = f"{prefix}{quote(args.tag, safe='@._-')}/"
    artifacts = sorted(
        path
        for path in args.artifacts.iterdir()
        if path.is_file()
        and (path.name.endswith(".whl") or path.name.endswith(".tar.gz"))
    )
    if not artifacts:
        raise SystemExit("No package artifacts were provided")
    for artifact in artifacts:
        digest = hashlib.sha256(artifact.read_bytes()).hexdigest()
        href = f"{release_prefix}{quote(artifact.name, safe='._-')}#sha256={digest}"
        previous = links.get(artifact.name)
        if previous is not None and previous != href:
            raise ValueError(
                f"Refusing to replace existing index entry {artifact.name}"
            )
        links[artifact.name] = href

    _render_root(root)
    _render_project(page, args.package, links)
    print(f"indexed {len(artifacts)} artifacts for {args.package} {args.version}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
