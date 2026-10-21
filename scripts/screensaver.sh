#!/bin/sh
set -eu
TV_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
TV_SERIAL=${1:?Usage: scripts/screensaver.sh IP:PORT atelier|restore|settings|stop}
TV_ACTION=${2:-atelier}
case "$TV_ACTION" in
  atelier)
    "$TV_ROOT/tv.sh" -s "$TV_SERIAL" shell settings put secure screensaver_components ch.atelier.tv/ch.atelier.tv.ArtDreamService
    "$TV_ROOT/tv.sh" -s "$TV_SERIAL" shell settings put secure screensaver_default_component ch.atelier.tv/.ArtDreamService
    "$TV_ROOT/tv.sh" -s "$TV_SERIAL" shell settings put secure screensaver_enabled 1
    "$TV_ROOT/tv.sh" -s "$TV_SERIAL" shell settings put secure screensaver_activate_on_sleep 1
    "$TV_ROOT/tv.sh" -s "$TV_SERIAL" shell settings put global stay_on_while_plugged_in 0
    ;;
  restore)
    TV_BACKUP="$TV_ROOT/.local/audits/2026-10-18-screensaver-before.txt"
    [ -f "$TV_BACKUP" ] || { printf '%s\n' 'Sauvegarde des réglages initiaux introuvable.' >&2; exit 1; }
    while IFS='=' read -r TV_KEY TV_VALUE; do
      case "$TV_KEY" in
        screensaver_components|screensaver_default_component|screensaver_enabled|screensaver_activate_on_sleep|screensaver_activate_on_dock)
          if [ "$TV_VALUE" = null ]; then
            "$TV_ROOT/tv.sh" -s "$TV_SERIAL" shell settings delete secure "$TV_KEY"
          else
            "$TV_ROOT/tv.sh" -s "$TV_SERIAL" shell settings put secure "$TV_KEY" "$TV_VALUE"
          fi ;;
      esac
    done < "$TV_BACKUP"
    TV_STAY_BACKUP="$TV_ROOT/.local/audits/2026-10-18-stay-awake-before.txt"
    if [ -f "$TV_STAY_BACKUP" ]; then
      TV_STAY=$(cat "$TV_STAY_BACKUP")
      "$TV_ROOT/tv.sh" -s "$TV_SERIAL" shell settings put global stay_on_while_plugged_in "$TV_STAY"
    fi
    ;;
  settings) "$TV_ROOT/tv.sh" -s "$TV_SERIAL" shell am start -n com.android.tv.settings/.device.display.daydream.DaydreamActivity ;;
  stop) "$TV_ROOT/tv.sh" -s "$TV_SERIAL" shell input keyevent KEYCODE_HOME ;;
  *) printf '%s\n' 'Action inconnue : atelier, restore, settings ou stop' >&2; exit 1 ;;
esac
