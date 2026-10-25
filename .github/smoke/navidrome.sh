#!/usr/bin/env bash
# Starts a throwaway Navidrome with a few generated songs and two playlists, for the smoke test.
set -euo pipefail

ND=http://localhost:4533
AUTH="u=admin&p=secret123&v=1.16.1&c=smoke&f=json"

command -v ffmpeg >/dev/null || { sudo apt-get update -qq && sudo apt-get install -y -qq ffmpeg; }

mkdir -p "music/Test Artist/Sine Album" "music/Other Artist/Square Album" nd-data
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

docker run -d --name navidrome -p 4533:4533 \
  -v "$PWD/music:/music:ro" -v "$PWD/nd-data:/data" \
  -e ND_LOGLEVEL=debug -e ND_SCANNER_SCHEDULE=0 \
  deluan/navidrome:latest

for _ in $(seq 90); do curl -fs "$ND/ping" >/dev/null && break; sleep 2; done

curl -fsS -X POST "$ND/auth/createAdmin" -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"secret123"}' >/dev/null
curl -fsS "$ND/rest/startScan?fullScan=true&$AUTH" >/dev/null || true

for _ in $(seq 90); do
  n=$(curl -fs "$ND/rest/getAlbumList2?type=newest&size=10&$AUTH" | jq '.["subsonic-response"].albumList2.album | length' || echo 0)
  [ "${n:-0}" -ge 2 ] && break
  sleep 2
done
echo "albums: $n"

songs=$(curl -fs "$ND/rest/search3?query=Song&songCount=10&$AUTH" | jq -r '.["subsonic-response"].searchResult3.song[].id')
q=""
for id in $songs; do q="$q&songId=$id"; done
# One playlist named like AudioMuse-AI's clustering output, and one normal one.
curl -fsS "$ND/rest/createPlaylist?name=Chill_automatic$q&$AUTH" >/dev/null
curl -fsS "$ND/rest/createPlaylist?name=Road%20trip$q&$AUTH" >/dev/null
curl -fs "$ND/rest/getPlaylists?$AUTH" | jq -r '.["subsonic-response"].playlists.playlist[].name'
