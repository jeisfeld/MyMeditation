# MyMeditation

An Android app for creating, editing, saving and reading personalized meditations. You can also paste your own text and use Android text-to-speech without an AI account.

- [User guide](docs/USER_GUIDE.md): the three tabs and the complete basic workflow.
- [Testing guide and coverage](docs/TESTING.md): local tests, device tests and manual checks.
- [Architecture and saved-data format](docs/ARCHITECTURE.md): where the original functionality lives today.
- [AI and audio integration](AI-INTEGRATION.md): provider keys, models, cloud speech, caching, background playback and offline MP3s.
- [Third-party notices](THIRD-PARTY-NOTICES.md).

## Build

Open the project in Android Studio, or install the SDK/toolchain configured by `app/build.gradle.kts`, `gradle/libs.versions.toml` and `gradle/wrapper/gradle-wrapper.properties` (currently SDK 37, AGP 9.5.0 and Gradle 9.6.0). Use JDK 21. Configure the Android SDK through Android Studio or your local, ignored `local.properties`.

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
```

A clean checkout and the automated baseline tests require no API credentials. To use a cloud provider, add its key to the ignored local `HttpCredentials.java` described in [AI-INTEGRATION.md](AI-INTEGRATION.md). Never commit credentials or an APK containing personal keys.

## Baseline scope

The baseline documents the capabilities present before the AI integration work, using commit `cbd677d` as the historical reference: prompts/templates, creation, editable drafts, device speech and pause/position controls, and saving/restoring/reordering/deleting meditations. Tests run against the current app. They preserve the retained contracts without restoring the removed coachat.de transport or reproducing old bugs. Newer functionality remains covered by the existing AI/audio suites.
