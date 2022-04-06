#!/bin/bash

function init() {
	shouldDeleteProfiles=$(cat serverConfig.json | jq .server.deleteProfilesOnInit)
	if [ "$shouldDeleteProfiles" == "true" ]; then
		profilesFolder=$(cat serverConfig.json | jq .server.profilesFolder | tr -d '"')
		rm -rf $profilesFolder && mkdir -p $profilesFolder
	fi
	apkFolder=$(cat serverConfig.json | jq .server.apkFolder | tr -d '"')
	cp $apkFolder/app-debug.apk .
}

function killInstaller() {
	ID=$(pgrep -f install_apk.sh)
	if [ ! -z "$ID" ]; then
		kill -9 $ID
	fi
}

echo "Specify as parameter the number of devices to use (if different than the max supported by the configured driver"
init $*
python3 emanager_server.py $1
adb disconnect
rm app-debug.apk 2> /dev/null
rm /tmp/view-*.xml 2> /dev/null
