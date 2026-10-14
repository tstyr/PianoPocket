#!/usr/bin/env python3
"""Generate the static updater manifest for the tested ARM64 APK."""
import argparse
import hashlib
import json
import pathlib
import re

parser = argparse.ArgumentParser()
parser.add_argument("--apk", type=pathlib.Path, required=True)
parser.add_argument("--notes", type=pathlib.Path, required=True)
parser.add_argument("--output", type=pathlib.Path, required=True)
args = parser.parse_args()
root = pathlib.Path(__file__).resolve().parents[1]
gradle = (root / "app/build.gradle.kts").read_text()
version = re.search(r'versionName\s*=\s*"([\w.-]+)"', gradle)[1]
code = int(re.search(r"versionCode\s*=\s*(\d+)", gradle)[1])
sdk = int(re.search(r"minSdk\s*=\s*(\d+)", gradle)[1])
name = "PianoPocket-" + version.removesuffix("-alpha") + "-arm64.apk"
assert args.apk.name == name
notes = args.notes.read_text().strip()
assert len(notes) <= 8000
size = args.apk.stat().st_size
assert 1024 <= size <= 128 * 1024 * 1024
sha = hashlib.sha256()
with args.apk.open("rb") as source:
    for block in iter(lambda: source.read(1024 * 1024), b""):
        sha.update(block)
manifest = dict(schema=1, packageName="dev.pianopocket", abi="arm64-v8a", apk=name,
                versionCode=code, versionName=version, minSdk=sdk, bytes=size,
                sha256=sha.hexdigest(), notes=notes)
data = (json.dumps(manifest, ensure_ascii=False, indent=2) + "\n").encode()
assert len(data) <= 16384
args.output.write_bytes(data)
print(f"{version}: {size} bytes / {sha.hexdigest()}")
