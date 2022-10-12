#!/bin/bash

function filter_detect_time {
    outfile=$1"_"$2
    echo "$2" > $outfile
    cat $3*/*/results.csv | grep $1 | cut -d"," -f6 >> $outfile
    echo $outfile
}


function build_detect_time_files {
    outputfiles=""
    tempfiles=""
    for dev in Xiaomi_Redmi_Note_7 samsung_SM_A305G;do

        tempfiles="$tempfiles $(filter_detect_time $dev "quantized_input32_4threads" "scn001")"
        tempfiles="$tempfiles $(filter_detect_time $dev "non_quantized_4threads" "scn002")"
        tempfiles="$tempfiles $(filter_detect_time $dev "quantized_input32_1thread" "scn003")"
        tempfiles="$tempfiles $(filter_detect_time $dev "non_quantized_1thread" "scn004")"

        paste -d, $tempfiles > $dev"_models_perf_data.csv"
        outputfiles="$outputfiles "$dev"_models_perf_data.csv"
        rm $tempfiles
        tempfiles=""
    done
    echo $outputfiles
}

function build_loadbalancing_datafile {
        #file record example:
        #quantized_input32_4threads,Round Robin,makespan(milliseconds)
        echo "Model Version,Load Balancing,Makespan (mm:ss)" > loadbalancing_data.csv
        for lb in RoundRobin PullBased; do
            for model_ver in quantized_input32_4threads non_quantized_4threads quantized_input32_1thread non_quantized_1thread; do
                makespan=$(cat *$model_ver*$lb/*/elapsed_times.csv | grep SCN_EXEC_TIME | cut -d"," -f2)
                echo "$model_ver,$lb,$makespan" >> loadbalancing_data.csv
            done
        done
        echo loadbalancing_data.csv
}


function plot_model_perf {

    filenames=$(build_detect_time_files)

    for dataset in $(echo $filenames); do
        python3 dist.py $dataset
    done
}


function plot_loadbalancing_perf {
    build_loadbalancing_datafile
    python3 groupedbar.py
}


#plot_model_perf
plot_loadbalancing_perf
