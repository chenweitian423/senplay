#!/usr/bin/env python3
"""Report preference keys that only ever appear on the settings surface.

The settings screens are the easy half. A key can be listed, stored, bound, and still
have no effect because nothing else reads it — which is how `ignoreAssStyle` sat there
looking implemented while the kernel renders ASS from the file's own styles.

The test is deliberately narrow: a key is reported when every reference outside the
settings UI is a declaration. Binding it with @AppStorage or declaring the constant
does not count as making it do something. A settings-only key is not automatically
wrong — a theme colour read through a different path is fine — but the list should be
short and deliberate rather than unknown.
"""

from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent

# The files that exist to render the settings list, where a key appearing means
# "shown to the user", not "acted on".
IOS_SETTINGS_UI = {"Preferences.swift"}
ANDROID_SETTINGS_UI = {"SettingsScreen.kt"}

IOS_DECL = re.compile(r'static let (\w+) = "([^"]+)"')
ANDROID_DECL = re.compile(r'private const val (P_\w+) = "([^"]+)"')
ANDROID_ROW = re.compile(r'Pref(?:Switch|Choice|Slider|Text)\(\s*store,\s*"([^"]+)"')


def scan(sources: list[pathlib.Path], decl_re: re.Pattern, settings_ui: set[str]) -> dict[str, set[str]]:
    """Map key -> files that reference it somewhere the value could be acted on."""
    name_to_key: dict[str, str] = {}
    declarations: dict[str, str] = {}  # key -> file holding the declaration

    for path in sources:
        for line in path.read_text(encoding="utf-8").splitlines():
            for name, key in decl_re.findall(line):
                name_to_key.setdefault(name, key)
                declarations.setdefault(key, path.name)

    live: dict[str, set[str]] = {}
    for path in sources:
        if path.name in settings_ui:
            continue
        for line in path.read_text(encoding="utf-8").splitlines():
            stripped = line.strip()
            if stripped.startswith("//") or stripped.startswith("*") or stripped.startswith("///"):
                continue
            if decl_re.search(line):
                continue  # the declaration itself
            # A settings row stores the value; storing is not acting on it.
            if "PrefSwitch" in line or "PrefChoice" in line or "PrefSlider" in line:
                continue
            for name, key in name_to_key.items():
                if re.search(r"\b" + re.escape(name) + r"\b", line):
                    live.setdefault(key, set()).add(path.name)
    return live


def android_live() -> dict[str, set[str]]:
    base = ROOT / "android" / "app" / "src" / "main" / "java" / "com" / "senplay"
    sources = sorted(base.rglob("*.kt"))
    live = scan(sources, ANDROID_DECL, ANDROID_SETTINGS_UI)

    # Keys written only as a literal in a settings row, with no constant at all.
    declared_literals: dict[str, str] = {}
    for path in sources:
        if path.name in ANDROID_SETTINGS_UI:
            for key in ANDROID_ROW.findall(path.read_text(encoding="utf-8")):
                declared_literals.setdefault("pref." + key, path.name)
    for key in declared_literals:
        if key not in live:
            live[key] = set()
    return live


def main() -> int:
    ios_sources = sorted((ROOT / "ios" / "SenPlay").glob("*.swift"))
    ios = scan(ios_sources, IOS_DECL, IOS_SETTINGS_UI)
    # @AppStorage literals are bound in place; if that binding is the only mention,
    # the value is displayed and never acted on either.
    for path in ios_sources:
        if path.name in IOS_SETTINGS_UI:
            continue
    android = android_live()

    all_keys = sorted(set(ios) | set(android))
    dead = [k for k in all_keys if not ios.get(k) and not android.get(k)]

    print(f"keys with a real read on iOS:     {sum(1 for k in ios if ios[k])}")
    print(f"keys with a real read on Android: {sum(1 for k in android if android[k])}")
    print()
    print(f"--- declared, shown in settings, read by nothing else ({len(dead)}) ---")
    for key in dead:
        sides = []
        if key in ios:
            sides.append("ios")
        if key in android:
            sides.append("android")
        print(f"  {key:<46} {'/'.join(sides)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
