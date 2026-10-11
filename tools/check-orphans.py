#!/usr/bin/env python3
"""Find types that are defined and never referenced anywhere else.

Round 36 found a cast dialog that was complete, correct, and had no caller: the menu
item set a flag nobody rendered. The preference audit could not see it (the flag was
read), grep-for-implementation could not see it (the implementation existed), and
neither could a type check. The only question that would have caught it is "does
anyone use this".

This asks that question in the crudest useful form: for every declared type, count how
many files mention its name. One file means the file that declares it, so nothing else
knows it exists.

Crude on purpose. It cannot tell a type used only in its own file from a type nobody
uses at all, and it counts a name in a comment the same as a name in a call. That makes
it a source of candidates, not a verdict — every hit needs reading.
"""

from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent

TARGETS = {
    "ios": (ROOT / "ios" / "SenPlay", "*.swift"),
    "android": (ROOT / "android" / "app" / "src" / "main" / "java" / "com" / "senplay", "*.kt"),
}

# Kinds worth reporting: things with behaviour attached. A struct of pure data that
# only its own file builds is ordinary; a client or a controller is not.
INTERESTING = re.compile(
    r'^(?:(?:final |open |internal |public |private |sealed |abstract )*'
    r'(?:class|object|actor|struct|enum)\s+)([A-Z]\w+)'
)

# Declarations that are deliberately local: helpers, view bodies, small value types.
SKIP_NAMES = {
    "MainActivity", "App", "Entry", "Builder", "Companion",
}


def declared_types(path: pathlib.Path) -> set[str]:
    names: set[str] = set()
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if stripped.startswith("//") or stripped.startswith("*"):
            continue
        match = INTERESTING.match(stripped)
        if match:
            name = match.group(1)
            if name not in SKIP_NAMES and len(name) > 3:
                names.add(name)
    return names


def main() -> int:
    total = 0
    for platform, (base, pattern) in TARGETS.items():
        if not base.exists():
            continue
        files = sorted(base.rglob(pattern))
        texts = {path: path.read_text(encoding="utf-8") for path in files}

        orphans: list[tuple[str, str]] = []
        for path, text in texts.items():
            for name in declared_types(path):
                # A word-boundary count across every other file.
                pattern = re.compile(r"\b" + re.escape(name) + r"\b")
                others = sum(
                    1 for other, other_text in texts.items()
                    if other != path and pattern.search(other_text)
                )
                if others == 0:
                    orphans.append((path.relative_to(ROOT).as_posix(), name))

        print(f"=== {platform}: {len(orphans)} candidates ===")
        for where, name in sorted(orphans):
            print(f"  {name:<32} {where}")
        total += len(orphans)
        print()

    print(f"{total} candidates. Each one needs reading: a type mentioned only in its own")
    print("file is either dead, or used only by its own file's entry point, which is fine")
    print("for a helper and not fine for a client.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
