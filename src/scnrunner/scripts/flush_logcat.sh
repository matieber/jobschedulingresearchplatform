#!/bin/bash

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


#$1: filename where logcat should be flushed to
#$2: device ip

myConnect $2

adb logcat -d ConnectionHandler:D *:S > $1

myDisconnect
