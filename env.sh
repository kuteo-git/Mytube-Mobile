# Toolchain for this project, and nowhere else.
#
#   source env.sh
#
# ## Why a file instead of ~/.zshrc
#
# A shell profile outlives the thing it points at. This machine's .zshrc carried
# five FLUTTER_ROOT lines aimed at a directory deleted in July, and nobody
# noticed until somebody went looking for disk space. A project's configuration
# should die with the project, so it lives beside it.
#
# ## Why everything is on the external volume
#
# The internal disk has ~13 GiB free and Xcode alone is about 20 GB. That is the
# whole reason, and it has a price worth stating: when /Volumes/Data2 is not
# mounted, this project cannot be built at all — not just the media library, the
# build too. See the charter's risk 1.

if [ ! -d /Volumes/Data2 ]; then
  echo "Data2 is not mounted — nothing here will build." >&2
  return 1 2>/dev/null || exit 1
fi

DEV=/Volumes/Data2/dev

export JAVA_HOME="$DEV/jdk/Contents/Home"
export ANDROID_SDK_ROOT="$DEV/android-sdk"
# The old name. Some Gradle plugins still read it, and disagreeing with itself is
# worse than being verbose.
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export GRADLE_USER_HOME="$DEV/gradle"
# Kotlin/Native's own toolchain and prebuilt klibs, ~1.3 GB. It defaults to
# ~/.konan on the internal disk, which is the one place this project is not
# allowed to grow.
export KONAN_DATA_DIR="$DEV/konan"
# Emulator images live here too. An AVD is several gigabytes and defaults to
# ~/.android on the internal disk.
export ANDROID_AVD_HOME="$DEV/avd"
export ANDROID_EMULATOR_HOME="$DEV/emulator-home"

export PATH="$JAVA_HOME/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"

echo "java   $("$JAVA_HOME/bin/java" -version 2>&1 | head -1)"
echo "sdk    $ANDROID_SDK_ROOT"
echo "gradle $GRADLE_USER_HOME"
echo "konan  $KONAN_DATA_DIR"
