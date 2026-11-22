#!/usr/bin/env bash
# Starts a throwaway Navidrome with a few generated songs and two playlists, for the smoke test.
set -euo pipefail

ND=http://localhost:4533
AUTH="u=admin&p=secret123&v=1.16.1&c=smoke&f=json"

# ffmpeg: a ready-made build from GitHub first (Ubuntu's mirrors once took 35 minutes), else apt.
if ! command -v ffmpeg >/dev/null; then
  if timeout 180 curl -fsSL -o /tmp/ffmpeg.tar.xz \
      https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-linux64-gpl.tar.xz \
      && mkdir -p /tmp/ffmpeg && tar xJf /tmp/ffmpeg.tar.xz -C /tmp/ffmpeg --strip-components=1; then
    export PATH="/tmp/ffmpeg/bin:$PATH"
  fi
  command -v ffmpeg >/dev/null || { sudo apt-get update -qq && sudo apt-get install -y -qq ffmpeg; }
fi
ffmpeg -version | head -1

mkdir -p "music/Test Artist/Sine Album" "music/Other Artist/Square Album" nd-data
# The same recording twice: on its single and on an album, for the Duplicates page (and the Trash).
# Made first, so they're the oldest and sit at the bottom of Recently added.
mkdir -p "music/Twin Artist/Twin Album" "music/Twin Artist/Twin Tune - Single"
ffmpeg -loglevel error -y -f lavfi -i "sine=frequency=660:duration=30" \
  -metadata title="Twin Tune" -metadata artist="Twin Artist" -metadata album_artist="Twin Artist" \
  -metadata album="Twin Album" -metadata track=1 -metadata date=2022 -metadata genre=Twin \
  -c:a libmp3lame -b:a 192k "music/Twin Artist/Twin Album/01.mp3"
ffmpeg -loglevel error -y -f lavfi -i "sine=frequency=660:duration=30" \
  -metadata title="Twin Tune" -metadata artist="Twin Artist" -metadata album_artist="Twin Artist" \
  -metadata album="Twin Tune - Single" -metadata track=1 -metadata date=2021 -metadata genre=Twin \
  -c:a libmp3lame -b:a 128k "music/Twin Artist/Twin Tune - Single/01.mp3"

sleep 2
for i in 1 2 3 4; do
  ffmpeg -loglevel error -y -f lavfi -i "sine=frequency=$((200 * i)):duration=45" \
    -metadata title="Sine Song $i" -metadata artist="Test Artist" -metadata album_artist="Test Artist" \
    -metadata album="Sine Album" -metadata track="$i" -metadata date=2024 -metadata genre=Electronic \
    -c:a libmp3lame -b:a 128k "music/Test Artist/Sine Album/0$i.mp3"
done
for i in 1 2 3; do
  ffmpeg -loglevel error -y -f lavfi -i "anoisesrc=color=pink:duration=45:amplitude=0.2" \
    -metadata title="Noise Song $i" -metadata artist="Other Artist" -metadata album_artist="Other Artist" \
    -metadata album="Square Album" -metadata track="$i" -metadata date=2023 -metadata genre=Ambient \
    -c:a libmp3lame -b:a 128k "music/Other Artist/Square Album/0$i.mp3"
done

# A WMA song: Navidrome streams it, but Android can't play the file itself, so the app has to
# switch to a converted stream.
mkdir -p "music/Odd Artist/Odd Album"
ffmpeg -loglevel error -y -f lavfi -i "sine=frequency=330:duration=40" \
  -metadata title="Odd Song 1" -metadata artist="Odd Artist" -metadata album_artist="Odd Artist" \
  -metadata album="Odd Album" -metadata track=1 -metadata date=2022 -metadata genre=Odd \
  -c:a wmav2 -b:a 128k "music/Odd Artist/Odd Album/01.wma"

# A song whose file disappears after the scan (moved or deleted on the server), so Navidrome still
# lists it but can't open it: the app has to say so and move on, not try every format.
mkdir -p "music/Gone Artist/Gone Album"
ffmpeg -loglevel error -y -f lavfi -i "sine=frequency=520:duration=30" \
  -metadata title="Vanished Track" -metadata artist="Gone Artist" -metadata album_artist="Gone Artist" \
  -metadata album="Gone Album" -metadata track=1 -metadata date=2021 -metadata genre=Gone \
  -c:a libmp3lame -b:a 128k "music/Gone Artist/Gone Album/01.mp3"
# And one more, for the import check: found in the library, but won't play.
ffmpeg -loglevel error -y -f lavfi -i "sine=frequency=540:duration=30" \
  -metadata title="Faded Track" -metadata artist="Gone Artist" -metadata album_artist="Gone Artist" \
  -metadata album="Gone Album" -metadata track=2 -metadata date=2021 -metadata genre=Gone \
  -c:a libmp3lame -b:a 128k "music/Gone Artist/Gone Album/02.mp3"

