#!/usr/bin/env python3
"""Install the pinned Android and Java tools locally; check official checksums."""
import hashlib
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
LOCAL = ROOT / ".local"
LOCAL.mkdir(exist_ok=True)
PACKAGES = [
    ("android-platform.zip", "https://dl.google.com/android/repository/platform-34-ext12_r01.zip",
     "sha1", "ba80ccbcc29b29f25ac926a08c0b2777f0bce842", "sdk/platforms"),
    ("android-build-tools.zip", "https://dl.google.com/android/repository/build-tools_r35_macosx.zip",
     "sha1", "93ab8ce91230e067b5add4bfa79919c52b27f072", "sdk/build-tools"),
    ("jdk.tar.gz", "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.20.1%2B1/OpenJDK17U-jdk_aarch64_mac_hotspot_17.0.20.1_1.tar.gz",
     "sha256", "196d13ba5f10414bef7f6a05a9b3f00edacb18ebacef2b99485db9e2ee18f0e8", "jdk"),
]

for name, url, algorithm, expected, folder in PACKAGES:
    archive = LOCAL / name
    if not archive.exists():
        subprocess.run(["curl", "--fail", "--location", "--connect-timeout", "10", "--max-time", "240", url, "-o", str(archive)], check=True)
    digest = hashlib.new(algorithm)
    with archive.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    if digest.hexdigest() != expected:
        raise SystemExit("Checksum incorrect : " + str(archive))
    destination = LOCAL / folder
    destination.mkdir(parents=True, exist_ok=True)
    if name.endswith(".zip"):
        with zipfile.ZipFile(archive) as package:
            for entry in package.infolist():
                target = (destination / entry.filename).resolve()
                if destination.resolve() not in target.parents and target != destination.resolve():
                    raise SystemExit("Archive path rejected: " + entry.filename)
            package.extractall(destination)
    else:
        subprocess.run(["tar", "-xzf", str(archive), "-C", str(destination), "--strip-components=1"], check=True)
    print(name + ": checksum vérifié, outil préparé", flush=True)

print("Prêt : ./build.sh")
