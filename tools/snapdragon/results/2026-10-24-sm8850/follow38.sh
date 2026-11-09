# Phone D: after follow35 (r33-B last), r38. Waits for follow35's last line.
cd /data/local/tmp/sd
until grep -q "follow35 done" follow35.log; do sleep 20; done
echo "$(date +%s) follow38 start" >> follow38.log
sh chain38.sh; echo "$(date +%s) chain38 exit $?" >> follow38.log
