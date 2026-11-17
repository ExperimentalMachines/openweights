#!/system/bin/sh
# What an unrooted streamed device exposes to the shell user: chip, memory, clocks, thermal
# sensors and cooling devices, GPU drivers, vendor NPU libraries, network tools.
# Serial numbers and network identifiers are left out on purpose.
sec() { echo; echo "=== $1"; }
sec props
for p in ro.soc.model ro.soc.manufacturer ro.hardware ro.board.platform ro.product.model ro.product.device \
  ro.build.version.release ro.build.version.sdk ro.build.version.security_patch ro.vendor.build.fingerprint \
  ro.hardware.vulkan ro.hardware.egl ro.product.cpu.abilist ro.vndk.version ro.build.type; do
  echo "$p=$(getprop $p)"; done
getprop | grep -i -E "apu|neuro|npu|nnapi|mtk.*ai" | grep -v -i -E "serial|mac|imei" | head -40
sec id; id; getenforce 2>&1
sec mem; head -5 /proc/meminfo; grep -E "Swap" /proc/meminfo
sec cpu; nproc; cat /sys/devices/system/cpu/online /sys/devices/system/cpu/possible /sys/devices/system/cpu/isolated 2>&1
grep -E "CPU part|Features" /proc/cpuinfo | sort | uniq -c
for p in /sys/devices/system/cpu/cpufreq/policy*; do echo "$p cpus=$(cat $p/related_cpus 2>&1) min=$(cat $p/cpuinfo_min_freq 2>&1) max=$(cat $p/cpuinfo_max_freq 2>&1) smax=$(cat $p/scaling_max_freq 2>&1) cur=$(cat $p/scaling_cur_freq 2>&1) gov=$(cat $p/scaling_governor 2>&1)"; done
sec thermal_zones
for z in /sys/class/thermal/thermal_zone*; do echo "$(basename $z) $(cat $z/type 2>&1) $(cat $z/temp 2>&1)"; done | head -150
sec trips
for z in /sys/class/thermal/thermal_zone*; do for t in $z/trip_point_*_temp; do [ -e "$t" ] && echo "$(basename $z) $(cat $z/type 2>/dev/null) $(basename $t) $(cat $t 2>&1) $(cat ${t%_temp}_type 2>&1) hyst=$(cat ${t%_temp}_hyst 2>&1)"; done; done | head -200
sec cooling
for c in /sys/class/thermal/cooling_device*; do echo "$(basename $c) $(cat $c/type 2>&1) cur=$(cat $c/cur_state 2>&1) max=$(cat $c/max_state 2>&1)"; done | head -120
sec bindings
ls -l /sys/class/thermal/thermal_zone*/ 2>/dev/null | grep cdev | head -60
sec thermalservice; dumpsys thermalservice 2>&1 | head -80
sec battery; dumpsys battery 2>&1 | grep -v -i serial
sec gpu
ls -l /vendor/lib64/libOpenCL* /vendor/lib64/egl/ /vendor/lib64/hw/vulkan* /system/lib64/libOpenCL* /vendor/lib64/libGLES_mali* 2>&1 | head -30
cat /vendor/etc/public.libraries.txt 2>&1
ls /sys/class/misc/mali0/device/ 2>&1 | head -40
ls /sys/kernel/gpu /sys/kernel/ged /sys/kernel/ged/hal 2>&1 | head -60
for f in /sys/kernel/gpu/gpu_clock /sys/kernel/gpu/gpu_freq_table /sys/kernel/gpu/gpu_busy /sys/kernel/gpu/gpu_model /sys/kernel/ged/hal/current_freqency /sys/kernel/ged/hal/gpu_utilization /sys/kernel/ged/hal/total_gpu_freq_level_count /proc/gpufreqv2/gpufreq_status; do echo "$f: $(cat $f 2>&1 | head -12)"; done
dumpsys SurfaceFlinger 2>/dev/null | grep -i -E "GLES|vulkan" | head -5
sec npu
ls -l /system_ext/lib64/ 2>/dev/null | grep -i -E "neuron|apu|nnapi" | head -40
ls -l /vendor/lib64/ 2>/dev/null | grep -i -E "neuron|apu|apusys|nnapi|npu|enn" | head -60
ls -l /system/lib64/ 2>/dev/null | grep -i -E "neuron|apu" | head
for f in /system/etc/public.libraries*.txt /system_ext/etc/public.libraries*.txt /product/etc/public.libraries*.txt; do echo "-- $f"; cat $f 2>&1; done
ls -l /dev/apusys* /dev/apu* /dev/mtk* 2>&1 | head -20
ls -lZ /dev/apusys 2>&1
service list 2>/dev/null | grep -i -E "neuron|apu|nnapi|neuralnetworks|apuware" | head -20
sec net
which curl wget nc toybox busybox 2>&1; toybox 2>&1 | tr ' ' '\n' | grep -E "^(wget|nc|netcat|curl|xz|gzip|tar|taskset|nproc|sha256sum)$" | tr '\n' ' '; echo
ping -c 2 -W 3 huggingface.co 2>&1 | tail -3
sec storage; df -h /data/local/tmp /sdcard 2>&1
sec power; dumpsys power 2>/dev/null | grep -E "mWakefulness|mStayOn|mIsPowered|mPlugType" | head; settings get global stay_on_while_plugged_in
sec perf; ls /sys/kernel/tracing 2>&1 | head -5; cat /proc/sys/kernel/perf_event_paranoid 2>&1; getprop security.perf_harden
sec sched; cat /proc/self/status | grep -E "Cpus_allowed_list"; taskset -p $$ 2>&1; cat /proc/self/cgroup 2>&1 | head -5
sec done
