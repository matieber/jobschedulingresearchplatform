#!/bin/sh

while true
do
	sample1=$(cat /proc/stat | head -1)
	cpu1_str=$(echo $sample1 | cut -d" " -f2-4,6-8 | tr " " "+")
	cpu1=$(awk "BEGIN {print $cpu1_str}")
	idle1=$(echo $sample1 | cut -d" " -f5)
	sleep 1

	sample2=$(cat /proc/stat | head -1)
	cpu2_str=$(echo $sample2 | cut -d" " -f2-4,6-8 | tr " " "+")
	cpu2=$(awk "BEGIN {print $cpu2_str}")
	idle2=$(echo $sample2 | cut -d" " -f5)

	timestamp=$(date +%s)
	date_format=$(date '+%F %H:%M:%S.%3N')
	cpu_usage=$(awk "BEGIN {print ($cpu2 - $cpu1) / (($cpu2 + $idle2) - ($cpu1 + $idle1))}")	
	echo $date_format,$timestamp,$cpu_usage >> $1
	sleep 1
done
