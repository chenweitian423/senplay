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

IOS_DECL = re.compile(r'static let (\w+) = "(pref\.[^"]*)"')
# @AppStorage("pref.x") names a key inline rather than through PrefKey.
IOS_LITERAL = re.compile(r'@AppStorage\("([^"]+)"\)')
# UserDefaults read directly rather than through a property wrapper. The browser
# reads its display flags this way, and missing the pattern made keys that are
# plainly wired look untouched.
IOS_DIRECT = re.compile(
    r'(?:object|string|bool|integer|double|array|data)\(forKey:\s*(?:"([^"]+)"|(\w+))\)'
)
# A storage key is often named by a constant instead of written out at the call site:
# `private let sourcesKey = "iptv.sources"` … `data(forKey: sourcesKey)`. Reading only
# literals made both IPTV keys look unread on iOS while Android was credited with them.
IOS_STRING_CONST = re.compile(r'(?:static\s+|private\s+)*(?:let|var)\s+(\w+)\s*=\s*"([^"]+)"')
ANDROID_DECL = re.compile(r'private const val (P_\w+) = "([^"]+)"')
# A key can also be named inline at the call site, with no constant at all. Missing
# these made the audit report three times that fewer keys were dead than really were.
ANDROID_INLINE = re.compile(r'(?:set)?[Pp]ref(?:Bool|Int|Float|String)\("([^"]+)"')
ANDROID_ROW = re.compile(r'Pref(?:Switch|Choice|Slider|Text)\(\s*store,\s*"([^"]+)"')
# A property wrapper binding, with the variable it declares. Used to tell "reads the
# value and uses it" apart from "reads the value and never mentions it again".
IOS_BINDING = re.compile(
    r'@AppStorage\((?:PrefKey\.(\w+)|"([^"]+)")\)\s*(?:private\s+)?var\s+(\w+)'
)


def settings_surface() -> set[str]:
    """Keys a settings screen actually draws.

    Everything else in the storage namespace — the IPTV channel lists, the server
    list, the user agent — is user data. Android prefixes every constant it reads with
    "pref." so the two sides can be compared, which meant those data keys arrived at
    the report dressed as settings and showed up as a one-sided preference. Reporting
    only what a settings screen names keeps the list about settings.
    """
    keys: set[str] = set()
    name_to_key: dict[str, str] = {}
    for path in sorted((ROOT / "ios" / "SenPlay").glob("*.swift")):
        for name, key in IOS_DECL.findall(path.read_text(encoding="utf-8")):
            name_to_key.setdefault(name, key)

    settings_path = ROOT / "ios" / "SenPlay" / "Preferences.swift"
    text = settings_path.read_text(encoding="utf-8")
    keys |= set(IOS_LITERAL.findall(text))
    for line in text.splitlines():
        for name, key in name_to_key.items():
            if re.search(r"PrefKey\." + re.escape(name) + r"\b", line):
                keys.add(key)

    android_path = (ROOT / "android" / "app" / "src" / "main" / "java" / "com" /
                    "senplay" / "ui" / "SettingsScreen.kt")
    keys |= {"pref." + key for key in ANDROID_ROW.findall(android_path.read_text(encoding="utf-8"))}
    return keys


def _is_use(line: str, variable: str) -> bool:
    """Whether a line actually consults the bound value.

    Three lines mention the name without using the value, and all three made the
    first version of this check miss `rememberAudioTrack`: the key constant happens
    to carry the same name (`static let rememberAudioTrack = "pref.audio.rememberTrack"`),
    the binding itself names it, and every `PrefKey.<name>` reference is about the key
    rather than the value.
    """
    if not re.search(r"\b" + re.escape(variable) + r"\b", line):
        return False
    if re.match(r"\s*static let\b", line):
        return False
    if "@AppStorage" in line:
        return False
    if re.search(r"PrefKey\." + re.escape(variable) + r"\b", line):
        return False
    return True


def bound_but_unused(sources: list[pathlib.Path], name_to_key: dict[str, str]) -> dict[str, str]:
    """Keys whose binding exists but whose variable is never consulted.

    A binding really does read UserDefaults, which is why it counts as a live read —
    but a value nothing acts on is not a feature. This is how `rememberZoomSize` and
    both `rememberTrack` keys looked wired on iOS while no code used them. Only the
    declaration itself is ignored; `$name` counts as a use.
    """
    out: dict[str, str] = {}
    for path in sources:
        if path.name in IOS_SETTINGS_UI:
            continue
        text = path.read_text(encoding="utf-8")
        for constant, literal, variable in IOS_BINDING.findall(text):
            key = literal or name_to_key.get(constant, "")
            if not key:
                continue
            if not any(_is_use(line, variable) for line in text.splitlines()):
                out[key] = path.name
    return out


