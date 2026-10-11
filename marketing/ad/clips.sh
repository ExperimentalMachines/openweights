#!/bin/sh
# Real footage as frames the ad shows one at a time: the phones' own screen recordings (see each
# clip's JSON for the device, build, settings and marks). 30 fps, portrait. Generated, not committed.
set -eu
cd "$(dirname "$0")"
rm -rf frames/clips
for c in npu-lease tools fit poco-fit canvas-ask canvas-write canvas-run; do
  mkdir -p "frames/clips/$c"
  # The lease is read up close, so it keeps more pixels than the rest.
  w=900; [ "$c" = npu-lease ] && w=1080
  ffmpeg -hide_banner -loglevel error -i "assets/clips/$c.mp4" -vf "fps=30,scale=$w:-2" -q:v 3 "frames/clips/$c/%04d.jpg"
done
for c in frames/clips/*; do printf '%s %s\n' "$(basename "$c")" "$(ls "$c" | wc -l | tr -d ' ')"; done
