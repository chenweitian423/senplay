#!/usr/bin/env python3
"""Check that read sites agree with the settings screen about default values.

A default that disagrees with the picker is worse than no default at all: the user
sees one state in settings and gets another until they touch the control once. Round
17 found three of these by hand, all of them written as `?? true` from reflex rather
than looked up, so the comparison runs here instead.

The settings screen is the authority — it is what the user is looking at. Every other
mention of the same key must carry the same default.

Fails on any mismatch. A mismatch is a question — "which of these is right?" — and the
answer is not always the settings screen, so the fix is a judgement call. But it is
always a bug: the two cannot both be right, and one of them is what the user gets.

Both sides are read structurally rather than by a regex over the call text. iOS keys
resolve through the `PrefKey` enum, and Android controls are parsed by matching
parentheses because the settings screen spreads them over several lines and nests
calls in their arguments. Reading only inline literals and single-line calls skipped
67 iOS rows and 55 Android controls — so the check passed while four real
disagreements sat inside it.
"""

from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent

IOS_PREFS = ROOT / "ios" / "SenPlay" / "Preferences.swift"
ANDROID_PREFS = (
    ROOT / "android" / "app" / "src" / "main" / "java" / "com" / "senplay" / "ui" / "SettingsScreen.kt"
)


def literal_value(text: str):
    """A source literal as the value it stands for, or None.

    Handles the spellings the two platforms actually write: Swift's `true`/`false`,
    quoted strings and numbers, plus Kotlin's `200f` float suffix.
    """
    text = text.strip()
    if text in ("true", "false"):
        return text == "true"
    quoted = re.fullmatch(r'"([^"]*)"', text)
    if quoted:
        return quoted.group(1)
    body = text[:-1] if text[-1:] in ("f", "F") else text
    if re.fullmatch(r"-?\d+", body):
        return int(body)
    if re.fullmatch(r"-?\d+\.\d+", body):
        return float(body)
    return None


def pref_key_literals() -> dict[str, str]:
    """`PrefKey` member name -> stored key, from the enum in PlayerScreen.swift."""
    out: dict[str, str] = {}
    for path in (ROOT / "ios" / "SenPlay").glob("*.swift"):
        text = path.read_text(encoding="utf-8")
        for block in re.findall(r"enum PrefKey[^{]*\{(.*?)\n\}", text, re.S):
            out.update(re.findall(r'static let (\w+) = "([^"]+)"', block))
    return out


def ios_app_storage(text: str, names: dict[str, str]) -> list[tuple[str, object]]:
    """Every `@AppStorage` declaration in the text, as (key, default)."""
    out: list[tuple[str, object]] = []
    pattern = re.compile(
        r'@AppStorage\((?:PrefKey\.(\w+)|"([^"]+)")(?:[^)"]*)\)[^=]*=\s*([^/\n]+)'
    )
    for member, literal, value in pattern.findall(text):
        key = literal or names.get(member)
        parsed = literal_value(value.split("//")[0])
        if key and parsed is not None:
            out.append((key, parsed))
    return out


def call_arguments(text: str, open_index: int) -> str | None:
    """The argument text of the call whose '(' sits at open_index."""
    depth = 0
    for index in range(open_index, len(text)):
        if text[index] == "(":
            depth += 1
        elif text[index] == ")":
            depth -= 1
            if depth == 0:
                return text[open_index + 1:index]
    return None


def split_arguments(args: str) -> list[str]:
    """Top-level comma split, ignoring commas inside strings and nested calls."""
    out: list[str] = []
    depth = 0
    quote: str | None = None
    buf = ""
    for char in args:
        if quote:
            buf += char
            if char == quote:
                quote = None
            continue
        if char in "\"'":
            quote = char
            buf += char
            continue
        if char in "([":
            depth += 1
        elif char in ")]":
            depth -= 1
        if char == "," and depth == 0:
            out.append(buf)
            buf = ""
        else:
            buf += char
    out.append(buf)
    return [item.strip() for item in out]


