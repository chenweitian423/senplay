#!/usr/bin/env python3
"""Fail if the two platforms disagree about preference keys.

The project promises that a profile written on one platform is readable on the
other, which only holds while both sides use the same strings. iOS keeps them in a
`PrefKey` enum (plus a few `@AppStorage` literals); Android keeps them in
`private const val P_*` and in the settings screen's literals.

Exit code is 0 when the key sets match and 1 otherwise, so this can gate CI.
"""

from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent

# Keys that are legitimate on one side only, with the reason.
ALLOWED_ANDROID_ONLY = {
    # Slots are addressed by index at runtime: pref.danmaku.endpoint.1 … .5
    "pref.danmaku.endpoint.$index",
}
ALLOWED_IOS_ONLY: set[str] = set()


def ios_keys() -> set[str]:
    keys: set[str] = set()
    for path in (ROOT / "ios" / "SenPlay").glob("*.swift"):
        text = path.read_text(encoding="utf-8")
        blocks = re.findall(r"enum PrefKey[^{]*\{(.*?)\n\}", text, re.S)
        for block in blocks:
            keys |= set(re.findall(r'static let \w+ = "([^"]+)"', block))
        keys |= set(re.findall(r'@AppStorage\("([^"]+)"\)', text))
    return {key for key in keys if key.startswith("pref.")}


def android_keys() -> set[str]:
    keys: set[str] = set()
    base = ROOT / "android" / "app" / "src" / "main" / "java" / "com" / "senplay"
    for path in base.rglob("*.kt"):
        text = path.read_text(encoding="utf-8")
        keys |= {k for k in re.findall(r'private const val P_\w+ = "([^"]+)"', text)
                 if k.startswith("pref.")}
        keys |= {k for k in re.findall(r'"([^"]+)"', text) if k.startswith("pref.")}
    # The bare placeholder inside a doc comment is not a key.
    keys.discard("pref.")
    keys.discard("pref.danmaku.endpoint.N")
    return keys


def main() -> int:
    ios = ios_keys()
    android = android_keys()

    missing = sorted(ios - android - ALLOWED_ANDROID_ONLY)
    extra = sorted(android - ios - ALLOWED_ANDROID_ONLY)

    print(f"iOS preference keys:     {len(ios)}")
    print(f"Android preference keys: {len(android)}")
    print(f"shared:                  {len(ios & android)}")

    if not missing and not extra:
        print("\nOK: both platforms agree on every preference key.")
        return 0

    if missing:
        print(f"\niOS declares {len(missing)} key(s) Android never reads:")
        for key in missing:
            print("  ", key)
    if extra:
        print(f"\nAndroid declares {len(extra)} key(s) iOS never reads:")
        for key in extra:
            print("  ", key)
    return 1


if __name__ == "__main__":
    sys.exit(main())
