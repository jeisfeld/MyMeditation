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
| Timed activity pauses, Unicode/decimal parsing, validation, offsets, cancellation/seeking, saved replay and MP3 silence | `MeditationPausesTest`, `DeviceTimedPauseTest`, `SentenceTimedPauseTest`, `SentenceAudioStoreTest`, `SentenceAudioAssemblerTest`; prompt enrichment in `AiProtocolTest` and invalid-draft UI in `OriginalWorkflowBaselineTest` |
| Sentence storage/identity/completion, reuse, restoration, automatic retention, mixed-setting protection and deletion | `SentenceAudioStoreTest`, `SavedAudioLibraryTest`, `SentenceReplayTest`, `SentencePlaybackServiceTest` |
| Share assembly, pauses, cancellation and UI routing/filtering | `SentenceAudioAssemblerTest`, `SentenceAudioUiTest` |
| Provider requests/responses, retries, voice lists/profiles, localization, MP3 encoding, focus recovery, filters | Existing HTTP/audio/i18n/filter suites |

The tests assert visible behavior or persistence contracts rather than pinning the original default prompt wording or recreating a retired web endpoint. They do not prove voice quality, device audio routing or live provider availability. There is no claimed line-coverage percentage.

## Device tests

With a disposable emulator/test device connected:

```sh
./gradlew :app:connectedDebugAndroidTest
```

Current device suites are `OfflineAudioRendererTest`, `SentenceAudioDeviceTest`, `NotificationNavigationTest`. They exercise real platform decoding/speed/assembly and PendingIntent/activity lifecycle. They require no provider keys. Instrumentation installs/runs the app and the audio tests create and remove temporary private files; use a test installation rather than your personal meditation library.

Compiling `assembleDebugAndroidTest` does **not** run these tests. Robolectric TTS callbacks also do not replace a listening check on an actual device.

## Manual release checks

Run the basic workflow in English and German. Include a portrait phone, landscape, and larger system font settings.

| Check | Expected result |
| --- | --- |
| Fresh installation, then a restart after editing prompts/topic | Missing defaults appear initially; user edits survive restart after focus is moved out of the editor |
| Paste several sentences, a final unpunctuated line and an empty text; use device speech | Spoken text matches the draft, nonempty trailing text is retained, empty input starts no audio |
| Start at a nonzero position; pause/resume; seek; stop | Position follows the intended segment, pause stops speech, Stop restores creation controls and position zero |
| Change pause duration; set zero, fractional and long pauses | Gaps match the chosen value without losing subsequent text |
| Play `Take three more breaths. [wait 18s] Continue.` in device/cloud speech, then saved replay and Share | A silent 18-second activity pause occurs regardless of speech speed or ordinary gap; the command is never spoken |
| Pause, seek or interrupt audio during `[wait 300s]`; try leading/trailing/consecutive commands and invalid `[wait 1,5s]` | Controls cancel old timers; interrupted pauses restart; invalid drafts show an error; Bluetooth remains connected through long pauses |
| Generate English and German meditations with empty/custom system instructions | Activity commands use the same `[wait Ns]` syntax; stored prompts stay unchanged; review realistic timing and placement |
| Device TTS unavailable or engine initialization fails | Playback stops with an error; meditation text is retained |
| Create successfully with one configured provider, then deliberately cause a request failure | Success replaces the draft; failure restores the previous draft and re-enables Create |
| Edit topic without changing focus, then press Create | Request considers the newly visible topic |
| Save, cancel another save, restore, reorder, cancel deletion, confirm deletion | Correct snapshot/order is retained; cancellation changes nothing; other entries remain intact |
| Lock the screen or let navigation interrupt playback, then tap the notification | Audio survives backgrounding and recovers according to the documented focus rules; notification returns to the existing app and correct tab |

Cloud creation and listening checks require a locally configured provider key and may incur API charges. Never add real keys to test fixtures, logs or Git.

## Validation environment

Timed-pause validation uses the configured SDK 37, AGP 9.5.0, Gradle 9.6.0 and JDK 21 directly, without downgrading build settings or dependencies. In this sandbox, an ignored Gradle init script under `work/pause-validation.gradle` redirects the JVM test `user.home` to `work/test-user` so Robolectric can write its runtime-download lock and cache, and configures the session proxy for its Maven downloads. The app build, lint and complete local JVM suite run with this setup. Device tests are compiled; real-device listening, Bluetooth behavior and live AI generation remain manual checks.

