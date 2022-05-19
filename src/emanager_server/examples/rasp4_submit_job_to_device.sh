#!/bin/bash

# This script submits a job to the dewsimserver, given in parameter 1

# $1 benchmark to run
cp $1 /tmp/test.json

curl -s -X POST -H "Content-Type: multipart/form-data" -F "data=@/tmp/test.json" http://localhost:1080/job/Submitter > /tmp/jsonData.json
jobId=$(jq .message /tmp/jsonData.json | tr -d '\"')
echo "Job id: "$jobId
