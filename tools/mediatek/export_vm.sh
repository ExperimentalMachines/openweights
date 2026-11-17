#!/usr/bin/env bash
# MediaTek NeuroPilot exports for a chip the exporter's own workflow does not build, run on a plain
# x86 Linux VM (Ubuntu 22.04) with the exporter's pipeline at a pinned commit. The workflow's
# Modal path failed its tool-version probe on 2026-11-15 (run 43753829102: "mtk_converter is None")
# before exporting anything; this does the same steps as its scripts/mtk-setup.sh by hand.
#
#   bash export_vm.sh setup                      # once: uv, both Python envs, SDK wheels, examples
#   bash export_vm.sh export <hf-id> <soc> <window> <A16W8|A16W4>
#
# Needs execupack.tgz (git archive of the exporter at the pinned commit) in $HOME. Gated models
# are read from ungated mirrors (unsloth/...) so no Hugging Face token reaches the VM.
set -euo pipefail
W=$HOME/mtk
cmd=${1:?setup or export}
if [ "$cmd" = setup ]; then
  sudo apt-get -qq update && sudo apt-get -qq install -y git curl python3.10-venv build-essential >/dev/null
  curl -LsSf https://astral.sh/uv/install.sh | sh >/dev/null
  export PATH=$HOME/.local/bin:$PATH
  mkdir -p "$W" && cd "$W" && rm -rf execupack && mkdir execupack && tar xzf "$HOME/execupack.tgz" -C execupack
  cd execupack
  uv venv -q --python 3.11 "$W/pipe-venv"
  uv pip install -q --python "$W/pipe-venv/bin/python" -r requirements/export-mtk.txt
  python3.10 -m venv "$W/mtk-venv"
  eval "$(grep -E '^(NEUROPILOT_SDK_[A-Z0-9_]+|MTK_[A-Z_]+_VERSION|EXECUTORCH_COMMIT)=' config/versions.env | sed 's/^/export /')"
  sdk="$W/neuropilot"; mkdir -p "$sdk"
  curl -fsSL --retry 3 -o "$sdk.tar.gz" "$NEUROPILOT_SDK_URL"
  echo "$NEUROPILOT_SDK_SHA256  $sdk.tar.gz" | sha256sum -c -
  tar xzf "$sdk.tar.gz" -C "$sdk" --strip-components=1 --wildcards \
    "*/mtk_neuron-${MTK_NEURON_VERSION}-*.whl" "*/mtk_converter-${MTK_CONVERTER_VERSION}+public-cp310-*.whl"
  neuron_whl=$(find "$sdk" -name "mtk_neuron-${MTK_NEURON_VERSION}-*.whl" | head -1)
  converter_whl=$(find "$sdk" -name "mtk_converter-${MTK_CONVERTER_VERSION}+public-cp310-*.whl" | head -1)
  "$W/mtk-venv/bin/pip" install -q --upgrade pip
  "$W/mtk-venv/bin/pip" install -q "$(grep -E '^torch==' requirements/mtk-tools.txt)" --index-url https://download.pytorch.org/whl/cpu
  "$W/mtk-venv/bin/pip" install -q -r requirements/mtk-tools.txt "$neuron_whl" "$converter_whl"
  rm -rf "$sdk" "$sdk.tar.gz"
  "$W/mtk-venv/bin/pip" list 2>/dev/null | grep -iE '^(torch|torchao|executorch|transformers|mtk.converter|mtk.neuron) '
  src="$W/executorch"; rm -rf "$src"; git init -q "$src"
  git -C "$src" remote add origin https://github.com/pytorch/executorch.git
  git -C "$src" sparse-checkout set examples/mediatek
  git -C "$src" fetch -q --depth 1 origin "$EXECUTORCH_COMMIT"
  git -C "$src" checkout -q FETCH_HEAD
  for patch in third_party/executorch/patches/mediatek-*.patch; do git -C "$src" apply "$PWD/$patch"; done
  # A swap file, as the workflow's runners had: calibration peaks above RAM for the largest windows.
  if ! swapon --show | grep -q swapfile; then
    sudo fallocate -l 96G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile >/dev/null && sudo swapon /swapfile
  fi
  echo SETUP_DONE
  exit 0
fi
model=${2:?hf id} soc=${3:?MT6989} window=${4:?4096} precision=${5:?A16W8}
cd "$W/execupack"
name=$(echo "$model" | tr '/' '_')-$soc-$window-$precision
# Work and the datasets cache on the data disk when there is one: qwen.py's calibration writes an
# Arrow cache of ~80 GB for Qwen3-0.6B at 4k into ~/.cache by default, and four exports in
# parallel filled a 300 GB boot disk on 2026-11-15. Inside the work dir it goes when the job does.
base=$W; [ -d /data ] && base=/data
out="$W/out/$name"; work="$base/work/$name"
export HF_DATASETS_CACHE="$work/hf-datasets"
rm -rf "$work"; mkdir -p "$out" "$work"
MTK_PRECISION=$precision "$W/pipe-venv/bin/python" -m pipeline export-mtk "$model" --soc "$soc" --revision main \
  --out "$out" --work "$work" --context "$window" --tool-python "$W/mtk-venv/bin/python" \
  --examples "$W/executorch/examples/mediatek" > "$W/out/$name.log" 2>&1
rc=$?
rm -rf "$work"
echo "EXPORT_DONE $name rc=$rc"
exit $rc
