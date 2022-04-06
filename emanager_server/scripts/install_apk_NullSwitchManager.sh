#!/bin/bash

DEVICES_PROCESSED=1
LAST_DEVICE_ID="none"

function waitDisconnectDevice {
	LAST_DEVICE_ID=$1
	echo "Disconnect $LAST_DEVICE_ID device from USB!"
	while true; do
		LINES=$(adb devices | grep -v unauthorized | wc -l)
		if [ "$LINES" -eq 3 ]; then
			break
		fi
		sleep 1
		echo "Disconnect $LAST_DEVICE_ID device from USB!"
	done
	echo "Device $LAST_DEVICE_ID dettached. Starting app on device now."
}

function waitNewDevice {
	local LINES=2
	while true; do
		sleep 1
		LINES=$(adb devices | wc -l)
		if [ "$LINES" -eq 3 ]; then
			LAST_DEVICE_ID=$(adb devices | tail -2 | head -1 | cut -f1)
			COUNT=$(cat /tmp/registered_devices | grep -c $LAST_DEVICE_ID)
			if [ "$COUNT" -gt 0 ]; then
				echo "Device $LAST_DEVICE_ID already registered. Please connect another device."
			fi
			if [ "$COUNT" -eq 0 ]; then
				echo $LAST_DEVICE_ID >> /tmp/registered_devices
				break
			fi
		fi
	done

	while true; do
		local LAST_DEVICE_IP=$(adb shell ip addr show wlan0 | grep "inet\s" | awk '{print $2}' | awk -F'/' '{print $1}')
		if [ $? -eq 0 ]; then
			break
		fi
	done
	echo "Device IP is $LAST_DEVICE_IP"

	adb tcpip 5555
	sleep 5	
	adb install -r $2"/app-debug.apk"
	adb connect $LAST_DEVICE_IP #Connect before usb is unplugged is a fix for an issue of SamsungA02 device
	waitDisconnectDevice $LAST_DEVICE_ID
	./scripts/launch_monitor.sh $LAST_DEVICE_IP $1

	if [ "$1" == "automatic" ]; then
		./scripts/tap_start_button.sh $LAST_DEVICE_IP
	fi
}

function saveServerIp {
	touch /tmp/serverConfig-base.properties
	ip=$(getMyIP)
	echo "httpAddress="$ip > /tmp/serverConfig-base.properties
	httpPort=$(cat serverConfig.json | jq .server.httpPort)
	echo "httpPort="$httpPort >> /tmp/serverConfig-base.properties
}

function getMyIP {
    local _ip _myip _line _nl=$'\n'
    while IFS=$': \t' read -a _line ;do
        [ -z "${_line%inet}" ] &&
           _ip=${_line[${#_line[1]}>4?1:2]} &&
           [ "${_ip#127.0.0.1}" ] && _myip=$_ip
      done< <(LANG=C /sbin/ifconfig)
    printf ${1+-v} $1 "%s${_nl:0:$[${#1}>0?0:1]}" $_myip
}

saveServerIp

# Por ahora, el uso es igual: pluguear móvil por móvil.
# Para automatizar: 
# 1) esperar wave devices
# 2) por cada device conectado, instalar APK, detectar USB de c/u, y cerrar USB datos
# 3) ir a 1)

echo "Installing apk in device(s)."
rm -rf /tmp/registered_devices
touch /tmp/registered_devices

ENERGY_HARDWARE=$(jq .benchmark.energyHardware serverConfig.json | tr -d '\"')
MAX_DEVICES=$(jq .benchmark.energyHardwareDefinitions.$ENERGY_HARDWARE.maxSupportedDevices serverConfig.json)
echo "Max devices supported by current energy hardware: "$MAX_DEVICES
if [ "$1" != "" ]; then
	MAX_DEVICES=$1
fi
echo "Max devices selected for the test: "$MAX_DEVICES
echo $MAX_DEVICES > /tmp/max_devices_dewsim

# "manual" or "automatic"
APP_LAUNCH_MODE=$(jq .benchmark.appLaunchMode serverConfig.json | tr -d '\"')
APP_APK_FOLDER=$(jq .server.apkFolder serverConfig.json | tr -d '\"')

while true; do
	echo "You can now plug device number $DEVICES_PROCESSED"
	cat /tmp/serverConfig-base.properties > /tmp/serverConfig.properties
	echo "slotId="$DEVICES_PROCESSED >> /tmp/serverConfig.properties	
	waitNewDevice $APP_LAUNCH_MODE $APP_APK_FOLDER
	echo "Device "$LAST_DEVICE_ID" initialized."
	if [ "$DEVICES_PROCESSED" -eq "$MAX_DEVICES" ]; then
		break
	fi
	let DEVICES_PROCESSED=DEVICES_PROCESSED+1
done
