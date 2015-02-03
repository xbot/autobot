#!/bin/bash

RESOLUTION="160x120"
FPS="30"

while getopts "r:f:" arg
do
    case $arg in
        r)
            RESOLUTION=$OPTARG
            break
            ;;
        f)
            FPS=$OPTARG
            break
            ;;
    esac
done

export LD_LIBRARY_PATH="$LD_LIBRARY_PATH:/usr/local/lib"
/usr/local/bin/mjpg_streamer -i "input_uvc.so -y -n -r $RESOLUTION -f $FPS" -o "output_http.so -w /usr/local/www"