Sentence recording regression tests also cover upgrade cleanup, restored-source attachment, same-named variant insertion and deduplication, and the offline heading reacting to exact text/settings while ignoring pauses.

`KeyboardEditingLayoutTest` dispatches IME insets through the activity and real page layouts, checking expanded meditation/topic/prompt editors, hidden/restored navigation and controls, cursor preservation, and the existing focus-based prompt saving. Physical keyboard behavior, keyboard animations and real-device portrait/landscape rendering remain manual checks.

`PageInsetsTest` covers system-bar insets consumed by AppCompat before page dispatch: the original window bars and tab space must remain reserved in normal mode and after keyboard dismissal. The top header allowance must also remain while editing; only the bottom tab space is released for the keyboard.

`PromptTemplatesTest` covers independent query validation, exact Unicode/whitespace persistence, stable IDs, name conflicts, non-destructive failed updates, sorting, removal and malformed entries. `PromptTemplatesUiTest` covers saving a visible draft, selecting/persisting it, recreation, separate libraries, rename/delete/update confirmations, invalid query feedback and compact German headers on a narrow screen. Existing keyboard-layout checks also run against the new prompt headings.

`PauseGuidanceUiTest` checks the default Few pauses selection, stable preference values across navigation/recreation, preservation of prompt/draft text, and German heading/dropdown placement on a 320 dp phone. `AiProtocolTest` verifies all three pacing instructions across every provider, including no-activity wording for No pauses. Live model adherence and the perceived amount of silence remain listening checks.

Saved pacing regression tests cover independent named entries, legacy fallback, automatic audio snapshot capture, shared audio with different saved pacing selections and restoring the visible dropdown. Dropdown layout checks use native font measurements and verify no ellipsis for the German pacing/provider labels on a 320 dp phone. Shared compact spinner backgrounds reserve 16 dp for a 10 dp arrow inset 2 dp from the end, while selected text rows explicitly clear themed start/end padding; popup rows keep their normal touch spacing. Real-device font scaling, dark mode and RTL rendering remain visual checks.

Saved pacing restoration also exercises the actual Saved meditations title-click and tab-return workflow. The pacing spinner disables Android view-state saving: its selection comes from preferences, so stale navigation state cannot override a freshly restored entry or write the old selection back to preferences.

`FilteredMeditationDragTest` exercises adapter moves through actual name/audio filters: upward/downward target placement among interleaved hidden rows, successive drag steps, shared audio links, reopening, empty/single results, invalid positions and preserving a hidden entry added after filtering. The full saved-ID list must always retain every hidden entry; physical handle dragging and edge autoscroll remain device checks.

`SettingsBackupTest` covers exact Unicode prompt/template/voice/profile round trips, provider-specific remembered settings, optional scalar reset and collection preservation, preserving meditations/recording links/sessions, credential exclusion and rejection of invalid/versioned/oversized/non-allowlisted settings. `SettingsFilesUiTest` exercises Android file-picker requests/callbacks, export draft isolation and activity recreation, import confirmation/cancellation, invalid-file feedback and refreshing prompt/template UI. Verify real local/cloud document providers and storage errors on a device; Robolectric stream callbacks do not exercise a vendor document provider.

`SettingsMergeTest` covers prompt content deduplication, conflicting names/IDs, preserving voice names/speeds, retaining old/imported custom model choices, version-1 compatibility and idempotent repeated imports. File-import UI coverage also checks that a pre-existing local template survives alongside the imported template.

Skipped-wait offline regressions cover automatic completion with valid audio for every spoken segment while leaving leading, intervening and trailing waits unplayed, real service Next actions across waits, preservation of the missing-speech-file requirement and recovery of older blocked caches. Wait segments have no MP3 file and never enter provider synthesis/prefetch.

Offline readiness now depends on complete generated speech files, including prefetched/unplayed clips. `SentenceAudioStoreTest`, `SavedAudioLibraryTest` and `SentencePlaybackServiceTest` verify creation-only publication, older-cache recovery, missing-file invalidation and separate voice identities. `OfflineCompletionPlayerTest` uses a controlled producer with real storage/player scheduling to test the three-missing threshold, ignoring waits/valid clips, natural-end completion with released focus, explicit Stop/settings cancellation and non-disruptive background failures. No provider endpoints or keys are used. Actual provider latency, screen-off completion and device notification controls remain listening/device checks.
