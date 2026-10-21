#!/bin/sh
set -eu

TV_PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
TV_ADB="$TV_PROJECT_DIR/.local/platform-tools/adb"

if [ ! -x "$TV_ADB" ]; then
  printf '%s\n' 'ADB manque : placer les Android SDK Platform Tools officiels dans .local/platform-tools.' >&2
  exit 1
fi

# Use a dedicated ADB server and connect only to explicitly selected devices.
export ADB_MDNS_AUTO_CONNECT=0
exec "$TV_ADB" -P 5038 "$@"
