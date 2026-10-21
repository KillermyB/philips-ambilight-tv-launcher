#!/usr/bin/env python3
"""Verify the Philips screensaver through its supported native settings UI."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument("serial")
parser.add_argument("--idle", action="store_true")
args = parser.parse_args()
ROOT = Path(__file__).resolve().parents[1]
QA = ROOT / ".local" / "qa-v2"
QA.mkdir(parents=True, exist_ok=True)
BASE = [str(ROOT / "tv.sh"), "-s", args.serial]
passed = []

def adb(*items):
    return subprocess.check_output(BASE + list(items), text=True, stderr=subprocess.STDOUT, timeout=35)

def key(name, count=1):
    for _ in range(count): adb("shell", "input", "keyevent", name)

def check(value, message):
    if not value: raise AssertionError(message)
    passed.append(message)
    print("PASS " + message, flush=True)

def screen(name):
    remote = "/sdcard/atelier-settings-qa.xml"
    adb("shell", "rm", "-f", remote)
    result = adb("shell", "uiautomator", "dump", "--compressed", remote)
    if "dumped to:" not in result: raise RuntimeError(result)
    local = QA / (name + ".xml")
    adb("pull", remote, str(local))
    return ET.parse(local).getroot()

def tap_text(tree, value):
    node = next(n for n in tree.iter("node") if n.get("text", "").casefold() == value.casefold())
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))

def active():
    state = adb("shell", "dumpsys", "dreams")
    return bool(re.search(r"mCurrentDream=DreamRecord[^\n]*ArtDreamService", state)), state

def capture():
    # The DreamService binds before its artwork worker has decoded the bitmap.
    time.sleep(2)
    adb("shell", "screencap", "-p", "/sdcard/atelier-dream.png")
    adb("pull", "/sdcard/atelier-dream.png", str(ROOT / "dist" / "atelier-tv-screensaver.png"))

try:
    key("KEYCODE_WAKEUP")
    key("KEYCODE_HOME", 2)
    if args.idle:
        original = adb("shell", "settings", "get", "system", "screen_off_timeout").strip()
        try:
            adb("shell", "settings", "put", "system", "screen_off_timeout", "15000")
            # Do not send Home after changing the delay: that can immediately dismiss a new dream.
            automatic = False
            for _ in range(25):
                time.sleep(2)
                automatic, state = active()
                if automatic: break
            (QA / "idle-dream-state.txt").write_text(state)
            check(automatic, "Art screensaver starts automatically after inactivity")
            capture()
        finally:
            adb("shell", "settings", "put", "system", "screen_off_timeout", original)
            key("KEYCODE_HOME", 2)

    # Open the shortcut from Atelier itself, without privileged shell dream commands.
    key("KEYCODE_DPAD_UP")
    key("KEYCODE_BACK", 2)
    key("KEYCODE_DPAD_UP", 2)
    key("KEYCODE_DPAD_RIGHT", 4)
    key("KEYCODE_DPAD_CENTER")
    key("KEYCODE_DPAD_DOWN", 4)
    key("KEYCODE_DPAD_CENTER")
    tap_text(screen("dream-options"), "Délai de veille TV")
    tree = screen("philips-dream-settings")
    check(any("Atelier · Galerie d’art" in n.get("text", "") for n in tree.iter("node")), "Atelier shortcut opens the Philips screensaver settings")
    tap_text(tree, "Activer maintenant")
    running = False
    for _ in range(12):
        running, state = active()
        if running: break
        time.sleep(.4)
    check(running, "Native Start now launches Atelier ArtDreamService")
    (QA / "dream-state.txt").write_text(state)
    capture()
    key("KEYCODE_HOME", 2)
    running, _ = active()
    check(not running, "Home exits the art screensaver")
finally:
    (QA / "screensaver-results.json").write_text(json.dumps({"passed": passed}, ensure_ascii=False, indent=2) + "\n")
