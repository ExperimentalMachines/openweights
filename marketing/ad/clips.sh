#!/bin/sh
# Real footage as frames the ad shows one at a time: the phones' own screen recordings (see each
# clip's JSON for the device, build, model, settings and marks). 30 fps, portrait. Generated.
set -eu
cd "$(dirname "$0")"
rm -rf frames/clips
for c in airplane-bill paella poco-fit; do
  mkdir -p "frames/clips/$c"
  # Read up close by the camera, so they keep their pixels.
  ffmpeg -hide_banner -loglevel error -i "assets/clips/$c.mp4" -vf "fps=30,scale=1080:-2" -q:v 3 "frames/clips/$c/%04d.jpg"
done
for c in frames/clips/*; do printf '%s %s\n' "$(basename "$c")" "$(ls "$c" | wc -l | tr -d ' ')"; done
