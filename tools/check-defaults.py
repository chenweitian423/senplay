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
"""

from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent

IOS_PREFS = ROOT / "ios" / "SenPlay" / "Preferences.swift"
ANDROID_PREFS = ROOT / "android" / "app" / "src" / "main" / "java" / "com" / "senplay" / "ui" / "SettingsScreen.kt"

def swift_value(text: str):
    text = text.strip()
    if text in ("true", "false"):
        return text == "true"
    if re.fullmatch(r"-?\d+(\.\d+)?", text):
        return float(text) if "." in text else int(text)
    m = re.fullmatch(r'"([^"]*)"', text)
    if m:
        return m.group(1)
    return None


def settings_defaults_ios() -> dict[str, object]:
    """The defaults the settings screen shows."""
    out: dict[str, object] = {}
    text = IOS_PREFS.read_text(encoding="utf-8")
    for key, value in re.findall(r'@AppStorage\("([^"]+)"\)[^=]*=\s*([^/\n]+)', text):
        parsed = swift_value(value.split("//")[0])
        if parsed is not None:
            out[key] = parsed
    return out


def read_sites_ios() -> dict[str, set[tuple[str, object]]]:
    """Defaults used anywhere that is not the settings screen."""
    out: dict[str, set[tuple[str, object]]] = {}
    for path in sorted((ROOT / "ios" / "SenPlay").glob("*.swift")):
        if path.name == IOS_PREFS.name:
            continue
        text = path.read_text(encoding="utf-8")
        for key, value in re.findall(r'@AppStorage\("([^"]+)"\)[^=]*=\s*([^/\n]+)', text):
            parsed = swift_value(value.split("//")[0])
            if parsed is not None:
                out.setdefault(key, set()).add((path.name, parsed))
        for key, value in re.findall(
            r'object\(forKey:\s*"([^"]+)"\)\s*as\?\s*\w+\s*\?\?\s*([^/\n]+)', text
        ):
            parsed = swift_value(value)
            if parsed is not None:
                out.setdefault(key, set()).add((path.name, parsed))
        for key, value in re.findall(
            r'string\(forKey:\s*"([^"]+)"\)\s*\?\?\s*"([^"]*)"', text
        ):
            out.setdefault(key, set()).add((path.name, value))
    return out


def settings_defaults_android() -> dict[str, object]:
    out: dict[str, object] = {}
    text = ANDROID_PREFS.read_text(encoding="utf-8")
    pattern = re.compile(
        r'Pref(?:Switch|Choice|Slider|Text)\(\s*store,\s*"([^"]+)",\s*"[^"]*"(?:,\s*(?:"[^"]*"|null))?,'
        r'\s*(?:\w+\s*)?,\s*([^)\n]+)'
    )
    for key, value in pattern.findall(text):
        parsed = swift_value(value.strip().rstrip(','))
        if parsed is not None:
            out["pref." + key] = parsed
    return out


def read_sites_android() -> dict[str, set[tuple[str, object]]]:
    out: dict[str, set[tuple[str, object]]] = {}
    base = ROOT / "android" / "app" / "src" / "main" / "java" / "com" / "senplay"
    for path in sorted(base.rglob("*.kt")):
        if path.name == ANDROID_PREFS.name:
            continue
        text = path.read_text(encoding="utf-8")
        for key, value in re.findall(
            r'pref(?:Bool|Int|Float|String)\("([^"]+)",\s*([^)]+)\)', text
        ):
            parsed = swift_value(value.strip().rstrip(','))
            if parsed is not None:
                out.setdefault("pref." + key, set()).add((path.name, parsed))
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
