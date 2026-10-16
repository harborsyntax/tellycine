# FFmpeg audio decoders

Many phones and TV boxes have no decoder for some of the sound formats IPTV channels use, so
those channels would play without sound. This module adds software decoders for them, which the
player uses only when the device has none of its own:

- Dolby Digital (AC-3) and Dolby Digital Plus (E-AC-3)
- DTS
- Dolby TrueHD (and MLP)
- MPEG audio layers I and II (MP1, MP2), common on broadcast channels

It's Media3's FFmpeg extension ([androidx/media](https://github.com/androidx/media) 1.6.1,
`libraries/decoder_ffmpeg`), which Google doesn't publish prebuilt:

- `src/main/java`: its audio decoder classes, unchanged (Apache License 2.0).
- `natives`: its JNI glue and FFmpeg build script, unchanged (Apache License 2.0), and
  `build.sh`, which builds them.
- `src/main/jniLibs`: `libffmpegJNI.so` for each ABI, built from [FFmpeg](https://ffmpeg.org)'s
  source by `natives/build.sh`. [`BUILT.txt`](BUILT.txt) says which FFmpeg, NDK and decoders,
  with the libraries' checksums.

## Licences

FFmpeg is licensed under the [GNU Lesser General Public License 2.1](https://github.com/FFmpeg/FFmpeg/blob/n6.0.1/COPYING.LGPLv2.1)
or later. It's built without any of its GPL or nonfree parts. Its source is at
[github.com/FFmpeg/FFmpeg](https://github.com/FFmpeg/FFmpeg/tree/n6.0.1) (the release in
`BUILT.txt`). FFmpeg lives in its own library, `libffmpegJNI.so`: to use a changed FFmpeg, build
it with `natives/build.sh` pointed at your copy and put the result in `src/main/jniLibs`.

## Rebuilding

The [FFmpeg decoders](../.github/workflows/ffmpeg.yml) workflow runs `natives/build.sh` when
anything in `natives` changes (or from the Actions tab) and commits the libraries and
`BUILT.txt` to the same branch. To build locally instead, on Linux with an NDK r26, CMake and
make:

```bash
ANDROID_NDK_HOME=/path/to/ndk/26.3.11579264 ffmpeg/natives/build.sh
```

To change the decoders or FFmpeg's version, edit `DECODERS` or `FFMPEG_TAG` and
`FFMPEG_COMMIT` in `build.sh`.
