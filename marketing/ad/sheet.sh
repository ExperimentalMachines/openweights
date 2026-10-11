#!/bin/sh
# sheet.sh out.png a.png b.png c.png d.png : 2x2 contact sheet at half size
out=$1; shift
ffmpeg -loglevel error -y -i "$1" -i "$2" -i "$3" -i "$4" -filter_complex "[0]scale=960:540[a];[1]scale=960:540[b];[2]scale=960:540[c];[3]scale=960:540[d];[a][b]hstack[t];[c][d]hstack[u];[t][u]vstack" "$out"
