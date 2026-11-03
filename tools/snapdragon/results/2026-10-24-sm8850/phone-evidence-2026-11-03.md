# Evidence read from the QDC phone (unit 783ee7e5) by adb as root, 2026-11-03, during r16 (Qwen3 1.7B running).

## Cooling devices and the thermal zones bound to them (zone, type, current temp, policy, trip index, trip temp, hysteresis, trip type)
== cooling_device17 thermal-pause-C0 max_state 1
  thermal_zone61 sys-therm-0 temp 77357 policy step_wise trip 2 at 78000 hyst 8000 passive
  thermal_zone90 pmh0110_i_tz temp 84043 policy step_wise trip 0 at 95000 hyst 0 passive
  thermal_zone93 socd temp 0 policy step_wise trip 2 at 90 hyst 0 passive
== cooling_device21 thermal-pause-3C max_state 1
  thermal_zone61 sys-therm-0 temp 77328 policy step_wise trip 2 at 78000 hyst 8000 passive
  thermal_zone89 pmh0110_g_tz temp 83907 policy step_wise trip 0 at 95000 hyst 0 passive
== cooling_device33 kgsl max_state 17
  thermal_zone36 gpuss-0 temp 89600 policy step_wise trip 2 at 105000 hyst 0 passive
  thermal_zone37 gpuss-1 temp 88800 policy step_wise trip 2 at 105000 hyst 0 passive
  thermal_zone38 gpuss-2 temp 90300 policy step_wise trip 2 at 105000 hyst 0 passive
  thermal_zone39 gpuss-3 temp 88000 policy step_wise trip 2 at 105000 hyst 0 passive
  thermal_zone40 gpuss-4 temp 88400 policy step_wise trip 2 at 105000 hyst 0 passive
  thermal_zone41 gpuss-5 temp 88800 policy step_wise trip 2 at 105000 hyst 0 passive
  thermal_zone42 gpuss-6 temp 91500 policy step_wise trip 2 at 105000 hyst 0 passive
  thermal_zone43 gpuss-7 temp 93500 policy step_wise trip 2 at 105000 hyst 0 passive
  thermal_zone44 gpuss-8 temp 91100 policy step_wise trip 2 at 105000 hyst 0 passive
  thermal_zone45 gpuss-9 temp 91900 policy step_wise trip 2 at 105000 hyst 0 passive
  thermal_zone46 gpuss-10 temp 90300 policy step_wise trip 2 at 105000 hyst 0 passive
  thermal_zone55 pmh0101-bcl-lvl0 temp 0 policy step_wise trip 0 at 1 hyst 0 passive
  thermal_zone56 pmh0101-bcl-lvl1 temp 0 policy step_wise trip 0 at 1 hyst 0 passive
  thermal_zone57 pmh0101-bcl-lvl2 temp 0 policy step_wise trip 0 at 1 hyst 0 passive
  thermal_zone61 sys-therm-0 temp 77357 policy step_wise trip 2 at 78000 hyst 8000 passive
  thermal_zone69 pmih010x-bcl-lvl0 temp 0 policy step_wise trip 2 at 1 hyst 0 passive
  thermal_zone72 pmih010x-bcl-lvl1 temp 0 policy step_wise trip 2 at 1 hyst 0 passive
  thermal_zone75 pmih010x-bcl-lvl2 temp 0 policy step_wise trip 2 at 1 hyst 0 passive
  thermal_zone88 pmh0110_f_tz temp 79698 policy step_wise trip 0 at 95000 hyst 0 passive
  thermal_zone93 socd temp 0 policy step_wise trip 2 at 90 hyst 0 passive
== cooling_device38 cdsp max_state 12
  thermal_zone61 sys-therm-0 temp 77348 policy step_wise trip 2 at 78000 hyst 8000 passive
  thermal_zone93 socd temp 0 policy step_wise trip 2 at 90 hyst 0 passive

## Kernel and scheduler
6.12.38-android16-5-maybe-dirty-4k
CONFIG_NO_HZ_COMMON=y
CONFIG_NO_HZ_IDLE=y
CONFIG_NO_HZ=y
CONFIG_HIGH_RES_TIMERS=y
CONFIG_HZ=250
100

## CPU topology
cpu0 max 3628800 capacity 762
cpu1 max 3628800 capacity 762
cpu2 max 3628800 capacity 762
cpu3 max 3628800 capacity 762
cpu4 max 3628800 capacity 762
cpu5 max 3628800 capacity 762
cpu6 max 4608000 capacity 1024
cpu7 max 4608000 capacity 1024
0-7
8