def settings_defaults_ios() -> dict[str, object]:
    """The defaults the settings screen shows."""
    names = pref_key_literals()
    out: dict[str, object] = {}
    for key, value in ios_app_storage(IOS_PREFS.read_text(encoding="utf-8"), names):
        out[key if key.startswith("pref.") else "pref." + key] = value
    return out


def read_sites_ios() -> dict[str, set[tuple[str, object]]]:
    """Defaults used anywhere that is not the settings screen."""
    names = pref_key_literals()
    out: dict[str, set[tuple[str, object]]] = {}
    for path in sorted((ROOT / "ios" / "SenPlay").glob("*.swift")):
        if path.name == IOS_PREFS.name:
            continue
        text = path.read_text(encoding="utf-8")
        for key, value in ios_app_storage(text, names):
            out.setdefault(key if key.startswith("pref.") else "pref." + key, set()).add(
                (path.name, value)
            )
        for key, value in re.findall(
            r'object\(forKey:\s*"([^"]+)"\)\s*as\?\s*\w+\s*\?\?\s*([^/\n]+)', text
        ):
            parsed = literal_value(value)
            if parsed is not None:
                out.setdefault(key, set()).add((path.name, parsed))
        for key, value in re.findall(
            r'string\(forKey:\s*"([^"]+)"\)\s*\?\?\s*"([^"]*)"', text
        ):
            out.setdefault(key, set()).add((path.name, value))
    return out


def settings_defaults_android() -> dict[str, object]:
    """The defaults the settings screen shows."""
    out: dict[str, object] = {}
    text = ANDROID_PREFS.read_text(encoding="utf-8")
    for match in re.finditer(r"Pref(IntChoice|Switch|Choice|Slider|Text)\(", text):
        kind = match.group(1)
        args = call_arguments(text, match.end() - 1)
        if args is None:
            continue
        parts = split_arguments(args)
        if len(parts) < 3:
            continue
        key = literal_value(parts[1])
        if not isinstance(key, str) or key == "store":
            continue  # the function declaration, not a call to it
        # PrefSlider ends with the unit, so its default is the second from the end.
        value = literal_value(parts[-2] if kind == "Slider" else parts[-1])
        if value is not None:
            out["pref." + key] = value
    return out


def read_sites_android() -> dict[str, set[tuple[str, object]]]:
    out: dict[str, set[tuple[str, object]]] = {}
    base = ROOT / "android" / "app" / "src" / "main" / "java" / "com" / "senplay"
    for path in sorted(base.rglob("*.kt")):
        if path.name == ANDROID_PREFS.name:
            continue
        text = path.read_text(encoding="utf-8")
        for match in re.finditer(r"pref(Bool|Int|Float|String)\(", text):
            args = call_arguments(text, match.end() - 1)
            if args is None:
                continue
            parts = split_arguments(args)
            if len(parts) < 2:
                continue
            key = literal_value(parts[0])
            value = literal_value(parts[-1])
            if isinstance(key, str) and value is not None:
                out.setdefault("pref." + key, set()).add((path.name, value))
    return out


def report(platform: str, settings: dict, sites: dict) -> int:
    mismatches = 0
    for key in sorted(set(settings) & set(sites)):
        expected = settings[key]
        for where, actual in sorted(sites[key]):
            if actual != expected:
                mismatches += 1
                print(f"  {platform} {key}")
                print(f"      settings screen: {expected!r}")
                print(f"      {where}: {actual!r}")
    return mismatches


def main() -> int:
    ios_settings = settings_defaults_ios()
    android_settings = settings_defaults_android()

    print(f"iOS settings defaults:     {len(ios_settings)}")
    print(f"Android settings defaults: {len(android_settings)}")
    print()

    ios_bad = report("ios", ios_settings, read_sites_ios())
    android_bad = report("android", android_settings, read_sites_android())

    print()
    print(f"mismatches: iOS {ios_bad}, Android {android_bad}")
    print("(a mismatch means one of the two is wrong; the settings screen is only")
    print(" the more visible of them, not automatically the correct one)")
    return 1 if (ios_bad or android_bad) else 0


if __name__ == "__main__":
    sys.exit(main())
