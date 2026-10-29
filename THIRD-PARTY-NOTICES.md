Offline audio processing dependencies:

- **Jump3r 1.0.5 / LAME** — LGPL 2.1 or later. Copyright the LAME authors, Kenjiro Uetake and Hanns Holger Rutz. The build includes the unmodified portable `mp3` and `mpg` packages and omits unused desktop JavaSound frontends. Full source and original notices: https://repo.maven.apache.org/maven2/de/sciss/jump3r/1.0.5/jump3r-1.0.5-sources.jar. Project: https://github.com/Sciss/Jump3r. License text: `app/src/main/assets/licenses/jump3r-LGPL-2.1.txt`.
- **AndroidX Media3 1.8.0 / Sonic processing** — Apache License 2.0. Copyright The Android Open Source Project and Sonic contributors. Source: https://github.com/androidx/media. License text: `app/src/main/assets/licenses/media3-Apache-2.0.txt`.

Notices and full license texts are also packaged in the app's `assets/licenses/` directory. The Gradle build resolves these dependencies from Maven repositories and rebuilds the portable encoder JAR, allowing the library to be replaced and the application rebuilt from source.
