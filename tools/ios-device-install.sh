#!/bin/sh
# Build the iOS release and install it on the household's phones over the
# network, signed by Xcode itself.
#
#   tools/ios-device-install.sh                       every phone in ios-devices
#   tools/ios-device-install.sh <udid> [bundle id]    one phone
#
# # Why this exists beside the IPA
#
# The household's phones were kept signed by AltStore, and since September
# Apple has answered AltServer's sign-in with HTTP 503: it refuses any client
# that introduces itself as Xcode without being Xcode. Xcode is not refused.
# With a free Apple ID the profile it writes lasts seven days, so this is meant
# to run every night (a LaunchAgent at 02:00) and the week never runs out.
#
# # The list of phones
#
# `ios-devices` in the repository's root, one phone per line, `#` for comments:
#
#   <udid>  <bundle id>  # who it belongs to
#
# The UDID is `xcrun devicectl list devices`'s Identifier. The bundle id is the
# one AltStore installed the app under, `<bundle id>.<team id>` — built with it,
# this install replaces AltStore's copy and keeps its data (the server address,
# the profile) instead of putting a second, empty Mytube beside it. Left out,
# the project's own id is used. The file is gitignored: UDIDs and team ids are
# the household's, and this repository is public.
#
# A phone that cannot be reached — away, off, another network — is reported
# and skipped; the others are still installed, and the exit status says so.
set -eu

here=$(cd "$(dirname "$0")/.." && pwd)
. "$here/env.sh" >/dev/null 2>&1 || exit 1

list=$here/ios-devices
derived=$here/build/ios-device
mkdir -p "$derived"

profiles=$HOME/Library/Developer/Xcode/UserData/Provisioning\ Profiles

# Xcode reuses a profile until it has expired, not until it is about to: run
# nightly, the app would still die on the seventh afternoon and stay dead until
# the next night. A profile for this app with under two days left is removed
# from Xcode's cache, so the build that follows asks Apple for a fresh week.
retire_expiring_profiles() {
    app_bundle=$1
    now=$(date +%s)
    for profile in "$profiles"/*.mobileprovision; do
        [ -f "$profile" ] || continue
        plist=$(security cms -D -i "$profile" 2>/dev/null) || continue
        app_id=$(echo "$plist" | plutil -extract Entitlements.application-identifier raw -o - - 2>/dev/null) || continue
        case $app_id in *."$app_bundle") ;; *) continue ;; esac
        expires=$(echo "$plist" | plutil -extract ExpirationDate raw -o - - 2>/dev/null) || continue
        left=$(( $(date -j -u -f '%Y-%m-%dT%H:%M:%SZ' "$expires" +%s) - now ))
        if [ "$left" -lt 172800 ]; then
            echo "==> profile for $app_bundle expires $expires; asking for a new one"
            rm -f "$profile"
        fi
    done
}

# One line per phone, `--> <name>: <result>`, for whatever reports the run —
# the nightly job sends these lines to Telegram as its list of phones.
result() {
    echo "--> $1: $2"
}

install_one() {
    device=$1
    bundle=${2:-}
    log=$here/build/ios-device-install-$device.log

    retire_expiring_profiles "${bundle:-com.xtube.com}"

    echo "==> $(date '+%F %T') $device: building"
    # One derived data directory for every phone: after the first, a build is
    # a re-sign for the next phone's profile, not a second Kotlin compile.
    # The destination is the phone itself so Xcode registers it with the team
    # and puts it in the profile.
    xcodebuild \
        -project "$here/iosApp/iosApp.xcodeproj" \
        -scheme iosApp \
        -configuration Release \
        -destination "id=$device" \
        -derivedDataPath "$derived" \
        -allowProvisioningUpdates \
        ${bundle:+PRODUCT_BUNDLE_IDENTIFIER=$bundle} \
        build \
        >"$log" 2>&1 \
      || { tail -20 "$log"; return 1; }

    app=$derived/Build/Products/Release-iphoneos/Mytube.app
    [ -d "$app" ] || { echo "no Mytube.app in $derived"; return 1; }

    echo "==> $(date '+%F %T') $device: installing"
    xcrun devicectl device install app --device "$device" "$app" >>"$log" 2>&1 \
      || { tail -20 "$log"; return 1; }

    expires=$(security cms -D -i "$app/embedded.mobileprovision" 2>/dev/null \
      | plutil -extract ExpirationDate raw -o - - 2>/dev/null || echo unknown)
    version=$(plutil -extract CFBundleShortVersionString raw -o - "$app/Info.plist" 2>/dev/null || echo "?")
    echo "==> $(date '+%F %T') $device: installed $version, signed until $expires"
}

# One line of why, out of a build or install log's tail. A phone that is away
# makes xcodebuild print every destination it does know, which read as the
# reason when the last line was taken.
failure_reason() {
    if echo "$1" | grep -q -E 'Unable to find a (destination|device) matching'; then
        echo "device not reachable"
    elif reason=$(echo "$1" | grep -i -m1 -E '(^|[[:space:]])error:' ); then
        echo "$reason" | sed -E 's/.*[Ee][Rr][Rr][Oo][Rr]:[[:space:]]*//' | cut -c1-160
    else
        echo "$1" | grep -v '^==>' | grep -v '^[[:space:]]*$' | tail -1 | cut -c1-160
    fi
}

# Installs one phone and says how it went in one `-->` line, named by [name]
# (the list's comment) or by its UDID.
report_one() {
    name=$1
    shift
    if out=$(install_one "$@"); then
        echo "$out"
        result "$name" "installed $(echo "$out" | sed -n 's/.*: installed //p')"
    else
        echo "$out"
        why=$(failure_reason "$out")
        result "$name" "FAILED${why:+ — $why}"
        return 1
    fi
}

if [ $# -gt 0 ]; then
    report_one "$1" "$@"
    exit
fi

[ -f "$list" ] || { echo "no $list — see the top of this script"; exit 1; }

# The loop runs in the pipeline's subshell, which cannot set a variable out
# here; a failure leaves a marker file instead.
rm -f "$derived/.failed"
# Whole-line comments and blank lines skipped. A line's own comment is the
# phone's name; the fields before it are read by `set --`, so a line with no
# bundle id is one argument, not an empty second one.
grep -v '^[[:space:]]*#' "$list" | while read -r line; do
    fields=${line%%#*}
    [ -n "$(echo "$fields" | tr -d '[:space:]')" ] || continue
    name=$(echo "$line" | sed -n 's/^[^#]*#[[:space:]]*//p')
    # shellcheck disable=SC2086
    set -- $fields
    report_one "${name:-$1}" "$@" || touch "$derived/.failed"
done

if [ -f "$derived/.failed" ]; then
    rm -f "$derived/.failed"
    exit 1
fi
