#!/system/bin/sh
# After the app-flag A/B: fetch the vision model, then run the engine tests on both APKs again,
# v0.8.0 first this time so each build has once run first, plus the vision test.
cd /data/local/tmp/sd
while ! grep -q AB_DONE ab2.log; do sleep 10; done
for u in LiquidAI/LFM2.5-VL-3B-GGUF/resolve/main/LFM2.5-VL-3B-Q4_0.gguf LiquidAI/LFM2.5-VL-3B-GGUF/resolve/main/mmproj-LFM2.5-VL-3B-Q8_0.gguf; do
    f=${u##*/}; curl -sSL --retry 3 -o gguf/$f.part https://huggingface.co/$u && mv gguf/$f.part gguf/$f
    echo "$(date +%T) $f $(stat -c %s gguf/$f)" >> dl.log
done
ln -f gguf/LFM2.5-VL-3B-Q4_0.gguf /data/local/tmp/openweights/vl.gguf
ln -f gguf/mmproj-LFM2.5-VL-3B-Q8_0.gguf /data/local/tmp/openweights/mmproj.gguf
chmod 644 gguf/*.gguf /data/local/tmp/openweights/*.png
mv app.log app-run1.log; mv inst-base.txt inst-base-1.txt; mv inst-080.txt inst-080-1.txt
mv logcat-base.txt logcat-base-1.txt; mv logcat-080.txt logcat-080-1.txt
sed -e 's/,\$C.GpuOffloadTest$/,$C.GpuOffloadTest,$C.MultimodalTest/' app_tests.sh > app_tests2.sh
ORDER="080 base" sh app_tests2.sh
