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


def dismiss_system_dialogs(root):
    """Taps "Wait" on an emulator "… isn't responding" popup, which otherwise covers the app."""
    nodes = list(root.iter("node"))
    if any("isn't responding" in n.get("text", "") for n in nodes):
        for n in nodes:
            if n.get("text", "") == "Wait":
                x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
                adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
                time.sleep(2)
                return True
    return False


def find(text=None, desc=None, contains=False, last=False):
    hit = None
    root = dump()
    if dismiss_system_dialogs(root):
        root = dump()
    for n in root.iter("node"):
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


def scroll_to(text=None, swipes=8, desc=None):
    for _ in range(swipes):
        n = find(text, desc=desc)
        if n is not None:
            return n
        adb("shell", "input", "swipe", "540", "1700", "540", "700", "400")
        time.sleep(1)
    raise AssertionError(f"couldn't scroll to {text!r}")


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
    # Answer the notification prompt up front so it never covers a button mid-test.
    adb("shell", "pm", "grant", "app.navimuse", "android.permission.POST_NOTIFICATIONS")
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
    tap(text="Collection")
    wait_for(text="Mixes & Radio")
    shot("03-collection")
    tap(text="Albums")
    wait_for(text="Square Album")
    time.sleep(3)
    shot("03b-albums")
    back()
    tap(text="Artists")
    wait_for(text="Test Artist")
    shot("04-artists")
    back()
    tap(text="Playlists")
    wait_for(text="Chill_automatic")
    shot("05-playlists")


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
    wait_for(text="Road trip")
    shot("08-playlist")
    # The playlist header is tall; scroll the song into view first.
    tap_node(scroll_to("Sine Song 1"))
    wait_for(desc="Pause", timeout=30)
    time.sleep(4)
    shot("09-playing")
    now = api("getNowPlaying").get("nowPlaying", {}).get("entry", [])
    assert now, "Navidrome didn't get a 'now playing' report"


def now_playing():
    tap(text="Sine Song 1", last=True)  # the mini player, below the list
    wait_for(text="Playing from")
    # Repeat the queue, so music is still playing when background playback is checked at the end.
    tap(desc="Repeat")
    time.sleep(2)
    shot("10-now-playing")
    assert find(desc="Share") is None, "the playing screen still has a Share button"
    # Tap the art for lyrics, then tap the lyrics to get the art back.
    tap(desc="Album art")
    wait_for(desc="Lyrics")
    shot("10b-lyrics")
    tap(desc="Lyrics")
    wait_for(desc="Album art")
    # Swipe up from the bottom of the screen for the queue.
    adb("shell", "input", "swipe", "540", "2150", "540", "1250", "350")
    wait_for(text="Save as playlist", contains=True)
    shot("11-queue")
    back()
    back()  # closes the playing screen


def auto_buttons():
    # What Android Auto (and the phone's media controls) get: favourite, shuffle, repeat, radio.
    out = adb("shell", "dumpsys", "media_session")
    with open(f"{OUT}/media-session.txt", "w") as f:
        f.write(out)
    for name in ("favourites", "Shuffle", "Repeat all", "Instant Mix"):
        assert name in out, f"{name!r} isn't among the media session's buttons"
    assert re.search(r"queueTitle=[^\n]*size=[1-9]", out), "the queue isn't shared with Android Auto"


def save_queue():
    # Save what's playing (the Road trip playlist) from the queue, as you would an Instant Mix.
    road_trip = next(p for p in api("getPlaylists")["playlists"]["playlist"] if p["name"] == "Road trip")
    tap(text="Sine Song 1", last=True)  # the mini player
    wait_for(text="Playing from")
    tap(desc="Queue")
    tap(text="Save as playlist", contains=True)
    wait_for(text="Save as playlist")  # the save box's title (the queue's button has an icon and spaces)
    assert find(text="Road trip copy", contains=True) is not None, "the save box didn't suggest a name"
    shot("11b-save-queue")
    tap(text="Save")
    wait_for(text="Saved playlist", contains=True, timeout=30)
    shot("11c-queue-saved")
    saved = [p for p in api("getPlaylists")["playlists"]["playlist"] if p["name"] == "Road trip copy"]
    assert saved, "the queue wasn't saved as a playlist"
    assert saved[0]["songCount"] == road_trip["songCount"], f"saved {saved[0]['songCount']} of {road_trip['songCount']} songs"
    back()  # closes the playing screen


