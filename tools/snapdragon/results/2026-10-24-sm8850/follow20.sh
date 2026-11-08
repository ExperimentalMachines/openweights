# Starts the thread test (chain19.sh) if the ladder (chain20b.sh) ends at its heat or cool-down limit,
# which skips the hand-off. After LADDER_DONE chain20b starts chain19 itself; after a failure (STOPPED)
# nothing more runs.
cd /data/local/tmp/sd
while ps -A -o ARGS | grep -q '^sh chain20b.sh'; do sleep 10; done
if grep -qE 'LADDER_HEAT_LIMIT|LADDER_COOL_LIMIT' chain20.log && ! grep -qE 'LADDER_DONE|STOPPED' chain20.log; then
  echo "$(date +%s) follow20 starts chain19 after $(grep -oE 'LADDER_(HEAT|COOL)_LIMIT' chain20.log)" >> chain20.log
  sh chain19.sh
fi
