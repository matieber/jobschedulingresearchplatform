#!/bin/bash

function myConnect {
	while true; do
		conn=$(adb connect $1:5555)
		notconnected=$(echo $conn | grep -c unable)
		alreadyConnected=$(echo $conn | grep -c already)

		# Unable to locate smartphone in the network
		if [ $notconnected -gt 0 ]; then
			return 1
		fi
		# Smartphone is already connected;
		# So we ensure we have successfully connected via ADB
		if [ $alreadyConnected -gt 0 ]; then
			return 0
		fi
		
		sleep 1
	done
}

function myDisconnect {
	adb disconnect
}

function myWaitScreenOnActivity {
	while true; do
		isScreen=$(adb -s $1:5555 shell dumpsys window windows | grep -E mCurrentFocus | grep -c ScreenOnActivity)
		if [ $isScreen -eq 0 ]; then
			break
		fi
		sleep 1
	done
}

function stopAttachedDevice {
	# $1 DeviceIp
	myConnect $1
	if [ "$?" -eq 0 ]; then
		adb -s $1:5555 shell sh -T- /sdcard/Download/stop_measure_cpu.sh
		myWaitScreenOnActivity $1
		adb -s $1:5555 pull /sdcard/window_dump.xml /tmp/view-$1.xml
		coords=$(perl -ne 'printf "%d %d\n", ($1+$3)/2, ($2+$4)/2 if /text="END"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/' /tmp/view-$1.xml)
		adb -s $1:5555 shell input tap $coords
		sleep 1
		myDisconnect $1
	fi
}
	
function killInstaller() {
	ID=$(pgrep -f install_apk.sh)
	if [ ! -z "$ID" ]; then
		kill -9 $ID
	fi
}

killInstaller
for device in $*; do
	echo "Stopping device $device"
	stopAttachedDevice $device
done
