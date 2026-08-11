#!/usr/bin/env python3
"""Build the compact Mandarin reading table bundled by the Android app."""

from __future__ import annotations

import argparse
import hashlib
import json
import lzma
import unicodedata
from pathlib import Path


MAGIC = "#KIGTTS-ZH-PINYIN-1"
EXPECTED_SINGLE_SHA256 = "5f294c01e6c6c0a1c8e329c79335a3f8e0b27d06bf1de7a99244b765892d1e5b"
EXPECTED_PHRASE_SHA256 = "a45ff140a6b631ca9c82127b280a2f414e0aba6bb2824a0e9d1e77fff359c665"
EXPECTED_LICENSE_SHA256 = "1e6c90014b4912815c296ee64bb6f6280af47e6d4c5d80e86232dfc5defe764c"
TONE_MARKS = {
    "\u0304": 1,
    "\u0301": 2,
    "\u030c": 3,
    "\u0300": 4,
}
CUSTOM_PHRASES = {
    "背着": "bei1 zhe5",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("source_root", type=Path, help="extracted pypinyin 0.55.0 wheel")
    parser.add_argument("output", type=Path, help="destination .tsv.xz asset")
    parser.add_argument("--license-output", type=Path)
    return parser.parse_args()


def checked_bytes(path: Path, expected_sha256: str) -> bytes:
    content = path.read_bytes()
    digest = hashlib.sha256(content).hexdigest()
    if digest != expected_sha256:
        raise SystemExit(f"unexpected SHA-256 for {path.name}: {digest}")
    return content


def to_tone3(syllable: str) -> str:
    normalized = unicodedata.normalize("NFD", syllable.strip().lower())
    if not normalized:
        return ""
    if normalized[-1].isdigit():
        return normalized
    output: list[str] = []
    tone = 5
    for char in normalized:
        if char in TONE_MARKS:
            tone = TONE_MARKS[char]
        elif char == "\u0308":
            if output and output[-1] == "u":
                output[-1] = "v"
        elif unicodedata.category(char) != "Mn":
            output.append(char)
    return "".join(output) + str(tone)


def main() -> None:
    args = parse_args()
    package_root = args.source_root / "pypinyin"
    single_data = json.loads(
        checked_bytes(package_root / "pinyin_dict.json", EXPECTED_SINGLE_SHA256)
    )
    phrase_data = json.loads(
        checked_bytes(package_root / "phrases_dict.json", EXPECTED_PHRASE_SHA256)
    )

    entries: dict[str, str] = {}
    for code_point, readings in single_data.items():
        surface = chr(int(code_point))
        reading = to_tone3(readings.split(",", 1)[0])
        if reading:
            entries[surface] = reading

    for surface, syllables in phrase_data.items():
        if len(surface) < 2 or not all("\u3400" <= char <= "\u9fff" or "\uf900" <= char <= "\ufaff" for char in surface):
            continue
        reading = " ".join(to_tone3(options[0]) for options in syllables if options)
        if reading and len(reading.split()) == len(surface):
            entries[surface] = reading

    entries.update(CUSTOM_PHRASES)
    sorted_entries = sorted(entries.items())
    max_word_length = max(len(surface) for surface, _ in sorted_entries)
    body = (
        f"{MAGIC}\t{len(sorted_entries)}\t{max_word_length}\n"
        + "".join(f"{surface}\t{reading}\n" for surface, reading in sorted_entries)
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(
        lzma.compress(
            body.encode("utf-8"),
            format=lzma.FORMAT_XZ,
            preset=9 | lzma.PRESET_EXTREME,
        )
    )

    if args.license_output is not None:
        license_candidates = list(args.source_root.glob("pypinyin-*.dist-info/licenses/LICENSE.txt"))
        if len(license_candidates) != 1:
            raise SystemExit("pypinyin license file was not found")
        license_bytes = checked_bytes(license_candidates[0], EXPECTED_LICENSE_SHA256)
        args.license_output.parent.mkdir(parents=True, exist_ok=True)
        args.license_output.write_bytes(license_bytes)

    output_bytes = args.output.read_bytes()
    print(f"entries={len(sorted_entries)} max_word_length={max_word_length}")
    print(
        f"output_bytes={len(output_bytes)} "
        f"sha256={hashlib.sha256(output_bytes).hexdigest()}"
    )


if __name__ == "__main__":
    main()
