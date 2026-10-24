# NaviMuse

An Android music player for **Navidrome** with **AudioMuse-AI** mixes, built so it **can't delete anything** on your server.

## What it does

- **Your library:** home shelves (recently added, recently played, most played, discover), albums, artists, playlists, favourites and search.
- **AudioMuse-AI**, through Navidrome's AudioMuse-AI plugin:
  - **Instant Mix** from any song, album or what's playing. You also get "Instant Mixes for you" on the home screen and a "Surprise mix".
  - **Similar songs**, with a match percentage for each one.
  - **Artist radio** and "sounds like" artists.
  - **Song path:** pick a start song and an end song, and get a smooth journey between them.
  - **Mood search** (optional, talks to AudioMuse-AI directly): type "calm piano for a rainy night" and get matching songs.
- **Keep AudioMuse playlists:** AudioMuse-AI deletes every playlist ending in `_automatic` each time it runs clustering. NaviMuse marks those playlists and can save a normal copy, one at a time or all at once, so the next run leaves them alone.
- **Playback** keeps going in the background, with notification, lock-screen and Bluetooth controls. Recent audio is cached on the phone so replays and seeking are smooth. The queue comes back after a restart, and plays are reported to Navidrome.
- **Android Auto:** tabs for Mixes, Recently added, Playlists and Favourites, an "Instant Mix from what's playing" button, and voice search ("play … on NaviMuse").
- **Streaming quality** can be set separately for Wi-Fi and mobile data (original, 320, 192 or 128 kbps).
- **Looks:** light, dark and pure-black themes, Material You colours, and a dark home-screen icon in dark mode.

## Why it can't delete your files

Every request passes through one gate, [`SafetyGuard`](app/src/main/java/app/navimuse/net/SafetyGuard.kt), before it leaves the phone. Anything not on its short list of safe requests is refused on the phone and never reaches your server:

| Never sent | Allowed |
| --- | --- |
| `deletePlaylist`, `deleteShare`, `deleteUser` and every other delete | Browsing, searching, streaming, album art |
| `createPlaylist` with a `playlistId` (this replaces an existing playlist's songs) | `createPlaylist` that makes a **new** playlist |
| `updatePlaylist` with `songIndexToRemove`, a rename or a visibility change | `updatePlaylist` that only **adds** songs |
| Playlist names ending in `_automatic` (AudioMuse-AI deletes those) | Favourites (star/unstar) and play reports (scrobble) |
| `startScan`, play-queue saves, user, share and radio changes | |
| Any AudioMuse-AI call except searching. Its API token is admin-level, so cleaning, clustering, analysis, delete and restore calls are all blocked | AudioMuse-AI mood search (`POST /api/clap/search`) |

The unit tests in [`SafetyGuardTest`](app/src/test/java/app/navimuse/SafetyGuardTest.kt) check every one of these, and they run on every build. The Safety screen in the app lists anything it blocked during the session.

## Keeping AudioMuse-AI away from your music folder

An app on your phone can't control what AudioMuse-AI does on the server, so check this part of your setup too:

- AudioMuse-AI streams each song from Navidrome into its temp folder (`TEMP_DIR`, default `/app/temp_audio`), analyses it, and deletes the copy. **At the start and end of every analysis run it empties that whole folder.** If your music is mounted at `/app/temp_audio`, or `TEMP_DIR` points at your music, it will delete your songs.
- With Navidrome, AudioMuse-AI **doesn't need your music mounted at all**. Give `/app/temp_audio` its own volume in both the flask and worker services:

  ```yaml
  environment:
    TEMP_DIR: "/app/temp_audio"
  volumes:
    - temp-audio-worker:/app/temp_audio
  ```

- If you do mount music into a container, make it read-only: `/mnt/tank/music:/music:ro`. Navidrome only needs read-only access too.

## Install

1. Open **Releases** on this repo and download the newest `navimuse-1.0.N.apk` on your phone.
2. Open it and allow installs from your browser or file manager if asked. New versions install over old ones and keep your login.

For **Android Auto** with a sideloaded app: in the Android Auto settings on your phone, tap *Version* about ten times to turn on developer mode. Then, from the ⋮ menu, open *Developer settings* and turn on **Unknown sources**.

## Connecting

- **Navidrome:** use the address you open Navidrome with in a browser (for example `http://192.168.1.10:4533`), plus your username and password. Only `md5(password + random salt)` is sent; your password never is. It's stored encrypted with the phone's hardware keystore.
- **AudioMuse features through Navidrome** need the [AudioMuse-AI Navidrome plugin](https://github.com/NeptuneHub/AudioMuse-AI-NV-plugin), with `audiomuseai` first in `ND_AGENTS` (for example `ND_AGENTS=audiomuseai,lastfm,deezer`).
- **Mood search (optional):** under Settings → AudioMuse-AI, enter its address (for example `http://192.168.1.10:8000`) and its API token, if its login is on.
- **Away from home:** connect with a VPN such as Tailscale and use the server's VPN address.

## Building

Every push to `main` runs the tests, builds a signed release APK, and publishes it as a GitHub Release.

To build it yourself, you need the JDK 17 and the Android SDK:

```sh
./gradlew testDebugUnitTest assembleRelease
```

Built with Kotlin, Jetpack Compose (Material 3), Media3 (ExoPlayer + MediaLibraryService), OkHttp and Coil. Requires Android 8.0 or newer.
