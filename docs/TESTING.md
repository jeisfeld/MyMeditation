# Testing and baseline coverage

## Scope

The pre-chat feature baseline is commit `cbd677d`: the three tabs, editable drafts, system instruction/query template, meditation creation, device TTS with pauses/position controls, and a named saved-text library with restore, reorder and confirmed deletion. Tests run the retained workflows in the current implementation. The old coachat.de transport is intentionally not revived or tested against a live website.

The baseline adds Robolectric 4.16.1 to the local JVM suite and enables Android resources for unit tests. Android-backed baseline tests use simulated API 35 and an English 411 × 891 dp phone viewport. This exercises app logic, resources, fragments and preferences; it is not a claim that every Android version, layout size or TTS engine has been validated.

No baseline test needs `HttpCredentials`, generates billable audio or calls an AI endpoint. The creation failure test uses an unsupported model, which fails locally before key lookup/network access. The first run of Gradle/Robolectric downloads test dependencies and its instrumented Android runtime from Maven repositories. After those artifacts are cached, tests can run offline.

## Run locally

Use the project's configured Gradle/AGP/SDK and JDK 21, as described in [README](../README.md).

```sh
# All plain-Java and Android-backed local tests
./gradlew :app:testDebugUnitTest

# The Android baseline subset, plus the pure template contract
./gradlew :app:testDebugUnitTest --tests '*BaselineTest' --tests '*MeditationPromptTest'

# Compile the app and device tests, and run lint
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

HTML test results are in `app/build/reports/tests/testDebugUnitTest/index.html`; XML results are in `app/build/test-results/testDebugUnitTest/`. Lint writes `app/build/reports/lint-results-debug.html`.

`testOptions.unitTests.isIncludeAndroidResources` is required: these tests inflate the app's real layouts and dialogs. `BaselineTest` seeds neutral data in Robolectric's isolated application sandbox before each test. The actual app installation, preferences and recordings on a phone are not involved. The tests deliberately advance the main looper for LiveData and speech timing rather than depending on wall-clock sleeps, except a bounded wait for the creation worker to report its local validation error.

## Coverage map

| Retained behavior | Automated coverage |
| --- | --- |
| First-run defaults; preserving user values on later starts | `ApplicationBaselineTest` |
| Legacy saved-ID order/CRLF format, clearing lists, isolated indexed text and Unicode, legacy pause fallback | `PreferenceBaselineTest` |
| Prompts survive reopening; invalid templates retain the previous valid value; restore refreshes existing state | `ConfigurationBaselineTest` |
| Topic/text persistence; fractional and zero pauses; restoration without starting speech | `MeditationBaselineTest` |
| Literal replacement of all `@TEXT@` placeholders, special characters/newlines, missing-template fallback | `MeditationPromptTest` |
| Save and cancel through real dialogs; snapshot of all original fields; restore into both tabs; reorder and delete/cancel; monotonic IDs | `OriginalWorkflowBaselineTest` |
| Edits across navigation/recreation; locally failed generation preserves the old text and takes the visible current topic | `OriginalWorkflowBaselineTest` |
| Device TTS sentence order, pause timing, starting at a selected position, stop during a pause, late callbacks, initialization errors, empty text | `DeviceSpeechBaselineTest` |
| Punctuation/Unicode splitting, trailing text, bounded segments | Existing `SpeechProtocolTest`, `NativeSpeechTest`, `SpokenTextRangesTest` |
| Current pause/speed mappings and playback-position conversion | Existing `PlaybackScalesTest`, `PlaybackRequestTest` |
| Provider requests/responses, retries, voice lists/profiles, localization, MP3 encoding, focus recovery, filters | Existing HTTP/audio/i18n/filter suites |

The tests assert visible behavior or persistence contracts rather than pinning the original default prompt wording or recreating a retired web endpoint. They do not prove voice quality, device audio routing or live provider availability. There is no claimed line-coverage percentage.

## Device tests

With a disposable emulator/test device connected:

```sh
./gradlew :app:connectedDebugAndroidTest
```

Current device suites are `OfflineAudioRendererTest`, `OfflinePlaybackInterruptionTest` and `NotificationNavigationTest`. They exercise real platform decoding, audio-focus requests, foreground playback and PendingIntent/activity lifecycle. They require no provider keys. Instrumentation installs/runs the app and the audio tests create and remove temporary private files; use a test installation rather than your personal meditation library.

Compiling `assembleDebugAndroidTest` does **not** run these tests. Robolectric TTS callbacks also do not replace a listening check on an actual device.

## Manual release checks

Run the basic workflow in English and German. Include a portrait phone, landscape, and larger system font settings.

| Check | Expected result |
| --- | --- |
| Fresh installation, then a restart after editing prompts/topic | Missing defaults appear initially; user edits survive restart after focus is moved out of the editor |
| Paste several sentences, a final unpunctuated line and an empty text; use device speech | Spoken text matches the draft, nonempty trailing text is retained, empty input starts no audio |
| Start at a nonzero position; pause/resume; seek; stop | Position follows the intended segment, pause stops speech, Stop restores creation controls and position zero |
| Change pause duration; set zero, fractional and long pauses | Gaps match the chosen value without losing subsequent text |
| Device TTS unavailable or engine initialization fails | Playback stops with an error; meditation text is retained |
| Create successfully with one configured provider, then deliberately cause a request failure | Success replaces the draft; failure restores the previous draft and re-enables Create |
| Edit topic without changing focus, then press Create | Request considers the newly visible topic |
| Save, cancel another save, restore, reorder, cancel deletion, confirm deletion | Correct snapshot/order is retained; cancellation changes nothing; other entries remain intact |
| Lock the screen or let navigation interrupt playback, then tap the notification | Audio survives backgrounding and recovers according to the documented focus rules; notification returns to the existing app and correct tab |

Cloud creation and listening checks require a locally configured provider key and may incur API charges. Never add real keys to test fixtures, logs or Git.

## Validation environment for this change

This workspace has SDK 36, AGP 8.13.0, Gradle 8.14.3 and JDK 21 available, rather than the repository's configured SDK 37/AGP 9.5.0/Gradle 9.6.0. Validation therefore uses an ignored copy under `work/speech-validation`, with compatibility toolchain/dependency versions. The checked-in build configuration is not downgraded. Robolectric tests execute locally in that copy; device tests are compiled only because no emulator/device is attached. Full verification with the configured toolchain and actual devices remains a separate check.