## Android thermal service (dumpsys thermalservice, now)
Thermal Status: 5
	Temperature{mValue=40.0, mType=2, mName=battery, mStatus=0}
	Temperature{mValue=105.0, mType=0, mName=CPU0, mStatus=3}
	Temperature{mValue=99.6, mType=9, mName=nsp0, mStatus=0}
	Temperature{mValue=61.08536, mType=3, mName=skin, mStatus=5}
	Temperature{mValue=40.0, mType=2, mName=battery, mStatus=0}
	Temperature{mValue=91.1, mType=9, mName=nsp0, mStatus=0}
	Temperature{mValue=64.11168, mType=3, mName=skin, mStatus=5}
	Temperature{mValue=105.4, mType=0, mName=CPU0, mStatus=3}
	CoolingDevice{mValue=6, mType=1, mName=battery}
	TemperatureThreshold{mType=2, mName=battery, mHotThrottlingThresholds=[NaN, NaN, NaN, 80.0, NaN, NaN, 90.0], mColdThrottlingThresholds=[NaN, NaN, NaN, NaN, NaN, NaN, NaN]}
	TemperatureThreshold{mType=9, mName=nsp0, mHotThrottlingThresholds=[NaN, NaN, NaN, 105.0, NaN, NaN, 125.0], mColdThrottlingThresholds=[NaN, NaN, NaN, NaN, NaN, NaN, NaN]}

## One Llama 3.2 3B log line on graph size (log-r14)
sched_reserve: graph nodes  = 874

## Android skin thresholds (dumpsys thermalservice, index = ThrottlingSeverity NONE, LIGHT, MODERATE, SEVERE, CRITICAL, EMERGENCY, SHUTDOWN)
	TemperatureThreshold{mType=3, mName=skin, mHotThrottlingThresholds=[NaN, 48.0, 49.0, 50.0, 60.0, 61.0, 90.0], mColdThrottlingThresholds=[NaN, NaN, NaN, NaN, NaN, NaN, NaN]}

## What sys-therm-0 is (device tree, /proc/device-tree)
model: Qualcomm Technologies, Inc. Canoe QRD + ST54L + Kundu
soc/thermal-zones/sys-therm-0: thermal-sensors = <0x6d0 0x44>
phandle 0x6d0 = soc/arbiter@c400000/spmi@c426000/pmk8850@0/vadc@9000 (compatible qcom,spmi-adc5-gen4)
channel reg 0x44 = node pmk8850_xo_therm, label "pmk8850_xo_therm"
Other sys-therm zones by the same reference: sys-therm-1 0x4b pmk8850_xoagg_therm; sys-therm-2 0x144 pmh0101_skin_therm;
sys-therm-3 0x145 cam_flash; sys-therm-4 0x146 wlan; sys-therm-5 0x147 pa_therm_1; sys-therm-7 0x149 pa_therm_2;
sys-therm-6 0x14b qtm; sys-therm-9 0x2744 batt_therm; sys-therm-11 0x2747 usb; sys-therm-12 0x274c batt_therm_2; sys-therm-8 0x274d wls.
	TemperatureThreshold{mType=0, mName=CPU4, mHotThrottlingThresholds=[NaN, NaN, NaN, 105.0, NaN, NaN, 125.0], mColdThrottlingThresholds=[NaN, NaN, NaN, NaN, NaN, NaN, NaN]}
	TemperatureThreshold{mType=0, mName=CPU7, mHotThrottlingThresholds=[NaN, NaN, NaN, 105.0, NaN, NaN, 125.0], mColdThrottlingThresholds=[NaN, NaN, NaN, NaN, NaN, NaN, NaN]}

## Android thermal status, the two readings (dumpsys thermalservice, captured in this session)
- About 17:38 local, just before trig17 started (its first row 17:39:14, sys-therm-0 68.66 C; the binding read moments earlier showed 68.79 C): "Thermal Status: 3", Temperature{mValue=57.77936, mType=3, mName=skin, mStatus=3}
- About 17:59 local (sys-therm-0 77.31 to 77.36 C in trig17 at 17:58:36 to 17:58:52; the binding read showed 77.36 C): "Thermal Status: 5", Temperature{mValue=64.11168, mType=3, mName=skin, mStatus=5}
