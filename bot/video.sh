#!/bin/bash

export LD_LIBRARY_PATH="$LD_LIBRARY_PATH:/usr/local/lib"
/usr/local/bin/mjpg_streamer -i "input_uvc.so -y -n -r 160x120 -f 30" -o "output_http.so -w /usr/local/www"
