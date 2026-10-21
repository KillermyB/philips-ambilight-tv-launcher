#!/usr/bin/env python3
"""Exercise timed changes, synchronized quotes, lifecycle pause and filters on a real TV."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument("serial")
parser.add_argument("--quick", action="store_true", help="Recheck controls and filters after a focused UI fix")
args = parser.parse_args()
ROOT = Path(__file__).resolve().parents[1]
QA = ROOT / ".local/qa-slideshow"
QA.mkdir(parents=True, exist_ok=True)
BASE = [str(ROOT / "tv.sh"), "-s", args.serial]
quotes = json.loads((ROOT / "app/src/main/assets/quotes.json").read_text())
passed = []
saved = None

def adb(*items):
    return subprocess.check_output(BASE + list(items), text=True, stderr=subprocess.STDOUT, timeout=40)

def key(name, count=1):
    for _ in range(count): adb("shell", "input", "keyevent", name)

def home():
    key("KEYCODE_WAKEUP"); key("KEYCODE_HOME"); key("KEYCODE_DPAD_UP"); key("KEYCODE_BACK", 2)

def screen(name):
    adb("shell", "rm", "-f", "/sdcard/atelier-cycle.xml")
    result = adb("shell", "uiautomator", "dump", "--compressed", "/sdcard/atelier-cycle.xml")
    if "dumped to:" not in result: raise RuntimeError(result)
    path = QA / (name + ".xml"); adb("pull", "/sdcard/atelier-cycle.xml", str(path))
    return ET.parse(path).getroot()

def tap(tree, label):
    n = next(n for n in tree.iter("node") if n.get("text", "").casefold() == label.casefold())
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
    adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))

def check(value, label):
    if not value: raise AssertionError(label)
    passed.append(label); print("PASS " + label, flush=True)

def http(address, token, path, values):
    request = urllib.request.Request(address + path, data=json.dumps(values).encode(),
        headers={"Content-Type": "application/json", "X-Atelier-Token": token}, method="POST")
    try:
        with urllib.request.urlopen(request, timeout=15) as response: return response.status, json.load(response)
    except urllib.error.HTTPError as error: return error.code, json.load(error)

def session():
    home(); key("KEYCODE_DPAD_UP", 2); key("KEYCODE_DPAD_RIGHT", 2); key("KEYCODE_DPAD_CENTER", 2)
    tree = screen("import")
    values = [n.get("text", "") + " " + n.get("content-desc", "") for n in tree.iter("node")]
    address = next(re.search(r"http://[0-9.:]+", v).group() for v in values if re.search(r"http://[0-9.:]+", v))
    pin = next(re.search(r"Code d’association (\d{6})", v).group(1) for v in values if re.search(r"Code d’association (\d{6})", v))
    code, unlocked = http(address, "", "/api/unlock", {"pin": pin}); assert code == 200
    token = unlocked["token"]
    code, library = http(address, token, "/api/library", {}); assert code == 200
    return address, token, library

def pair(name):
    tree = screen(name)
    values = [n.get("text", "") for n in tree.iter("node")]
    quote = next(q["text"] for q in quotes if q["text"] in values)
    caption = next(v for v in values if any(v.startswith(w["title"] + " · ") for w in works))
    return caption, quote


try:
    address, token, library = session(); works = library["works"]
    saved = {k: library[k] for k in ["home_slideshow", "home_shuffle", "home_personal", "home_seconds"]}
    selected = library["selected"]
    check(sum(not w["personal"] for w in works) == 50, "50 bundled paintings are available on the television")
    check(any(w["personal"] for w in works), "Previously imported personal paintings are preserved")
    code, _ = http(address, "", "/api/slideshow", {"home_seconds": 30})
    check(code == 403, "Slideshow controls require a paired browser")
    code, _ = http(address, token, "/api/slideshow", {"home_seconds": 1})
    check(code == 400, "Unsupported slideshow durations are rejected")
    code, _ = http(address, token, "/api/slideshow", {"home_slideshow": True, "home_shuffle": False, "home_personal": False, "home_seconds": 30})
    check(code == 200, "The slideshow can be configured from the paired browser")
    if not args.quick:
        key("KEYCODE_BACK"); before = pair("before-timer")
        print("Waiting for the 30-second home interval…", flush=True); time.sleep(32)
        after = pair("after-timer")
        check(before[0] != after[0] and before[1] != after[1], "The painting and its quotation both change automatically")
        i = next(i for i,q in enumerate(quotes) if q["text"] == before[1])
        check(after[1] == quotes[(i+1)%len(quotes)]["text"], "One new quotation accompanies one new painting")

        adb("shell", "am", "start", "-a", "android.settings.SETTINGS")
        print("Checking lifecycle pause outside the launcher…", flush=True); time.sleep(32)
        home(); check(pair("resumed") == after, "Slideshow pauses behind another application and resumes the same painting and quotation")

        address, token, _ = session()
        http(address, token, "/api/slideshow", {"home_slideshow": False})
        key("KEYCODE_BACK"); paused = pair("paused")
        print("Checking the user's pause setting…", flush=True); time.sleep(32)
        check(pair("still-paused") == paused, "Pause keeps both the painting and the quotation unchanged")

    address, token, _ = session()
    http(address, token, "/api/slideshow", {"home_personal": True, "home_slideshow": False})
    key("KEYCODE_BACK"); personal = pair("personal-filter")
    check(any(w["personal"] and personal[0].startswith(w["title"] + " · ") for w in works), "Personal-only mode displays an imported painting")
    address, token, _ = session()
    http(address, token, "/api/slideshow", {"home_personal": False, "home_shuffle": True})
    key("KEYCODE_BACK")
    seen = [pair("shuffle-start")[0]]
    for index in range(3):
        home(); key("KEYCODE_DPAD_UP", 2); key("KEYCODE_DPAD_RIGHT", 4); key("KEYCODE_DPAD_CENTER")
        tap(screen("settings"), "Diaporama de l’accueil")
        tap(screen("slideshow-options"), "Tableau suivant")
        seen.append(pair("manual-next-" + str(index))[0])
    check(len(set(seen)) == len(seen), "Next painting works from the remote menu without repeating a painting")

    adb("shell", "am", "force-stop", "ch.atelier.tv"); home()
    address, token, library = session()
    check(not library["home_slideshow"] and library["home_shuffle"], "Slideshow preferences survive application recreation")
finally:
    if saved is not None:
        address, token, _ = session()
        http(address, token, "/api/slideshow", saved)
        http(address, token, "/api/select", {"id": selected})
        key("KEYCODE_BACK"); home()
    (QA / ("results-quick.json" if args.quick else "results.json")).write_text(json.dumps({"passed": passed}, ensure_ascii=False, indent=2) + "\n")
