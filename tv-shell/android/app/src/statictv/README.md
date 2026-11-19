# StaticTV flavor (fork only)

StaticTV is this fork's own build of the tv-shell APK, for phones, tablets and
TVs. It is **based on [IPTVnator](https://github.com/4gray/iptvnator)**. It has its
own name, application ID (`io.github.tomasmourato.tv`) and icon, so it never
presents itself as IPTVnator, and it installs next to an IPTVnator APK.

The `statictv` product flavor is defined in [`app/fork.gradle`](../../fork.gradle),
which `app/build.gradle` applies with one line. The `tvshell` flavor keeps
upstream's identity (`org.tvshell.iptvnator`) unchanged.

## What this source set overrides

- `res/values*/strings.xml`: the app name and every string in `src/main` that
  names IPTVnator (English and French).
- The launcher icons, the adaptive icon layers, the TV banner and the startup
  mark. The files keep the IPTVnator file names and qualifiers, so they replace
  that artwork instead of shipping next to it. They are placeholder vectors;
  the banner's name is Inter Regular (SIL OFL 1.1) converted to outlines.
- `res/xml/shortcuts.xml`: the settings shortcut with StaticTV's application ID.

## What this source set adds

- `java/…/player/NextLibDecoderProvider.java`, registered in
  `resources/META-INF/services/`: [NextLib](https://github.com/anilbeesetti/nextlib)'s
  FFmpeg decoders as the built-in player's `VideoDecoderProvider`. When the
  device's hardware video decoder takes input and shows nothing (the open-GOP
  H.264 case), the player switches to FFmpeg instead of the platform's software
  decoder. NextLib's FFmpeg audio decoders (AC-3, E-AC-3, DTS, TrueHD) follow
  the device's own in every stage, in place of upstream's AC-3 extension. The
  dependency is in `fork.gradle`; its version pairs with Media3's.
- `assets/tvshell/NOTICES-StaticTV.txt` and `GPL-3.0.txt`: NextLib is GPL-3.0,
  so the StaticTV APK as a whole is GPL-3.0 (see [License](#license)). NextLib
  ships the licences of its native dependencies under `assets/native-dependencies/`.
  It adds about 12.5 MiB of native libraries per ABI.

`tv-shell/tests/statictv-flavor.test.mjs` fails when a rebase brings an
IPTVnator string or image into `src/main` that this source set doesn't override.

The bundled web app (`apps/web`) still shows the IPTVnator name. That needs a
decision before StaticTV is distributed publicly.

## Build

From `tv-shell/android`, with JDK 17, after copying the PWA into
`app/src/main/assets/www` (see [`tv-shell/README.md`](../../../../README.md)):

```bash
# the phone (arm64-v8a, the default ABI)
./gradlew :app:assembleStatictvRelease
# → app/build/outputs/apk/statictv/release/app-statictv-release.apk

# the x86_64 emulators
./gradlew :app:assembleStatictvDebug -Ptvshell.abis=x86_64
# → app/build/outputs/apk/statictv/debug/app-statictv-debug.apk
```

`-Ptvshell.abis` takes a comma-separated list of ABIs. The upstream build is
`:app:assembleTvshellRelease` (`app/build/outputs/apk/tvshell/release/`). The
Media3 FFmpeg (AC-3) extension is built into `tvshell` only.

## License

The IPTVnator code is under the MIT License, and its notice is kept:

> Copyright (c) 2019-2026 4gray
>
> Permission is hereby granted, free of charge, to any person obtaining a copy
> of this software and associated documentation files (the "Software"), to deal
> in the Software without restriction, including without limitation the rights
> to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
> copies of the Software, and to permit persons to whom the Software is
> furnished to do so, subject to the following conditions:
>
> The above copyright notice and this permission notice shall be included in
> all copies or substantial portions of the Software.
>
> THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
> IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
> FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
> AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
> LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
> OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
> SOFTWARE.

The repository copy is [`LICENSE.md`](../../../../../LICENSE.md).

The StaticTV APK includes NextLib (GPL-3.0) and FFmpeg 9.0.1 as NextLib builds
it (LGPL 3.0 or later), so the APK as a whole is distributed under the GNU
General Public License version 3. The repository's own files keep their
licences. The notices and the source links are in
`assets/tvshell/NOTICES-StaticTV.txt`; the licence text is
`assets/tvshell/GPL-3.0.txt`. The `tvshell` build contains no GPL code.
