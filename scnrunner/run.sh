#!/bin/bash 


scenario_folder=$1

OLDIFS=$IFS
IFS='
'

#Iterate over scenario folder provided
for scenario in $(find $scenario_folder -iname *.json); do


    # STEP 1 - Configure RESULTS_HOME directory and related log files using information, e.g., scn_id, contained in the
    # scenario descriptor file passed as argument to the script.
    scn_id=$(jq .scn_id $scenario | tr -d '"')
    RESULTS_HOME="../"$(jq .app_dir $scenario | tr -d '"')'/results/'$scn_id'/'$(date | tr ' ' _)
    mkdir -p $RESULTS_HOME
    logfilename=$scn_id'.log'
    logfilepath=$RESULTS_HOME'/'$logfilename
    touch $logfilepath

    # STEP 2 - Update the scenario descriptor file content with log and results paths defined in this script
    #scape special characters / and : contained in the filepath
    #eg: this: ../dogs_finder_app/experiments/dew_scenario_test/mié_08_dic_2021_15:05:30_-03
    # into this: ..\/dogs_finder_app\/experiments\/dew_scenario_test\/mié_08_dic_2021_15\:05\:30_-03\
    scapedvar=$(echo $logfilepath | sed -r 's#/#\\/#g' | sed -r 's#:#\\:#g')
    sed -i "s/\"log_file\"\:.*,/\"log_file\"\:\"$scapedvar\",/g" $scenario
    scapedvar=$(echo $RESULTS_HOME | sed -r 's#/#\\/#g' | sed -r 's#:#\\:#g')
    sed -i "s/\"results_dir\"\:.*,/\"results_dir\"\:\"$scapedvar\",/g" $scenario

    #STEP 3 - Call dew_runner that parses all scenario parameters and runs the test
    start=$(date +%s)
    echo "running $scenario"
    python3 dew_runner.py --scenarioDescriptor=$scenario
    end=$(date +%s)
    elapsed_seconds=$((end - start))
    device=$(jq .processor.hardware_support $scenario | tr -d '"')
    echo "[stats]device,scenario,elapsed_seconds,scenario_descriptor" >> $logfilepath
    echo "[stats]$device,$scn_id,$elapsed_seconds,$scenario" >> $logfilepath

done

IFS=$OLDIFS
