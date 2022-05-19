#!/bin/bash

export ID=$(pgrep -f "python3 emanager_server.py") && kill -TERM $ID
adb disconnect
