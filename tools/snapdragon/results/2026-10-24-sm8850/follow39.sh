# Phone D: r39, now that follow38 is done.
cd /data/local/tmp/sd
echo "$(date +%s) follow39 start" >> follow39.log
sh chain39.sh; echo "$(date +%s) chain39 exit $?" >> follow39.log
pkill -f "sh trig39.sh"; echo "$(date +%s) follow39 done" >> follow39.log
