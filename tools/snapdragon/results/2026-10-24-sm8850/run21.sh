# After r21 and the pause check: r24, r23, r25, then the held r22 thread chain. Each stops itself on a
# failed gate or run; the next still starts, so a stop costs one experiment, not the reservation.
cd /data/local/tmp/sd
echo "$(date +%s) run21 start" >> run21.log
for c in chain24 chain23 chain25; do sh $c.sh; echo "$(date +%s) $c exit $?" >> run21.log; done
[ -f chain22.hold ] && mv chain22.hold chain22.sh
sh chain22.sh; echo "$(date +%s) chain22 exit $?" >> run21.log
