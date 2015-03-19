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

# Uncomment this line when mjpg-streamer is not installed in the standard path.
# export LD_LIBRARY_PATH="$LD_LIBRARY_PATH:/usr/local/lib"

# Uncomment this line for cameras which do not support mjpeg format.
# /usr/local/bin/mjpg_streamer -i "input_uvc.so -y -n -r $RESOLUTION -f $FPS" -o "output_http.so -w /usr/local/www"

# Uncomment this line for cameras which support mjpeg format.
# /usr/local/bin/mjpg_streamer -i "input_uvc.so -n -r $RESOLUTION -f $FPS" -o "output_http.so -w /usr/local/www"

/usr/bin/mjpg_streamer -i "input_uvc.so -n -r $RESOLUTION -f $FPS" -o "output_http.so -w /usr/share/mjpeg-streamer/www"