def swipe_to_queue():
    # Still on the Road trip playlist: swipe a song right to add it to the end of the queue.
    n = scroll_to("Sine Song 2")
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
    y = (y1 + y2) // 2
    adb("shell", "input", "swipe", "150", str(y), "800", str(y), "500")
    wait_for(text="Added to queue: Sine Song 2", timeout=15)
    shot("11d-swiped-to-queue")


def audiomuse_tab():
    tap(text="Mixes")
    wait_for(text="Instant Mix")
    shot("12-mixes")
    scroll_to("Song path")


def genres():
    tap(text="Search")
    wait_for(text="Browse genres")
    shot("13a-genres")
    tap(text="Ambient")
    wait_for(text="Shuffle Ambient")
    time.sleep(2)
    shot("13b-genre")
    back()


def search():
    tap(text="Search")
    tap(text="Songs, albums, artists")
    type_text("Noise")
    wait_for(text="Noise Song 1", timeout=20)
    adb("shell", "input", "keyevent", "111")
    shot("13-search")


def album_and_artist():
    tap(text="Collection")
    tap(text="Albums")
    tap(text="Square Album")
    wait_for(desc="Instant Mix")
    time.sleep(2)
    shot("14-album")
    tap(text="Other Artist")
    wait_for(text="Artist radio")
    time.sleep(2)
    shot("15-artist")
    back()
    back()
    back()


def dolby_album():
    # Keep what Navidrome reports for the Dolby songs, to see which details it gives away.
    with open(f"{OUT}/dolby-songs.json", "w") as f:
        json.dump(api("search3", "&query=Dolby&songCount=5&albumCount=0&artistCount=0"), f, indent=1)
    tap(text="Collection")
    tap(text="Albums")
    tap_node(scroll_to("Dolby Album"))
    wait_for(text="Dolby Digital Plus", timeout=40)
    time.sleep(1)
    shot("16a-dolby-album")
    back()
    back()


def settings_and_dark():
    tap(text="Home")
    tap(desc="Settings")
    wait_for(text="Navidrome")
    shot("17-settings")
    # Safety lives in Settings now.
    tap_node(scroll_to("Safety"))
    wait_for(text="This app never")
    shot("16-safety")
    back()
    time.sleep(1)
    settings_tab("Look")
    tap_node(scroll_to("Dark"))
    shot("17b-settings-dark")
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


def downloads():
    # The emulator's network may count as mobile data, so let downloads use any connection.
    adb("shell", "am", "start", "-n", "app.navimuse/.MainActivity")
    time.sleep(2)
    tap(text="Home")
    tap(desc="Settings")
    settings_tab("Downloads")
    tap_node(scroll_to("Download on Wi-Fi only"))
    back()
    tap(text="Collection")
    tap(text="Albums")
    tap(text="Square Album")
    tap(desc="Download")
    wait_for(desc="Remove download", timeout=90)
    shot("20-album-downloaded")
    back()
    back()
    tap(text="Downloads")
    wait_for(text="On this phone")
    shot("21-downloads")


