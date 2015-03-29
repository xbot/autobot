#!/bin/bash
# Set the wireless network to work as normal or a router.

WIRELESS_MODE=""

while getopts "s:" arg
do
    case $arg in
        s)
            WIRELESS_MODE=$OPTARG
            ;;
    esac
done

(test -z $WIRELESS_MODE || [[ $WIRELESS_MODE != "normal" && $WIRELESS_MODE != "router" ]]) \
    && echo "Bad wireless network mode." >&2 && exit 1

if [[ $WIRELESS_MODE == "normal" ]]; then
    systemctl stop network-router@wlan0 hostapd dnsmasq
    systemctl disable network-router@wlan0 hostapd dnsmasq
    systemctl start network-wireless@wlan0 wpa_supplicant@wlan0
    systemctl enable network-wireless@wlan0 wpa_supplicant@wlan0
fi

if [[ $WIRELESS_MODE == "router" ]]; then
    systemctl stop network-wireless@wlan0 wpa_supplicant@wlan0
    systemctl disable network-wireless@wlan0 wpa_supplicant@wlan0
    systemctl start network-router@wlan0 hostapd dnsmasq
    systemctl enable network-router@wlan0 hostapd dnsmasq
fi

exit 0
