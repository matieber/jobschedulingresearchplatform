
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


myConnect $1

measure_cpu_PID=$(adb -s $1:5555 shell lsof /sdcard/Download/measure_cpu.sh | grep "measure_cpu" | tr -s ' ' | cut -d' ' -f2)
if [ ! -z "$measure_cpu_PID" ]; then
adb -s $1:5555 shell kill $measure_cpu_PID
    echo "measure_cpu.sh PID: $measure_cpu_PID killed"
else
    echo "Nothing to do. measure_cpu.sh process is not running at the device"
fi

myDisconnect
