#!/bin/sh
# Install the signed release on a television and compile it ahead of time.
#
# usage: tools/tv-install.sh <adb serial>   e.g. 10.25.113.114:34583
#
# # Why the compile
#
# `adb install` leaves the app at `status=verify`: every method interpreted or
# JIT-compiled as it is first run, until the television's nightly maintenance
# compiles it — and every install starts that wait again. The Play Store
# compiles at install from the baseline profile; a sideloaded APK gets nothing.
# Measured on the household's Chromecast, Home's first scroll after a cold
# start: 52% janky frames and a 350ms frame at p99 before, 31% and 89ms once
# compiled; the first Down in the player, 350ms → 57ms. What was reported as
# "slow for a moment, then fine" was the interpreter.
#
# `speed` rather than `speed-profile`: the profile covers the libraries'
# hot paths and none of this app's own code until it has been run, which is
# the slowness being removed. Whether the nightly job later swaps `speed` for
# `speed-profile` has not been checked; the last line prints the status, so
# `dumpsys package dexopt` a day later answers it.
#
# Installed over the existing app (`-r`), never uninstalled first: the
# household's profile choice and settings live in its data.
set -eu

serial=${1:?usage: tools/tv-install.sh <adb serial>}
here=$(cd "$(dirname "$0")/.." && pwd)
. "$here/env.sh" >/dev/null 2>&1 || true

apk=$here/composeApp/build/outputs/apk/release/composeApp-release.apk
[ -f "$here/keystore.properties" ] || { echo "keystore.properties missing: the release would be unsigned" >&2; exit 1; }

echo "==> building the release"
(cd "$here" && ./gradlew :composeApp:assembleRelease -q)

echo "==> installing on $serial"
adb -s "$serial" install -r "$apk"

echo "==> compiling ahead of time (about a minute on a Chromecast)"
adb -s "$serial" shell cmd package compile -m speed -f com.mytube.app
adb -s "$serial" shell dumpsys package dexopt | grep -A2 '\[com.mytube.app\]' | tail -1
