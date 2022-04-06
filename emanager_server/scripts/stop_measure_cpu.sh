#!/system/bin/sh

Pid=$(lsof /sdcard/Download/measure_cpu.sh 2>> /sdcard/Download/errors.txt | grep measure_cpu | grep -v COMMAND | sed -e's/  */ /g' | cut -d' ' -f2)
kill -TERM $Pid 2>> /sdcard/Download/errors.txt

isScreen=$(dumpsys window windows | grep -E mCurrentFocus | grep -c ScreenOnActivity)
while [ $isScreen -eq 1 ]; do
	input keyevent KEYCODE_BACK
	sleep 1
	isScreen=$(dumpsys window windows | grep -E mCurrentFocus | grep -c ScreenOnActivity)
done

# Clean up configuration, output and job result files
rm /sdcard/Download/run-*.txt 2>> /sdcard/Download/errors.txt
rm /sdcard/Download/results-*.zip 2>> /sdcard/Download/errors.txt
rm /sdcard/Download/cpu-usage-sample-1.txt 2>> /sdcard/Download/errors.txt
rm /sdcard/Download/cpu-usage-sample-2.txt 2>> /sdcard/Download/errors.txt
rm /sdcard/Download/measure_cpu.sh 2>> /sdcard/Download/errors.txt
rm /sdcard/Download/stop_measure_cpu.sh 2>> /sdcard/Download/errors.txt
rm /sdcard/Download/serverConfig.properties 2>> /sdcard/Download/errors.txt
rm /sdcard/Download/gpu-usage.txt 2>> /sdcard/Download/errors.txt

