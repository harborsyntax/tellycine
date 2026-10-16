#!/usr/bin/env bash
#
# Builds libffmpegJNI.so, Media3's FFmpeg audio decoder, for each ABI into
# ffmpeg/src/main/jniLibs, from FFmpeg's source at a pinned release. LGPL only (no GPL or
# nonfree parts), with just the decoders for sound Android devices may not have one for.
# Then writes ffmpeg/BUILT.txt: what was built, from what, and the libraries' checksums.
#
# Run by .github/workflows/ffmpeg.yml, which commits the results. Needs ANDROID_NDK_HOME (an
# NDK r26, as Media3 1.6.1 is tested with), cmake, make and git.
set -euo pipefail

FFMPEG_TAG=n6.0.1
FFMPEG_COMMIT=c41ff724ede7da657762d61097e26fac296c53bf
# FFmpeg's names for them: Dolby Digital (Plus), DTS, Dolby TrueHD (and MLP), and MPEG audio
# layers I to III (Media3 asks for "mp3" for all three).
DECODERS=(ac3 eac3 dca truehd mlp mp1 mp2 mp3)
ABIS=(armeabi-v7a arm64-v8a x86 x86_64)
MIN_SDK=26

HERE="$(cd "$(dirname "$0")" && pwd)"
MODULE="$(dirname "$HERE")"
NDK="${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to an Android NDK r26}"
STRIP="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# Media3's build files expect FFmpeg in jni/ffmpeg, beside them.
mkdir -p "$WORK/jni"
cp "$HERE/CMakeLists.txt" "$HERE/ffmpeg_jni.cc" "$HERE/build_ffmpeg.sh" "$WORK/jni/"
git clone --quiet --depth 1 --branch "$FFMPEG_TAG" https://github.com/FFmpeg/FFmpeg.git "$WORK/jni/ffmpeg"
actual="$(git -C "$WORK/jni/ffmpeg" rev-parse HEAD)"
if [[ "$actual" != "$FFMPEG_COMMIT" ]]; then
  echo "FFmpeg $FFMPEG_TAG is $actual, not $FFMPEG_COMMIT" >&2
  exit 1
fi

# FFmpeg's static libraries for each ABI, into jni/ffmpeg/android-libs.
(cd "$WORK/jni" && ./build_ffmpeg.sh "$WORK" "$NDK" linux-x86_64 "$MIN_SDK" "${DECODERS[@]}")

# Media3's JNI glue, linked with them into one library per ABI.
for abi in "${ABIS[@]}"; do
  cmake -S "$WORK/jni" -B "$WORK/build/$abi" \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$abi" \
    -DANDROID_PLATFORM="android-$MIN_SDK" \
    -DCMAKE_BUILD_TYPE=Release
  cmake --build "$WORK/build/$abi" -j "$(nproc)"
  mkdir -p "$MODULE/src/main/jniLibs/$abi"
  "$STRIP" --strip-unneeded -o "$MODULE/src/main/jniLibs/$abi/libffmpegJNI.so" "$WORK/build/$abi/libffmpegJNI.so"
done

ndk_version="$(sed -n 's/^Pkg.Revision *= *//p' "$NDK/source.properties")"
{
  echo "libffmpegJNI.so, built by ffmpeg/natives/build.sh. See ffmpeg/README.md."
  echo
  echo "FFmpeg $FFMPEG_TAG ($FFMPEG_COMMIT), LGPL 2.1 or later:"
  echo "  https://github.com/FFmpeg/FFmpeg/tree/$FFMPEG_TAG"
  echo "Media3 1.6.1 decoder_ffmpeg JNI glue (Apache 2.0), in ffmpeg/natives"
  echo "NDK $ndk_version, API $MIN_SDK"
  echo "Decoders: ${DECODERS[*]}"
  echo
  echo "SHA-256:"
  (cd "$MODULE/src/main/jniLibs" && sha256sum */libffmpegJNI.so | sed 's/^/  /')
} > "$MODULE/BUILT.txt"
cat "$MODULE/BUILT.txt"
