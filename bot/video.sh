#!/bin/bash

export LD_LIBRARY_PATH="$LD_LIBRARY_PATH:/usr/local/lib"
mjpg_streamer -i "input_uvc.so -y -n -r 320x240 -f 10" -o "output_http.so -w /usr/local/www"
