#!/system/bin/sh
# Runs on the phone, detached, so a dropped tunnel cannot stop it. The final code: the test models, the engine's and the app's device tests, then the 160 questions as shipped.
D=/data/local/tmp/sd; P=io.github.alpharomercoma.openweights.debug; E=/data/local/tmp/openweights/eval; R=io.github.alpharomercoma.openweights; M=/sdcard/Android/data/$R/files/models
cd $D; svc power stayon true; am force-stop $R
echo "$(date +%T) chain start" >> suite.log
$D/dl-reg.sh
mkdir -p $E; cp $M/* $E/; cp $D/decisions.json $E/decisions.json; chmod 755 /data/local/tmp/openweights $E; chmod 644 $E/*
pm install -r -t --user 0 $D/f-app.apk >> queue.log 2>&1
pm install -r -t --user 0 $D/f-test.apk >> queue.log 2>&1
pm install -r -t --user 0 $D/f-engine-test.apk >> queue.log 2>&1
dumpsys package $P | grep -m1 versionCode >> queue.log 2>/dev/null
cmd deviceidle whitelist +$P >/dev/null; cmd deviceidle whitelist +$P.test >/dev/null; cmd deviceidle whitelist +io.github.alpharomercoma.openweights.core.engine.test >/dev/null
C=io.github.alpharomercoma.openweights.core.engine
CLASSES=$C.ReasoningBudgetOnDeviceTest,$C.LlamaCppEngineTest,$C.ToolCallingTest,$C.PrefixReuseOnDeviceTest,$C.ToolLoopReuseOnDeviceTest,$C.PrefillReuseTest,$C.SlidingWindowOnDeviceTest,$C.GpuOffloadTest,$C.MultimodalTest
echo "$(date +%T) engine start" >> suite.log
am instrument -w -r -e class $CLASSES $C.test/androidx.test.runner.AndroidJUnitRunner > inst-engine-final.txt 2>&1
echo "$(date +%T) ENGINE_DONE" >> suite.log
A=io.github.alpharomercoma.openweights.ui.chat
am instrument -w -r -e class $A.ToolTurnOnDeviceTest,$A.HarnessSmokeTest,$A.LongConversationOnDeviceTest $P.test/androidx.test.runner.AndroidJUnitRunner > inst-app-final.txt 2>&1
echo "$(date +%T) APP_DONE" >> suite.log
$D/run-shipped.sh final-pte LFM2.5-1.2B-Instruct-8da4w-gptq-4k.pte 4096 > /dev/null 2>&1
$D/run-shipped.sh final-lfm25 LFM2.5-1.2B-Instruct-Q4_K_M.gguf 32768 > /dev/null 2>&1
$D/run-shipped.sh final-lfm3 LFM3-1.7B-Q4_K_M.gguf 32768 > /dev/null 2>&1
echo DONE_ALL >> suite.log