def settings_tab(name):
    """Opens one of the tabs across the top of Settings; the row scrolls sideways for the later ones."""
    n = find(text=name)
    if n is None:
        anchor = find(text="General") or find(text="Playback") or find(text="Look")
        _, y1, _, y2 = map(int, re.findall(r"\d+", anchor.get("bounds")))
        y = str((y1 + y2) // 2)
        adb("shell", "input", "swipe", "950", y, "150", y, "300")
        time.sleep(1)
        n = wait_for(text=name)
    tap_node(n)
    time.sleep(1)


def open_customize():
    """Settings → Look (colours, themes, styles)."""
    adb("shell", "am", "start", "-n", "app.navimuse/.MainActivity")
    time.sleep(2)
    # Collection's gear: Home may be scrolled down (an app style's sections), hiding its own.
    tap(text="Collection")
    tap(desc="Settings")
    settings_tab("Look")
    wait_for(text="Accent colour")


def customize():
    open_customize()
    shot("22a-customize")
    tap(desc="Pink")
    # Layout → Home: switch on Random songs.
    settings_tab("Layout")
    shot("22a2-layout-tab")
    tap_node(scroll_to("Random songs", swipes=12))
    # Player: a spinning record and a round play button (Share is off by default).
    settings_tab("Player")
    tap_node(scroll_to("Album art", swipes=6))
    tap(text="Spinning record")
    tap_node(scroll_to("Circle", swipes=6))
    tap_node(scroll_to("Open the playing screen", swipes=10))
    wait_for(text="Playing from")
    time.sleep(3)
    assert find(desc="Share") is None, "the share button is still on the playing screen"
    assert find(desc="Pause") is not None or find(desc="Play") is not None, "no play button"
    shot("22b-playing-customized")
    back()  # closes the playing screen
    # Mini player: floating.
    tap_node(scroll_to("Floating", swipes=6))
    time.sleep(1)
    shot("22c-customize-mini")
    back()  # Collection
    tap(text="Home")
    scroll_to("Random songs", swipes=10)
    time.sleep(2)
    shot("22d-home-customized")


NIGHT_DRIVE = {
    "name": "Night Drive", "id": "", "imported": True,
    "schemes": {
        "light": {"primary": "#A6005C", "onPrimary": "#FFFFFF", "secondaryContainer": "#FFDEA0",
                  "surface": "#FFF8FC", "onSurface": "#1F1826", "onSurfaceVariant": "#443A54",
                  "outline": "#766B87", "surfaceContainer": "#F2E6F5", "surfaceContainerHigh": "#ECDFF0"},
        "dark": {"primary": "#FF2E92", "onPrimary": "#4A0028", "secondaryContainer": "#593D00",
                 "surface": "#150F1C", "onSurface": "#EEE3F5", "onSurfaceVariant": "#CBBEDD",
                 "outline": "#9186A6", "surfaceContainerLow": "#1A1424", "surfaceContainer": "#1F1829",
                 "surfaceContainerHigh": "#2A2235", "surfaceContainerHighest": "#352C41"},
    },
}


def symfonium_theme():
    # Serve a Symfonium-format colour theme from this machine (the emulator sees it as 10.0.2.2).
    os.makedirs("theme-site", exist_ok=True)
    with open("theme-site/night-drive.json", "w") as f:
        json.dump(NIGHT_DRIVE, f)
    server = subprocess.Popen(["python3", "-m", "http.server", "8088", "--directory", "theme-site"],
                              stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        time.sleep(2)
        adb("shell", "am", "start", "-n", "app.navimuse/.MainActivity")
        time.sleep(2)
        open_customize()
        tap_node(scroll_to("Import link", swipes=12))
        wait_for(text="Import from a link")
        tap(text="styles.symfonium.app/styles", contains=True)
        type_text("http://10.0.2.2:8088/night-drive.json")
        tap(text="Import")
        wait_for(text="Night Drive", timeout=30)
        time.sleep(2)
        shot("23-theme-imported")
        back()
        tap(text="Home")
        time.sleep(2)
        shot("24-home-night-drive")
    finally:
        server.terminate()


NOW_PLAYING_STYLE = {
    "name": "Smoke Combo", "type": "nowplaying", "version": 1,
    "style": json.dumps({
        "compactConfiguration": {
            "floating": True, "playerCornerRadius": 50, "swipeToSkip": True,
            "playerMargins": {"horizontal": 8, "bottom": 4}, "thumbnailRoundedCorners": 50,
            "title": {"text": 100, "customText": "Mini %title%"},
        },
        "portraitConfiguration": {
            "coverArtRoundedCornerPercent": 50, "sliderSize": 1.3, "sliderShowTimes": 1,
            "controlsOrder": [10, 11, 20, 30],
            "controlText1": {"text": 100, "style": 2, "size": 1.4, "customText": "Styled %title%"},
            "controlText2": {"text": 100, "color": 1, "customText": "%artist%{ · %album%}{ (%year%)}"},
            "topBarTitle": {"text": 100, "customText": "{Up next: %next.title%}"},
        },
    }),
}

APP_STYLE = {
    "name": "Smoke App", "type": "application", "version": 1,
    "style": json.dumps({"i": {
        "home.rows": "s:shortcut;q:recentalbums;q:lastplayedalbums;q:favoritesongs",
        "home.rows.hidden": "",
        "home.shortcuts": "playlists;artists;settings",
        "library.shortcuts": "artists;realalbums;playlists",
        "library.page.configuration": json.dumps({"gridColumns": 2}),
        "overview.row.q:recentalbums": json.dumps({"headerVisible": True, "headerCustomText": "Fresh in", "dataItemCount": 20}),
        "overview.row.q:lastplayedalbums": json.dumps({"headerVisible": True, "headerCustomText": "Jump back in"}),
    }}),
}


def import_link(url):
    """Imports a style from a link in Settings → Look, then goes back to Home."""
    open_customize()
    tap_node(scroll_to("Import link", swipes=12))
    wait_for(text="Import from a link")
    tap(text="styles.symfonium.app/styles", contains=True)
    type_text(url)
    tap(text="Import")
    time.sleep(3)
    back()
    tap(text="Home")


def symfonium_styles():
    with open("theme-site/smoke-combo.json", "w") as f:
        json.dump(NOW_PLAYING_STYLE, f)
    with open("theme-site/smoke-app.json", "w") as f:
        json.dump(APP_STYLE, f)
    server = subprocess.Popen(["python3", "-m", "http.server", "8088", "--directory", "theme-site"],
                              stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        time.sleep(2)
        import_link("http://10.0.2.2:8088/smoke-app.json")
        time.sleep(3)
        scroll_to("Fresh in")
        time.sleep(2)
        shot("25-home-app-style")
        import_link("http://10.0.2.2:8088/smoke-combo.json")
        wait_for(text="Mini ", contains=True, timeout=20)
        shot("26-mini-player-style")
        tap(text="Mini ", contains=True)
        wait_for(text="Styled ", contains=True, timeout=20)
        time.sleep(2)
        shot("27-now-playing-style")
        back()
    finally:
        server.terminate()


def background_playback():
    # Music should keep playing with the app in the background.
    out = adb("shell", "dumpsys", "media_session")
    assert "state=PLAYING" in out or "state=3" in out, "media session isn't playing in the background"


ok = True
for name, fn in [
    ("log in", login),
    ("collection", library),
    ("keep a copy of an _automatic playlist", keep_copy),
    ("play a song + now-playing report", play),
    ("now playing screen and queue", now_playing),
    ("Android Auto buttons: favourite, shuffle, repeat, radio", auto_buttons),
    ("save the queue as a playlist", save_queue),
    ("swipe a song right to add it to the queue", swipe_to_queue),
    ("Mixes tab", audiomuse_tab),
    ("genres", genres),
    ("search", search),
    ("album and artist pages", album_and_artist),
    ("Dolby badge on a Dolby album", dolby_album),
    ("safety, settings, dark mode + dark icon", settings_and_dark),
    ("download an album for offline play", downloads),
    ("customize look and layout", customize),
    ("import a Symfonium colour theme", symfonium_theme),
    ("import Symfonium app and now-playing styles", symfonium_styles),
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
