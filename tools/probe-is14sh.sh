#!/bin/sh
set -eu

out=${1:-is14sh-probe.txt}
adb=${ADB:-adb}
out_dir=$(dirname "$out")
if [ "$out_dir" != "." ]; then
    mkdir -p "$out_dir"
fi

{
    echo "# J2ME-Loader IS14SH probe $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "## build"
    for prop in ro.build.version.sdk ro.build.version.release ro.product.model \
        ro.product.device ro.product.cpu.abi ro.product.manufacturer; do
        printf '%s=' "$prop"
        "$adb" shell getprop "$prop" | tr -d '\r'
    done
    echo "## memory"
    "$adb" shell dumpsys meminfo | sed -n '1,80p' | tr -d '\r'
    echo "## graphics"
    "$adb" shell dumpsys SurfaceFlinger | sed -n '1,100p' | tr -d '\r'
    echo "## package"
    "$adb" shell pm list packages | grep 'ru.playsoftware.j2meloader' || true
    echo "## key capture"
    echo "Run: adb shell getevent -lt /dev/input/event*"
    echo "Record keyCode/scanCode/action for 0-9, *, #, D-pad, Enter, Call, End, Back, Mail, Browser."
} > "$out"

echo "Wrote $out"
