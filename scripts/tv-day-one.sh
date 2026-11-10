#!/usr/bin/env bash
# Day-one checks for the TCL 85QM7L Pro. Read-only on the TV: it only reads and saves info to your computer.
# Usage: TV_HOST=192.168.1.50 scripts/tv-day-one.sh
set -euo pipefail
: "${TV_HOST:?Set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50}"
OUT="backups/$(date +%Y-%m-%d_%H%M)"
mkdir -p "$OUT"

echo "Connecting to $TV_HOST (approve the prompt on the TV the first time)..."
adb connect "$TV_HOST:5555" >/dev/null
adb -s "$TV_HOST:5555" wait-for-device
A="adb -s $TV_HOST:5555 shell"

echo "Model and Android version"
$A getprop ro.product.model | tee "$OUT/model.txt"
$A getprop ro.build.version.release | tee -a "$OUT/model.txt"
$A getprop ro.build.fingerprint | tee -a "$OUT/model.txt"

echo "Saving the installed and disabled package lists (your restore reference)"
$A pm list packages -f > "$OUT/packages-all.txt"
$A pm list packages -d > "$OUT/packages-disabled.txt"
$A cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME > "$OUT/current-home.txt" || true
echo "  current Home: $(tail -1 "$OUT/current-home.txt")"

echo "Display: resolution, density and refresh rates"
$A wm size | tee "$OUT/display.txt"
$A wm density | tee -a "$OUT/display.txt"
$A dumpsys display | grep -iE "mode|fps|refresh" | head -20 >> "$OUT/display.txt" || true

echo "Memory (decides wallpaper_true_color)"
$A cat /proc/meminfo | head -3 | tee "$OUT/memory.txt"

echo "Screenshot of the current screen"
$A screencap -p /sdcard/day-one.png
adb -s "$TV_HOST:5555" pull /sdcard/day-one.png "$OUT/day-one.png" >/dev/null
$A rm /sdcard/day-one.png

echo "Done. Everything is in $OUT"
