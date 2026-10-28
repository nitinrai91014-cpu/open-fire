#!/usr/bin/env python3
"""Static consistency checks for the Open Fire Android sources.

These checks mirror (and go beyond) what the JVM unit tests can see:

  1. Every sound key used anywhere in the Kotlin sources is declared in
     AudioManager.soundAliases, and every declared alias points at a file that
     really exists in app/src/main/assets/sounds.
  2. Every asset sound file is actually referenced by the alias table.
  3. Every translation key used through t("...") exists in the English table,
     and the English, Hindi and Nepali tables define exactly the same keys.
  4. Braces, parentheses and brackets balance in every Kotlin file and no
     Kotlin file mixes tabs into its indentation.
  5. Every sound asset really is an MP3 file: a well-formed ID3v2 tag (when
     present) followed by at least one MPEG audio frame. A truncated or
     non-audio asset (an HTML error page, a partial download, a Git LFS
     pointer) plays as silence on the device and is what makes ZIP tooling
     report bogus stream errors for that one file.
"""
import os
import re
import sys

ROOT = os.path.dirname(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
)
SOUNDS_DIR = os.path.join(ROOT, "app", "src", "main", "assets", "sounds")
MAIN = os.path.join(ROOT, "app", "src", "main", "java")
AUDIO = os.path.join(
    MAIN, "com", "blindtechnexus", "openfire", "audio", "AudioManager.kt"
)
STRINGS = os.path.join(
    MAIN, "com", "blindtechnexus", "openfire", "data", "Strings.kt"
)

failures = []


def fail(message):
    failures.append(message)


def kotlin_files():
    for base, _dirs, files in os.walk(os.path.join(ROOT, "app", "src")):
        for name in sorted(files):
            if name.endswith(".kt"):
                yield os.path.join(base, name)


def skip_string(src, i):
    """Return the index just past the string literal that starts at src[i]."""
    i += 1
    n = len(src)
    while i < n:
        c = src[i]
        if c == "\\":
            i += 2
            continue
        if c == '"':
            return i + 1
        if c == "$" and i + 1 < n and src[i + 1] == "{":
            # A template expression holds real code, so its braces must be
            # walked with the same nesting rules as normal code.
            depth = 1
            i += 2
            while i < n and depth:
                d = src[i]
                if d == "{":
                    depth += 1
                elif d == "}":
                    depth -= 1
                elif d == '"':
                    i = skip_string(src, i) - 1
                elif d == "\\":
                    i += 1
                i += 1
            continue
        i += 1
    return i


def strip_noise(text):
    """Remove comments, string literals and char literals for structure checks."""
    out = []
    i = 0
    n = len(text)
    while i < n:
        c = text[i]
        if c == "/" and i + 1 < n and text[i + 1] == "/":
            j = text.find("\n", i)
            i = n if j < 0 else j
            continue
        if c == "/" and i + 1 < n and text[i + 1] == "*":
            j = text.find("*/", i + 2)
            i = n if j < 0 else j + 2
            continue
        if text.startswith('"""', i):
            j = text.find('"""', i + 3)
            i = n if j < 0 else j + 3
            continue
        if c == '"':
            i = skip_string(text, i)
            continue
        if c == "'":
            j = i + 1
            if j < n and text[j] == "\\":
                j += 1
            j += 1
            if j < n and text[j] == "'":
                i = j + 1
                continue
            # A plain apostrophe inside prose, not a character literal.
            out.append(" ")
            i += 1
            continue
        out.append(c)
        i += 1
    return "".join(out)


# ---------------------------------------------------------------- 1 & 2: sounds
assets = set(os.listdir(SOUNDS_DIR))
audio_src = open(AUDIO, encoding="utf-8").read()

alias_block = audio_src.split("private val soundAliases = mapOf(", 1)[1]
alias_block = alias_block.split("\n    )", 1)[0]
aliases = {}
for key, files in re.findall(r'"([A-Za-z0-9]+)"\s+to\s+listOf\(([^)]*)\)', alias_block):
    aliases[key] = re.findall(r'"([^"]+)"', files)

if not aliases:
    fail("could not parse soundAliases from AudioManager.kt")

alias_keys = re.findall(r'"([A-Za-z0-9]+)"\s+to\s+listOf\(', alias_block)
for key in sorted(set(alias_keys)):
    if alias_keys.count(key) > 1:
        fail("sound key '%s' is declared more than once" % key)

for key, files in sorted(aliases.items()):
    for name in files:
        if name not in assets:
            fail("sound key '%s' points at missing asset sounds/%s" % (key, name))

referenced = set()
for files in aliases.values():
    referenced.update(files)
for name in sorted(assets):
    if name not in referenced:
        fail("asset sounds/%s is never referenced by soundAliases" % name)

# Every sound key used in the app must exist in the alias table.
used_keys = set()
for path in kotlin_files():
    if path == AUDIO:
        continue
    src = open(path, encoding="utf-8").read()
    for call in re.findall(
        r'\b(?:playSound|playLoop|stopSound|stopLoop)\(\s*"([^"]+)"', src
    ):
        used_keys.add((call, path))
    # Sound keys that come from the Weapon enum.
for path in kotlin_files():
    src = open(path, encoding="utf-8").read()
    for key in re.findall(
        r'(?:fireSoundKey|switchSoundKey|hitSoundKey|missSoundKey)\s*=\s*"([^"]+)"', src
    ):
        used_keys.add((key, path))

for key, path in sorted(used_keys):
    if key not in aliases:
        fail("sound key '%s' used in %s is not declared in soundAliases" % (key, path))

