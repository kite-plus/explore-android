#!/usr/bin/env python3
"""Fails when a Kotlin or XML comment holds anything but ASCII: comments are
written in plain English. String literals may hold any text."""

from __future__ import annotations

import pathlib
import re
import sys


def kotlin_comments(source: str):
    """Yields (line number, comment text) for each line that has one,
    skipping string and character literals, multi-line ones included."""
    in_multiline = False
    in_block = False
    for number, line in enumerate(source.splitlines(), 1):
        i = 0
        in_string = False
        found = None
        while i < len(line):
            if in_block:
                end = line.find("*/", i)
                found = line[i:] if end < 0 else line[i : end + 2]
                if end < 0:
                    break
                in_block = False
                i = end + 2
                continue
            if in_multiline:
                end = line.find('"""', i)
                if end < 0:
                    break
                in_multiline = False
                i = end + 3
                continue
            c = line[i]
            if in_string:
                if c == "\\":
                    i += 2
                    continue
                if c == '"':
                    in_string = False
            elif line.startswith('"""', i):
                in_multiline = True
                i += 3
                continue
            elif c == '"':
                in_string = True
            elif c == "'":
                end = line.find("'", i + 2 if line.startswith("'\\", i) else i + 1)
                i = (end if end > 0 else i) + 1
                continue
            elif line.startswith("//", i):
                found = line[i:]
                break
            elif line.startswith("/*", i):
                in_block = True
                found = line[i:]
                i += 2
                continue
            i += 1
        if found:
            yield number, found


def xml_comments(source: str):
    for match in re.finditer(r"<!--(.*?)-->", source, re.S):
        yield source.count("\n", 0, match.start()) + 1, match.group(0)


def main(roots: list[str]) -> int:
    problems = []
    for root in roots:
        for path in sorted(pathlib.Path(root).rglob("*")):
            if path.suffix in (".kt", ".kts"):
                found = kotlin_comments(path.read_text(encoding="utf-8"))
            elif path.suffix == ".xml":
                found = xml_comments(path.read_text(encoding="utf-8"))
            else:
                continue
            for number, text in found:
                if not text.isascii():
                    problems.append(f"{path}:{number}: {text.strip()}")
    if problems:
        print("Comments must be plain ASCII English:")
        print("\n".join(problems))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
