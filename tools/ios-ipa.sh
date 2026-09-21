#!/bin/sh
# Build the iOS release and package it as an IPA for AltStore.
#
# # Why this does not use `xcodebuild -exportArchive`
#
# Export signs. This household's Apple ID is a free one, so the provisioning
# profile Xcode writes lasts **seven days** — so an export is a build step that
# starts failing a week after it last succeeded, for a reason that has nothing
# to do with the code. That was the whole complaint this script answers.
#
# AltStore re-signs whatever it is handed, with the Apple ID on the phone. So
# the signature this build could put on the app is one AltStore throws away.
# An IPA is a zip with `Payload/<App>.app` inside it and nothing else is
# required, so packaging it by hand skips signing entirely and cannot expire.
#
# The archive still signs — `xcodebuild archive` insists — and that is fine:
# it is the *certificate* that signs there, and the certificate is good for a
# year. It is the profile embedded on export that is good for a week.
set -eu

here=$(cd "$(dirname "$0")/.." && pwd)
. "$here/env.sh" >/dev/null 2>&1 || true

out=${1:-$here/dist}
work=${TMPDIR:-/tmp}/mytube-ios-ipa.$$
archive=$work/Mytube.xcarchive

mkdir -p "$work" "$out"
trap 'rm -rf "$work"' EXIT

echo "==> archiving"
xcodebuild \
    -project "$here/iosApp/iosApp.xcodeproj" \
    -scheme iosApp \
    -configuration Release \
    -destination 'generic/platform=iOS' \
    -derivedDataPath "$work/dd" \
    -allowProvisioningUpdates \
    -archivePath "$archive" \
    archive \
    >"$work/archive.log" 2>&1 \
  || { tail -40 "$work/archive.log"; exit 1; }

app=$archive/Products/Applications/Mytube.app
[ -d "$app" ] || { echo "no Mytube.app in the archive"; exit 1; }

echo "==> packaging"
mkdir -p "$work/Payload"
cp -R "$app" "$work/Payload/"
# `cd` into the staging directory so the zip holds `Payload/…` and not the
# whole path from the root of the disk — an IPA with a deeper tree in it is one
# no installer will open.
(cd "$work" && zip -qry "$out/Mytube-release.ipa" Payload)

ls -lh "$out/Mytube-release.ipa"