# ------------------------------------------------------------------- 3: strings
strings_src = open(STRINGS, encoding="utf-8").read()
tables = {}
for name in ("EN", "HI", "NE"):
    block = strings_src.split("private val %s = mapOf(" % name, 1)[1]
    block = block.split("\n    )", 1)[0]
    keys = re.findall(r'^\s{8}"([^"]+)" to ', block, flags=re.M)
    for key in sorted(set(keys)):
        if keys.count(key) > 1:
            fail("translation key '%s' is declared twice in %s" % (key, name))
    tables[name] = set(keys)
    if not tables[name]:
        fail("could not parse the %s string table" % name)

for lang in ("HI", "NE"):
    missing = tables["EN"] - tables[lang]
    extra = tables[lang] - tables["EN"]
    for key in sorted(missing):
        fail("missing %s translation for '%s'" % (lang, key))
    for key in sorted(extra):
        fail("extra %s translation for '%s'" % (lang, key))

for path in kotlin_files():
    src = open(path, encoding="utf-8").read()
    for key in re.findall(r'\bt\(\s*"([^"]+)"\s*\)', src):
        if key not in tables["EN"]:
            fail("translation key '%s' used in %s does not exist" % (key, path))
    for key in re.findall(r'(?:nameKey|pluralKey|descriptionKey)\s*=\s*"([^"]+)"', src):
        if key not in tables["EN"]:
            fail("translation key '%s' used in %s does not exist" % (key, path))

# ------------------------------------------------------------------ 4: structure
for path in kotlin_files():
    src = open(path, encoding="utf-8").read()
    clean = strip_noise(src)
    for open_ch, close_ch in (("{", "}"), ("(", ")"), ("[", "]")):
        if clean.count(open_ch) != clean.count(close_ch):
            fail(
                "%s: %s=%d but %s=%d"
                % (
                    os.path.relpath(path, ROOT),
                    open_ch,
                    clean.count(open_ch),
                    close_ch,
                    clean.count(close_ch),
                )
            )
    for number, line in enumerate(src.split("\n"), start=1):
        if line.startswith("\t") or "\t" in line[: len(line) - len(line.lstrip())]:
            fail("%s:%d uses tab indentation" % (os.path.relpath(path, ROOT), number))

# ------------------------------------------------------------- 5: MP3 assets
BITRATES = {
    (1, 1): [0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448],
    (1, 2): [0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384],
    (1, 3): [0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320],
    (2, 1): [0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256],
    (2, 2): [0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160],
    (2, 3): [0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160],
}
SAMPLE_RATES = {1: [44100, 48000, 32000], 2: [22050, 24000, 16000], 25: [11025, 12000, 8000]}


def id3v2_size(data):
    """Return the byte length of a leading ID3v2 tag, or 0 when there is none."""
    if len(data) < 10 or data[:3] != b"ID3":
        return 0
    major = data[3]
    size = (data[6] << 21) | (data[7] << 14) | (data[8] << 7) | data[9]
    footer = 10 if major >= 4 and data[5] & 0x10 else 0
    return 10 + size + footer


def mp3_frame_length(data, offset):
    """Return the length of the MPEG audio frame at offset, or 0 when there is none."""
    if offset + 4 > len(data):
        return 0
    header = data[offset : offset + 4]
    if header[0] != 0xFF or (header[1] & 0xE0) != 0xE0:
        return 0
    version = {3: 1, 2: 2, 0: 25}.get((header[1] >> 3) & 3)
    layer = {3: 1, 2: 2, 1: 3}.get((header[1] >> 1) & 3)
    bitrate_index = (header[2] >> 4) & 0xF
    rate_index = (header[2] >> 2) & 3
    if version is None or layer is None or bitrate_index in (0, 15) or rate_index == 3:
        return 0
    padding = (header[2] >> 1) & 1
    bitrate = BITRATES[(1 if version == 1 else 2, layer)][bitrate_index] * 1000
    rate = SAMPLE_RATES[version][rate_index]
    if layer == 1:
        return (12 * bitrate // rate + padding) * 4
    if layer == 3 and version != 1:
        return 72 * bitrate // rate + padding
    return 144 * bitrate // rate + padding


for name in sorted(assets):
    path = os.path.join(SOUNDS_DIR, name)
    with open(path, "rb") as handle:
        data = handle.read()
    if not data:
        fail("sound asset sounds/%s is empty" % name)
        continue
    tag_size = id3v2_size(data)
    if tag_size > len(data):
        fail("sound asset sounds/%s has an ID3v2 tag larger than the file" % name)
        continue
    if not tag_size and data[:3] != b"ID3" and not mp3_frame_length(data, 0):
        fail("sound asset sounds/%s does not start with an ID3v2 tag or an MPEG frame" % name)
        continue
    offset = mp3_frame_length(data, tag_size)
    if not offset:
        fail("sound asset sounds/%s contains no MPEG audio frame after its tag" % name)
        continue
    if tag_size + offset > len(data):
        fail("sound asset sounds/%s ends inside its first MPEG frame (truncated)" % name)

# ---------------------------------------------------------------------- report
print("sound keys declared : %d" % len(aliases))
print("sound assets present: %d" % len(assets))
print("translation keys    : EN=%d HI=%d NE=%d" % (
    len(tables["EN"]), len(tables["HI"]), len(tables["NE"])))

if failures:
    print("\nFAILURES (%d):" % len(failures))
    for message in failures:
        print("  - " + message)
    sys.exit(1)
print("\nAll static consistency checks passed.")
