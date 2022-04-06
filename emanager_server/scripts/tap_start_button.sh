#!/bin/bash

function myConnect {
	while true; do
		conn=$(adb connect $1:5555 | grep -c already)
		if [ $conn -gt 0 ]; then
			break
		fi
		sleep 1
	done
}

function myDisconnect {
	adb disconnect
}

LAST_DEVICE_IP=$1
rm /tmp/view-$LAST_DEVICE_IP.xml 2> /dev/null
myConnect $LAST_DEVICE_IP
adb -s $LAST_DEVICE_IP:5555 pull /sdcard/window_dump.xml /tmp/view-$LAST_DEVICE_IP.xml
sleep 2
coords=$(perl -ne 'printf "%d %d\n", ($1+$3)/2, ($2+$4)/2 if /text="START!"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/' /tmp/view-$LAST_DEVICE_IP.xml)
adb -s $LAST_DEVICE_IP:5555 shell input tap $coords
sleep 1
myDisconnect $LAST_DEVICE_IP
