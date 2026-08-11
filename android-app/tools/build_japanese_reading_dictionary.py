#!/usr/bin/env python3
"""Build the compact Japanese reading table bundled by the Android app."""

from __future__ import annotations

import argparse
import csv
import hashlib
import io
import lzma
import re
import tarfile
from pathlib import Path


EXPECTED_SHA256 = "b62f527d881c504576baed9c6ef6561554658b175ce6ae0096a60307e49e3523"
MAGIC = "#KIGTTS-JA-READING-1"
HAN_PATTERN = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff]")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("archive", type=Path, help="mecab-ipadic-2.7.0-20070801.tar.gz")
    parser.add_argument("output", type=Path, help="destination .tsv.xz asset")
    parser.add_argument("--license-output", type=Path)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    archive_bytes = args.archive.read_bytes()
    digest = hashlib.sha256(archive_bytes).hexdigest()
    if digest != EXPECTED_SHA256:
        raise SystemExit(f"unexpected source SHA-256: {digest}")

    entries: dict[str, tuple[int, str]] = {}
    license_bytes: bytes | None = None
    with tarfile.open(fileobj=io.BytesIO(archive_bytes), mode="r:gz") as archive:
        for member in archive.getmembers():
            if member.isfile() and member.name.endswith("/COPYING"):
                license_bytes = archive.extractfile(member).read()
            if not member.isfile() or not member.name.endswith(".csv"):
                continue
            source = archive.extractfile(member)
            text = io.TextIOWrapper(source, encoding="euc_jp", newline="")
            for row in csv.reader(text):
                if len(row) < 13 or not HAN_PATTERN.search(row[0]) or row[11] in ("", "*"):
                    continue
                surface = row[0]
                candidate = (int(row[3]), row[11])
                previous = entries.get(surface)
                if previous is None or candidate[0] < previous[0]:
                    entries[surface] = candidate

    sorted_entries = sorted(entries.items())
    max_word_length = max(len(surface) for surface, _ in sorted_entries)
    header = f"{MAGIC}\t{len(sorted_entries)}\t{max_word_length}\n"
    body = header + "".join(
        f"{surface}\t{reading}\n" for surface, (_, reading) in sorted_entries
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
        if license_bytes is None:
            raise SystemExit("COPYING was not found in source archive")
        args.license_output.parent.mkdir(parents=True, exist_ok=True)
        args.license_output.write_bytes(license_bytes)
    print(f"entries={len(sorted_entries)} max_word_length={max_word_length}")
    print(f"output_bytes={args.output.stat().st_size} sha256={hashlib.sha256(args.output.read_bytes()).hexdigest()}")


if __name__ == "__main__":
    main()
