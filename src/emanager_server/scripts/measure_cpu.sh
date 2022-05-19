#!/system/bin/sh

date > /sdcard/Download/log.txt
echo "App launch mode:"$1 >> /sdcard/Download/log.txt

installed=0
while [ $installed -eq 0 ]; do
	installed=$(pm list packages -f | sed -e 's/.*=//' | sed 's/\r//g' | grep -c edu.benchmarkandroid)
	if [ $installed -eq 0 ]; then
		sleep 1
	fi
done

echo "Application is installed" >> /sdcard/Download/log.txt

if [ "$1" == "automatic" ]; then

	monkey -p edu.benchmarkandroid -c android.intent.category.LAUNCHER 1

	launched=0
	while [ $launched -eq 0 ]; do
		launched=$(dumpsys window windows | grep -E mCurrentFocus | grep -c edu.benchmarkandroid)
		if [ $launched -eq 0 ]; then
			sleep 1
		fi
	done

	echo "Application is launched" >> /sdcard/Download/log.txt

	# Dumps to /sdcard/window_dump.xml
	# Check that we are capturing the main Window
	captured=0
	while [ $captured -eq 0 ]; do
		uiautomator dump | grep -oP '[^ ]+.xml'
		captured=$(cat /sdcard/window_dump.xml | grep -c "START!")
		if [ $captured -eq 0 ]; then
			sleep 3
		fi
	done
	
	echo "Application main window captured" >> /sdcard/Download/log.txt

fi

# If monitor already running, do not start
COUNT=$(lsof /sdcard/Download/measure_cpu.sh 2>> /sdcard/Download/errors.txt | wc -l)
if [ "$COUNT" -eq 2 ]; then
	echo "Monitor already running" >> /sdcard/Download/log.txt
else
	> /sdcard/Download/cpu-usage-sample-1.txt
	> /sdcard/Download/cpu-usage-sample-2.txt
	while true
	do
		cat /proc/stat | head -1 > /sdcard/Download/cpu-usage-sample-1.txt
		sleep 1
		cat /proc/stat | head -1 > /sdcard/Download/cpu-usage-sample-2.txt 
		sleep 1
	done
fi
