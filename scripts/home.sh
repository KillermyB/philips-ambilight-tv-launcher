#!/bin/sh
set -eu
TV_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
TV_SERIAL=${1:?Usage: scripts/home.sh IP:PORT atelier|google}
TV_TARGET=${2:-atelier}
case "$TV_TARGET" in
  atelier)
    "$TV_ROOT/tv.sh" -s "$TV_SERIAL" shell pm path ch.atelier.tv | rg -q '^package:'
    # This Philips firmware gives Google's HOME filter a higher system priority.
    # Android blocks shell changes to this system component individually.
    # Disable the Google home package for this user; do not uninstall it.
    "$TV_ROOT/tv.sh" -s "$TV_SERIAL" shell pm disable-user --user 0 com.google.android.tvlauncher
    "$TV_ROOT/tv.sh" -s "$TV_SERIAL" shell cmd package set-home-activity ch.atelier.tv/.MainActivity
    ;;
  google)
    "$TV_ROOT/tv.sh" -s "$TV_SERIAL" shell pm default-state --user 0 com.google.android.tvlauncher
    "$TV_ROOT/tv.sh" -s "$TV_SERIAL" shell cmd package set-home-activity com.google.android.tvlauncher/.MainActivity
    ;;
  *) printf '%s\n' 'Accueil inconnu : atelier ou google' >&2; exit 1 ;;
esac
"$TV_ROOT/tv.sh" -s "$TV_SERIAL" shell input keyevent KEYCODE_HOME
"$TV_ROOT/tv.sh" -s "$TV_SERIAL" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
