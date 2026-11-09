# Phone D: r40.
cd /data/local/tmp/sd
pkill -f "sh trig39.sh"
echo "$(date +%s) follow40 start" >> follow40.log
sh chain40.sh; echo "$(date +%s) chain40 exit $?" >> follow40.log
pkill -f "sh trig39.sh"; echo "$(date +%s) follow40 done" >> follow40.log
