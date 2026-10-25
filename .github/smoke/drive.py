"""Drives the installed app with adb + uiautomator, takes screenshots, and checks the server."""
import json
import os
import re
import subprocess
import sys
import time
import urllib.request
import xml.etree.ElementTree as ET

OUT = "smoke-out"
ND = "http://localhost:4533"
AUTH = "u=admin&p=secret123&v=1.16.1&c=smoke&f=json"
APK = "app/build/outputs/apk/release/app-release.apk"
os.makedirs(OUT, exist_ok=True)
results = []


def adb(*args, check=False, timeout=60):
    r = subprocess.run(["adb", *args], capture_output=True, text=True, timeout=timeout)
    if check and r.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed: {r.stderr}")
    return r.stdout


def api(method, params=""):
    with urllib.request.urlopen(f"{ND}/rest/{method}?{AUTH}{params}", timeout=20) as r:
        return json.load(r)["subsonic-response"]


def shot(name):
    with open(f"{OUT}/{name}.png", "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, timeout=60).stdout)


def dump():
    for _ in range(5):
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml", timeout=40)
        xml = adb("shell", "cat", "/sdcard/ui.xml")
        if "<?xml" in xml:
            try:
                return ET.fromstring(xml[xml.index("<?xml"):])
            except ET.ParseError:
                pass
        time.sleep(1.5)
    return ET.fromstring("<hierarchy/>")


def find(text=None, desc=None, contains=False, last=False):
    hit = None
    for n in dump().iter("node"):
        t, d = n.get("text", ""), n.get("content-desc", "")
        if (text is not None and (t == text or (contains and text in t))) or (
            desc is not None and (d == desc or (contains and desc in d))
        ):
            hit = n
            if not last:
                return n
    return hit


def wait_for(text=None, desc=None, timeout=30, contains=False, last=False):
    end = time.time() + timeout
    while time.time() < end:
        n = find(text, desc, contains, last)
        if n is not None:
            return n
        time.sleep(1.5)
    raise AssertionError(f"didn't appear on screen: {text or desc!r}")


def tap_node(n):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(1.5)


def tap(text=None, desc=None, timeout=30, contains=False, last=False):
    tap_node(wait_for(text, desc, timeout, contains, last))


def type_text(s):
    adb("shell", "input", "text", s.replace(" ", "%s"))
    time.sleep(0.8)


def back():
    adb("shell", "input", "keyevent", "4")
    time.sleep(1.5)


def crashed():
    log = adb("logcat", "-d", "-b", "crash")
    return "app.navimuse" in log and "FATAL EXCEPTION" in log


def step(name, fn):
    try:
        fn()
        if crashed():
            raise AssertionError("app crashed (see logcat-crash.txt)")
        results.append(f"PASS  {name}")
        print(f"PASS  {name}", flush=True)
        return True
    except Exception as e:  # noqa: BLE001
        results.append(f"FAIL  {name}: {e}")
        print(f"FAIL  {name}: {e}", flush=True)
        shot(f"fail-{re.sub(r'[^a-z0-9]+', '-', name.lower())}")
        return False


def login():
    adb("install", "-r", APK, check=True, timeout=180)
    adb("logcat", "-c")
    adb("shell", "am", "start", "-n", "app.navimuse/.MainActivity", check=True)
    wait_for(text="Connect", timeout=60)
    shot("01-login")
    tap(text="Navidrome address")
    type_text("http://10.0.2.2:4533")
    tap(text="Username")
    type_text("admin")
    tap(text="Password")
    type_text("secret123")
    adb("shell", "input", "keyevent", "66")  # Enter = Go on the password field
    wait_for(text="Recently added", timeout=45)
    time.sleep(5)
    shot("02-home")


def library():
    tap(text="Library")
    wait_for(text="Albums")
    time.sleep(3)
    shot("03-library-albums")
    tap(text="Artists")
    wait_for(text="Test Artist")
    shot("04-library-artists")
    tap(text="Playlists")
    wait_for(text="Chill_automatic")
    shot("05-library-playlists")


def keep_copy():
    tap(text="Chill_automatic")
    wait_for(text="Keep a copy")
    shot("06-automatic-playlist")
    tap(text="Keep a copy")
    time.sleep(4)
    shot("07-after-keep")
    names = [p["name"] for p in api("getPlaylists")["playlists"]["playlist"]]
    assert "Chill (kept)" in names, f"kept copy missing: {names}"
    assert "Chill_automatic" in names, f"original playlist disappeared: {names}"
    back()


def play():
    tap(text="Road trip")
    wait_for(text="Sine Song", contains=True)
    shot("08-playlist")
    tap(text="Sine Song 1")
    wait_for(desc="Pause", timeout=30)
    time.sleep(4)
    shot("09-playing")
    now = api("getNowPlaying").get("nowPlaying", {}).get("entry", [])
    assert now, "Navidrome didn't get a 'now playing' report"


def now_playing():
    tap(text="Sine Song 1", last=True)  # the mini player, below the list
    wait_for(text="Now playing")
    time.sleep(2)
    shot("10-now-playing")
    tap(text="Queue")
    wait_for(text="Save as playlist", contains=True)
    shot("11-queue")
    back()
    tap(desc="Close")


def audiomuse_tab():
    tap(text="AudioMuse")
    wait_for(text="Song path")
    shot("12-audiomuse")


def search():
    tap(text="Search")
    tap(text="Songs, albums, artists")
    type_text("Noise")
    wait_for(text="Noise Song 1", timeout=20)
    adb("shell", "input", "keyevent", "111")
    shot("13-search")


def album_and_artist():
    tap(text="Library")
    tap(text="Albums")
    tap(text="Square Album")
    wait_for(text="Instant Mix")
    time.sleep(2)
    shot("14-album")
    tap(text="Other Artist")
    wait_for(text="Artist radio")
    time.sleep(2)
    shot("15-artist")
    back()
    back()


def settings_and_dark():
    tap(text="Home")
    tap(desc="Safety")
    wait_for(text="This app never")
    shot("16-safety")
    back()
    tap(desc="Settings")
    wait_for(text="Playback")
    shot("17-settings")
    tap(text="Dark")
    time.sleep(2)
    back()
    time.sleep(3)
    shot("18-home-dark")
    adb("shell", "input", "keyevent", "3")  # home: app goes to the background, icon should switch
    time.sleep(4)
    pkg = adb("shell", "dumpsys", "package", "app.navimuse")
    enabled = ""
    if "enabledComponents:" in pkg:
        enabled = re.split(r"disabledComponents:|\n\s*\n", pkg.split("enabledComponents:", 1)[1])[0]
    assert "LauncherDark" in enabled, "dark launcher icon wasn't switched on"
    shot("19-launcher")


def background_playback():
    # Music should keep playing with the app in the background.
    out = adb("shell", "dumpsys", "media_session")
    assert "state=PLAYING" in out or "state=3" in out, "media session isn't playing in the background"


ok = True
for name, fn in [
    ("log in", login),
    ("library tabs", library),
    ("keep a copy of an _automatic playlist", keep_copy),
    ("play a song + now-playing report", play),
    ("now playing screen and queue", now_playing),
    ("AudioMuse tab", audiomuse_tab),
    ("search", search),
    ("album and artist pages", album_and_artist),
    ("safety, settings, dark mode + dark icon", settings_and_dark),
    ("background playback", background_playback),
]:
    ok = step(name, fn) and ok

with open(f"{OUT}/logcat-crash.txt", "w") as f:
    f.write(adb("logcat", "-d", "-b", "crash"))
with open(f"{OUT}/logcat-app.txt", "w") as f:
    pid = adb("shell", "pidof", "app.navimuse").strip()
    f.write(adb("logcat", "-d", f"--pid={pid}") if pid else adb("logcat", "-d", "-t", "3000"))
with open(f"{OUT}/results.txt", "w") as f:
    f.write("\n".join(results) + "\n")
print("\n".join(results))
sys.exit(0 if ok else 1)