# A Dolby Digital Plus 5.1 album in .m4a, like Atmos music: Navidrome can't tell it from AAC,
# so the app has to look inside the file to show its Dolby badge.
mkdir -p "music/Surround Artist/Dolby Album"
for i in 1 2; do
  ffmpeg -loglevel error -y -f lavfi -i "anoisesrc=color=brown:duration=30:amplitude=0.2" \
    -af "pan=5.1|c0=c0|c1=c0|c2=c0|c3=c0|c4=c0|c5=c0" \
    -metadata title="Dolby Track $i" -metadata artist="Surround Artist" -metadata album_artist="Surround Artist" \
    -metadata album="Dolby Album" -metadata track="$i" -metadata date=2025 -metadata genre=Surround \
    -c:a eac3 -b:a 384k -f mp4 "music/Surround Artist/Dolby Album/0$i.m4a"
done

docker run -d --name navidrome -p 4533:4533 \
  -v "$PWD/music:/music:ro" -v "$PWD/nd-data:/data" \
  -e ND_LOGLEVEL=debug -e ND_SCANNER_SCHEDULE=0 -e ND_SCANNER_WATCHERWAIT=0 \
  deluan/navidrome:latest

for _ in $(seq 90); do curl -fs "$ND/ping" >/dev/null && break; sleep 2; done

# The music folder as an SMB share (user smoke / smbpass), as on a NAS: where the app moves a
# song you delete, into its _Trash folder. Files stay owned by this runner's user.
chmod -R a+rwX music
docker run -d --name samba -p 445:445 \
  -e ACCOUNT_smoke=smbpass -e UID_smoke="$(id -u)" \
  -e "SAMBA_VOLUME_CONFIG_music=[music]; path=/shares/music; valid users = smoke; guest ok = no; read only = no; browseable = yes; force user = smoke" \
  -v "$PWD/music:/shares/music" \
  ghcr.io/servercontainers/samba:latest

curl -fsS -X POST "$ND/auth/createAdmin" -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"secret123"}' >/dev/null
curl -fsS "$ND/rest/startScan?fullScan=true&$AUTH" >/dev/null || true

for _ in $(seq 90); do
  n=$(curl -fs "$ND/rest/getAlbumList2?type=newest&size=10&$AUTH" | jq '.["subsonic-response"].albumList2.album | length' || echo 0)
  [ "${n:-0}" -ge 2 ] && break
  sleep 2
done
echo "albums: $n"

# Once Navidrome has the vanishing song, take its file away (the watcher is off, so it doesn't notice).
# Both of its songs: the scan can list one a moment before the other.
for _ in $(seq 60); do
  gone=$(curl -fs "$ND/rest/search3?query=Vanished&songCount=5&$AUTH" | jq -r '.["subsonic-response"].searchResult3.song[0].id // empty' || true)
  faded=$(curl -fs "$ND/rest/search3?query=Faded&songCount=5&$AUTH" | jq -r '.["subsonic-response"].searchResult3.song[0].id // empty' || true)
  [ -n "$gone" ] && [ -n "$faded" ] && break
  sleep 2
done
echo "vanishing songs: ${gone:-not found}, ${faded:-not found}"
rm -f "music/Gone Artist/Gone Album/01.mp3" "music/Gone Artist/Gone Album/02.mp3"

# A song that went into the Trash 40 days ago (by the Trash's log): the app deletes it for good.
# Put there after the scan, so Navidrome never lists it.
mkdir -p "music/_Trash/Old Artist/Old Album"
cp "music/Test Artist/Sine Album/01.mp3" "music/_Trash/Old Artist/Old Album/01.mp3"
old_ms=$(( ($(date +%s) - 40 * 86400) * 1000 ))
printf '{"items": {"Old Artist/Old Album/01.mp3": %s}}\n' "$old_ms" > "music/_Trash/.navimuse-trash.json"
curl -s "$ND/rest/stream?id=$gone&format=raw&$AUTH" | head -c 300; echo

songs=$(curl -fs "$ND/rest/search3?query=Song&songCount=10&$AUTH" | jq -r '.["subsonic-response"].searchResult3.song[].id')
q=""
for id in $songs; do q="$q&songId=$id"; done
# One playlist named like AudioMuse-AI's clustering output, and one normal one.
curl -fsS "$ND/rest/createPlaylist?name=Chill_automatic$q&$AUTH" >/dev/null
curl -fsS "$ND/rest/createPlaylist?name=Road%20trip$q&$AUTH" >/dev/null
curl -fs "$ND/rest/getPlaylists?$AUTH" | jq -r '.["subsonic-response"].playlists.playlist[].name'
