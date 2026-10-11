#!/bin/sh
# Builds out/openweights-ad-16x9.mp4 from the recordings, music.py and ad.html: renders 120 fps,
# blends each pair of frames into one at 60 fps (motion blur), and sets the music to -14 LUFS with
# a two-pass loudnorm. Needs node (npm install here), uv and ffmpeg.
set -eu
cd "$(dirname "$0")"

./clips.sh
uv run qr.py
uv run music.py
node data.cjs
rm -rf frames/hi
node render.cjs 120 frames/hi 8

measured=$(ffmpeg -hide_banner -i out/music.wav -af loudnorm=I=-14:TP=-2.0:LRA=11:print_format=json -f null - 2>&1 | sed -n '/^{/,/^}/p')
field() { printf '%s\n' "$measured" | sed -n "s/.*\"$1\" : \"\(.*\)\".*/\1/p"; }
ffmpeg -hide_banner -loglevel error -y -i out/music.wav \
  -af "loudnorm=I=-14:TP=-2.0:LRA=11:measured_I=$(field input_i):measured_TP=$(field input_tp):measured_LRA=$(field input_lra):measured_thresh=$(field input_thresh):offset=$(field target_offset):linear=true,aresample=48000" \
  -c:a pcm_s24le out/music-norm.wav

ffmpeg -hide_banner -loglevel error -y -framerate 120 -i frames/hi/f_%05d.jpg -i out/music-norm.wav \
  -vf "tmix=frames=2,fps=60,scale=in_range=full:out_range=tv:in_color_matrix=bt601:out_color_matrix=bt709,format=yuv420p" \
  -c:v libx264 -preset slow -crf 16 -profile:v high -level 4.2 \
  -colorspace bt709 -color_primaries bt709 -color_trc bt709 -color_range tv \
  -c:a aac -b:a 256k -ar 48000 -shortest -movflags +faststart out/openweights-ad-16x9.mp4
ffmpeg -hide_banner -loglevel error -y -ss 9.8 -i out/openweights-ad-16x9.mp4 -frames:v 1 out/thumbnail.png
rm -rf frames/hi
echo "built out/openweights-ad-16x9.mp4"
