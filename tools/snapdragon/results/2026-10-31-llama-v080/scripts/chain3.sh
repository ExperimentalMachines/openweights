#!/system/bin/sh
# Runs the reversed-order speed pass after the second engine-test run (chain2.sh) has finished.
cd /data/local/tmp/sd
while ! { [ -f app-run1.log ] && grep -q APP_DONE app.log 2>/dev/null; }; do sleep 10; done
sh ab3.sh ab3.log