def scan(sources: list[pathlib.Path], decl_re: re.Pattern, settings_ui: set[str]) -> dict[str, set[str]]:
    """Map key -> files that reference it somewhere the value could be acted on."""
    name_to_key: dict[str, str] = {}
    declarations: dict[str, str] = {}  # key -> file holding the declaration

    for path in sources:
        for line in path.read_text(encoding="utf-8").splitlines():
            for name, key in decl_re.findall(line):
                name_to_key.setdefault(name, key)
                declarations.setdefault(key, path.name)

    # Keys are stored in their normalised form — "pref.x" — because that is what the
    # iOS side produces and what the two are compared on. Keeping the raw constant
    # text here meant every Android key failed to match its iOS counterpart, so the
    # report marked every row as Android-only regardless of what was wired.
    def normalised(key: str) -> str:
        return key if key.startswith("pref.") else "pref." + key

    live: dict[str, set[str]] = {}
    for path in sources:
        if path.name in settings_ui:
            continue
        # An @AppStorage literal outside the settings screen is a read: the property
        # wrapper fetches the value from UserDefaults wherever it is used. Treating it
        # as only a declaration made the report claim keys were dead that were wired.
        text = path.read_text(encoding="utf-8")
        for key in IOS_LITERAL.findall(text):
            live.setdefault(key, set()).add(path.name)
        constants = {name: value for name, value in IOS_STRING_CONST.findall(text)}
        for literal, identifier in IOS_DIRECT.findall(text):
            key = literal or constants.get(identifier, "")
            if key:
                live.setdefault(key, set()).add(path.name)
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
                    live.setdefault(normalised(key), set()).add(path.name)
    return live


def android_live() -> dict[str, set[str]]:
    base = ROOT / "android" / "app" / "src" / "main" / "java" / "com" / "senplay"
    sources = sorted(base.rglob("*.kt"))
    live = scan(sources, ANDROID_DECL, ANDROID_SETTINGS_UI)

    # Inline-named keys: declared by the call that reads them, so the read is the
    # declaration. They are live by definition.
    for path in sources:
        if path.name in ANDROID_SETTINGS_UI:
            continue
        for key in ANDROID_INLINE.findall(path.read_text(encoding="utf-8")):
            live.setdefault("pref." + key, set()).add(path.name)

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
    ios_live = scan(ios_sources, IOS_DECL, IOS_SETTINGS_UI)

    # Every key the settings screen offers, whether it names one through PrefKey or
    # as an @AppStorage literal. Without this the iOS side contributes nothing to the
    # report and every row comes out marked "android", which is how a whole column of
    # missing keys went unnoticed for several rounds.
    ios_declared: set[str] = set()
    for path in ios_sources:
        text = path.read_text(encoding="utf-8")
        for _, key in IOS_DECL.findall(text):
            ios_declared.add(key)
        if path.name in IOS_SETTINGS_UI:
            ios_declared |= set(IOS_LITERAL.findall(text))
            ios_declared |= set(IOS_DIRECT.findall(text))
    # A key declared but never read needs an entry on both sides to be compared.
    ios = {key: ios_live.get(key, set()) for key in ios_declared | set(ios_live)}

    android = android_live()

    # Only preference keys, and only the ones a settings screen could show. The
    # direct-read pattern also catches storage keys like "servers.v1", the user-agent
    # string, and the danmaku endpoint templates that are built per slot — none of
    # those is a setting, and leaving them in padded the report with rows nobody
    # expects to find in a settings screen. The templates are recognisable by the
    # interpolation they still carry.
    all_keys = sorted(
        k for k in set(ios) | set(android)
        if k.startswith("pref.") and "$" not in k and "\\(" not in k
    )
    surface = settings_surface()
    dead = [k for k in all_keys if k in surface and not ios.get(k) and not android.get(k)]

    # Keys one platform reads and the other does not. The dead list above is an
    # intersection, so a key that only one side wired appears nowhere at all: that is
    # how iOS's showStartTitle and showSkipTip stayed unread for rounds while the
    # report looked clean. Single-sided is not automatically wrong — SMB and SFTP are
    # Android-only on purpose — but it has to be a decision rather than an omission,
    # so it belongs in the output.
    one_sided = sorted(
        k for k in all_keys
        if k in surface and bool(ios.get(k)) != bool(android.get(k))
    )

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

    print()
    print(f"--- read on one platform only ({len(one_sided)}) ---")
    for key in one_sided:
        reader = "ios" if ios.get(key) else "android"
        silent = "android" if reader == "ios" else "ios"
        print(f"  {key:<46} {reader} reads it, {silent} does not")

    name_to_key: dict[str, str] = {}
    for path in ios_sources:
        for name, key in IOS_DECL.findall(path.read_text(encoding="utf-8")):
            name_to_key.setdefault(name, key)
    unused = bound_but_unused(ios_sources, name_to_key)

    print()
    print(f"--- iOS binding exists, the value is never used ({len(unused)}) ---")
    for key in sorted(unused):
        print(f"  {key:<46} {unused[key]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
