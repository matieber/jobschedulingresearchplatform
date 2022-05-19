#!/bin/bash

# $1 DeviceIp
# $2 "manual" or "automatic"

function myConnect {
	while true; do
		conn=$(adb connect $1:5555 | grep -c already)
		if [ $conn -gt 0 ]; then
			break
		fi
		sleep 1
	done
	sleep 2
	
}

function myDisconnect {
	adb disconnect
	sleep 3
}

echo "Connecting to $1"
myConnect $1
adb -s $1:5555 push /tmp/serverConfig.properties /sdcard/Download
adb -s $1:5555 push ./scripts/measure_cpu.sh /sdcard/Download
adb -s $1:5555 push ./scripts/stop_measure_cpu.sh /sdcard/Download
myConnect $1

echo "Starting monitor on $1"
adb -s $1:5555 shell nice -n -20 sh -T- /sdcard/Download/measure_cpu.sh $2

myDisconnect $1
