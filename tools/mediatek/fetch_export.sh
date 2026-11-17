#!/bin/bash
# Copies one finished MediaTek export from the export VM to the Mac, one scp per file in parallel
# (a single scp ran ~3.7 MB/s), writes runner.txt for the given window, and checks sizes.
#   tools/mediatek/fetch_export.sh <export name, e.g. Qwen_Qwen3-0.6B-MT6989-2048-A16W8> <local dir> <window>
set -u
NAME=$1; DEST=$2; WIN=$3
G=(--project thematic-garage-514815-a4 --zone us-central1-a --quiet)
SRC="~/mtk/out/$NAME/mtk/mt6989"
mkdir -p "$DEST"
files=$(gcloud compute ssh mtk-export "${G[@]}" --command "cd $SRC && ls" 2>/dev/null)
sizes=$(gcloud compute ssh mtk-export "${G[@]}" --command "cd $SRC && stat -c '%n %s' *" 2>/dev/null)
for f in $files; do gcloud compute scp "${G[@]}" "mtk-export:$SRC/$f" "$DEST/$f" >/dev/null 2>&1 & done
wait
bad=0
while read -r f s; do
  [ "$(stat -f %z "$DEST/$f" 2>/dev/null)" = "$s" ] || { echo "size mismatch: $f"; bad=1; }
done <<< "$sizes"
python3 "$(dirname "$0")/runner_txt.py" "$DEST/config.json" "$WIN" > "$DEST/runner.txt" || bad=1
# Llama 3.1/3.2 train with llama3 RoPE scaling, which MediaTek's runner ignores unless told
# (patch_bench.py, rope_llama3_*). The values come from the source model's own config.json, not the
# export's name (Codex review, round three: Llama 3.1's factor is 8, 3.2's 32), fetched from
# Hugging Face at the revision the export names (source_model, source_revision in its config.json;
# the VM deletes its copy when an export succeeds). A rope_scaling of any other type is refused,
# because the runner would silently ignore it.
src=$(python3 -c 'import json,sys; c=json.load(open(sys.argv[1])); print(c["source_model"] + "/resolve/" + c["source_revision"])' "$DEST/config.json")
curl -sfL "https://huggingface.co/$src/config.json" \
  | python3 -c '
import json, sys
rs = json.load(sys.stdin).get("rope_scaling")
if rs:
    if rs.get("rope_type", rs.get("type")) != "llama3":
        sys.exit(f"unsupported rope_scaling {rs}")
    for k, f in (("factor", "factor"), ("low", "low_freq_factor"), ("high", "high_freq_factor"), ("orig", "original_max_position_embeddings")):
        print(f"rope_llama3_{k}={rs[f]:g}")
' >> "$DEST/runner.txt" || { echo "rope_scaling: source config unreadable or unsupported"; bad=1; }
echo "fetched $NAME bad=$bad"; ls -la "$DEST"
exit $bad
