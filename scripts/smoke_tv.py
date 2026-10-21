#!/usr/bin/env python3
"""Verify the installed launcher, gallery, LAN import, widgets and real DreamService."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument("serial")
parser.add_argument("--image", type=Path)
parser.add_argument("--title", default="Mon tableau personnel")
parser.add_argument("--artist", default="Collection personnelle")
parser.add_argument("--idle", action="store_true", help="Temporarily use a 15-second idle delay and restore it afterward")
args = parser.parse_args()
ROOT = Path(__file__).resolve().parents[1]
BASE = [str(ROOT / "tv.sh"), "-s", args.serial]
QA = ROOT / ".local" / "qa-v2"
QA.mkdir(parents=True, exist_ok=True)
passed = []

def adb(*values):
    return subprocess.check_output(BASE + list(values), text=True, stderr=subprocess.STDOUT, timeout=35)

def key(value, count=1):
    for _ in range(count):
        adb("shell", "input", "keyevent", value)

def screen(name):
    adb("shell", "rm", "-f", "/sdcard/atelier-ui.xml")
    result = adb("shell", "uiautomator", "dump", "--compressed", "/sdcard/atelier-ui.xml")
    if "dumped to:" not in result: raise RuntimeError(result)
    output = QA / (name + ".xml")
    adb("pull", "/sdcard/atelier-ui.xml", str(output))
    return ET.parse(output).getroot()

def check(value, description):
    if not value:
        raise AssertionError(description)
    passed.append(description)
    print("PASS " + description, flush=True)

def focus(tree):
    for node in tree.iter("node"):
        if node.get("focused") == "true":
            return node.get("content-desc") or node.get("text") or ""
    return ""

def has(tree, value):
    return any(value in (n.get("text", "") + n.get("content-desc", "")) for n in tree.iter("node"))

def wait_foreground(value):
    for _ in range(16):
        state = adb("shell", "dumpsys", "activity", "activities")
        lines = [line for line in state.splitlines() if "topResumedActivity=" in line or "ResumedActivity:" in line]
        if any(value in line for line in lines):
            return True
        time.sleep(.3)
    return False

def home():
    key("KEYCODE_WAKEUP")
    key("KEYCODE_HOME")
    if not wait_foreground("ch.atelier.tv/.MainActivity"):
        raise AssertionError("Home did not open Atelier TV")
    # Android may preserve focus when Home is already foreground. Back closes
    # any modal/panel, then returns focus to the first favourite for each route.
    key("KEYCODE_DPAD_UP")  # Leave Android touch mode after a dialog button tap.
    key("KEYCODE_BACK", 2)

def open_import():
    home()
    key("KEYCODE_DPAD_UP", 2)
    key("KEYCODE_DPAD_RIGHT", 2)
    key("KEYCODE_DPAD_CENTER")
    check(has(screen("gallery"), "Votre galerie"), "Gallery opens from the remote")
    key("KEYCODE_DPAD_CENTER")
    tree = screen("import")
    values = [n.get("text", "") + " " + n.get("content-desc", "") for n in tree.iter("node")]
    address = next(re.search(r"http://[0-9.:]+", v).group() for v in values if re.search(r"http://[0-9.:]+", v))
    pin = next(re.search(r"Code d’association (\d{6})", v).group(1) for v in values if re.search(r"Code d’association (\d{6})", v))
    return address, pin

def http(address, path, data=None, token="", headers=None):
    fields = {"X-Atelier-Token": token, **(headers or {})}
    if not isinstance(data, bytes):
        fields["Content-Type"] = "application/json"
        data = json.dumps(data or {}).encode()
    request = urllib.request.Request(address + path, data=data, headers=fields, method="POST")
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, json.load(error)

try:
    home()
    tree = screen("home")
    cards = [n for n in tree.iter("node") if ". Appui long pour les favoris." in n.get("content-desc", "")]
    names = [n.get("content-desc", "").split(".")[0] for n in cards]
    check(names[0] == "YouTube" and names[2] == "Spotify", "YouTube is first and Spotify is third")
    check(focus(tree).startswith("YouTube."), "Home focuses the first application")
    quotes = json.loads((ROOT / "app/src/main/assets/quotes.json").read_text())
    check(any(has(tree, q["text"]) and has(tree, q["author"]) for q in quotes), "Attributed stoic quotation is visible")
    check(not any(n.get("text") in names for n in tree.iter("node")), "No duplicate application names below the cards")
    check(not any(any(c in n.get("text", "") for c in "↗←→▶") for n in tree.iter("node")), "No decorative navigation arrows on home")
    seen_quotes = set()
    for index in range(3):
        home()
        current = screen("quote-" + str(index))
        seen_quotes.update(q["text"] for q in quotes if has(current, q["text"]))
        key("KEYCODE_DPAD_DOWN")
        key("KEYCODE_DPAD_RIGHT")
        key("KEYCODE_DPAD_CENTER")
        dialog = screen("quote-selector-" + str(index))
        target = next(n for n in dialog.iter("node") if n.get("text", "").casefold() == "autre citation")
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", target.get("bounds")))
        adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    check(len(seen_quotes) == 3, "Three distinct sourced quotations can be selected from Widgets")
    home()
    for node in cards:
        values = list(map(int, re.findall(r"\d+", node.get("bounds"))))
        ratio = (values[2] - values[0]) / (values[3] - values[1])
        check(abs(ratio - 16 / 9) < .09, "16:9 card ratio: " + node.get("content-desc").split(".")[0])
    key("KEYCODE_DPAD_RIGHT", 2)
    check(focus(screen("spotify")).startswith("Spotify."), "D-pad reaches Spotify in third position")
    home()
    key("KEYCODE_DPAD_CENTER")
    check(wait_foreground("com.google.android.youtube.tv/"), "OK opens the real YouTube application")
    home()
    check(wait_foreground("ch.atelier.tv/.MainActivity"), "Home returns from YouTube to Atelier TV")
    key("KEYCODE_DPAD_UP", 2)
    key("KEYCODE_DPAD_RIGHT")
    key("KEYCODE_DPAD_CENTER")
    check(has(screen("apps"), "Toutes vos applications"), "All-apps drawer opens")
    key("KEYCODE_DPAD_UP")
    check(focus(screen("drawer-focus")) == "Fermer  ×", "Focus stays inside the application drawer")
    key("KEYCODE_BACK")

    address, pin = open_import()
    with urllib.request.urlopen(address, timeout=10) as page:
        check("Un écran." in page.read().decode(), "Local artwork transfer page is served")
    status, _ = http(address, "/api/widgets", {"note": "unauthorized"})
    check(status == 403, "Upload session rejects unauthenticated changes")
    status, _ = http(address, "/api/unlock", {"pin": pin}, headers={"Origin": "http://example.invalid"})
    check(status == 403, "Upload session rejects a foreign browser origin")
    status, _ = http(address, "/api/unlock", {"pin": "000000" if pin != "000000" else "111111"})
    check(status == 403, "Incorrect pairing code is rejected")
    status, value = http(address, "/api/unlock", {"pin": pin})
    check(status == 200 and bool(value.get("token")), "TV pairing code unlocks the local browser")
    token = value["token"]
    _, original = http(address, "/api/library", {}, token)
    widget_keys = ["note", "widget_quote", "widget_note", "widget_clock", "widget_date", "widget_art"]
    saved_widgets = {name: original[name] for name in widget_keys}
    status, _ = http(address, "/api/artwork", b"not an image", token)
    check(status == 400, "Non-image uploads are rejected")
    status, _ = http(address, "/api/artwork", b"{}", token, {"Content-Length": str(20 * 1024 * 1024 + 1)})
    check(status == 413, "Images above the upload limit are rejected before reading")
    if args.image:
        status, value = http(address, "/api/artwork", args.image.read_bytes(), token, {
            "Content-Type": "image/jpeg", "X-Art-Title": urllib.parse.quote(args.title), "X-Art-Artist": urllib.parse.quote(args.artist)})
        check(status == 200 and value.get("title") == args.title, "A real personal painting is uploaded and stored on the TV")
        _, library = http(address, "/api/library", {}, token)
        check(any(x["id"] == value["id"] and x["personal"] for x in library["works"]), "Personal painting appears in the persistent gallery")
    status, _ = http(address, "/api/widgets", {"note": "Mon espace, mes tableaux.", "widget_note": True}, token)
    check(status == 200, "Custom message widget is saved from the browser")
    key("KEYCODE_BACK")
    tree = screen("personal-home")
    check(has(tree, "Mon espace, mes tableaux."), "Custom message is visible on the native TV home")
    if args.image:
        check(has(tree, args.title), "Uploaded painting becomes the home background")
    try:
        urllib.request.urlopen(address, timeout=2)
        raise AssertionError("Transfer server remained open")
    except urllib.error.URLError:
        check(True, "Transfer server closes when its TV screen is closed")
    adb("shell", "am", "force-stop", "ch.atelier.tv")
    home()
    tree = screen("persistence")
    check(has(tree, "Mon espace, mes tableaux."), "Custom widget survives application recreation")
    if args.image:
        check(has(tree, args.title), "Personal background survives application recreation")
    address, pin = open_import()
    _, value = http(address, "/api/unlock", {"pin": pin})
    status, _ = http(address, "/api/widgets", saved_widgets, value["token"])
    check(status == 200, "Original widget preferences restored after verification")
    key("KEYCODE_BACK")

    home()
    key("KEYCODE_DPAD_UP", 2)
    key("KEYCODE_DPAD_RIGHT", 3)
    key("KEYCODE_DPAD_CENTER")
    check(has(screen("inputs"), "Sources de la télévision"), "Native HDMI source list opens")
    key("KEYCODE_BACK")
    key("KEYCODE_DPAD_RIGHT")
    key("KEYCODE_DPAD_CENTER")
    check(has(screen("settings"), "Votre Atelier"), "Custom settings menu opens")
    key("KEYCODE_DPAD_DOWN", 5)
    key("KEYCODE_DPAD_CENTER")
    check(wait_foreground("com.android.tv.settings/"), "Television settings remain accessible")
    home()
    key("KEYCODE_DPAD_UP", 2)
    key("KEYCODE_DPAD_RIGHT", 2)
    key("KEYCODE_DPAD_CENTER")
    key("KEYCODE_DPAD_RIGHT")
    key("KEYCODE_DPAD_CENTER")
    check(wait_foreground("ch.atelier.tv/.GalleryActivity"), "Full-screen art mode opens")
    before = screen("art-mode-before")
    key("KEYCODE_DPAD_RIGHT")
    after = screen("art-mode-after")
    captions = lambda t: [n.get("text") for n in t.iter("node") if " — " in n.get("text", "")]
    check(captions(before) != captions(after), "Gallery arrows change the actual painting")
    home()
    check("ch.atelier.tv/.MainActivity" in adb("shell", "cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME"), "Atelier TV is the resolved default home")

    command = [sys.executable, str(ROOT / "scripts" / "verify_screensaver.py"), args.serial]
    if args.idle:
        command.append("--idle")
    subprocess.run(command, check=True)
    passed.extend(json.loads((QA / "screensaver-results.json").read_text())["passed"])

finally:
    (QA / "results.json").write_text(json.dumps({"passed": passed}, ensure_ascii=False, indent=2) + "\n")
