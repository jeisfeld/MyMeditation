# Architecture and baseline contracts

The historical baseline is `cbd677d`, the final SDK-only commit before the direct AI integration. This guide describes how its retained functionality is implemented **now**. The removed `HttpSender`/coachat.de endpoint is not a supported runtime dependency and has no new regression tests.

## Main components

| Responsibility | Current implementation |
| --- | --- |
| First-run values, application context, message localization | `Application` |
| Tab navigation, notification destination delivery | `MainActivity`, Navigation component, `mobile_navigation.xml` |
| Prompts and template validation | `ConfigurationFragment`, `ConfigurationViewModel` |
| Editable topic/text, request action, pause/position controls | `MeditationFragment`, `MeditationViewModel` |
| Literal `@TEXT@` substitution | `MeditationPrompt` |
| Generated text request/response | `AiClient`, `AiProvider`, `AiProtocol`; replaces the former web relay |
| Sentence/segment boundaries | `SpeechSegments`; punctuation-based and bounded for provider requests |
| Device TTS and cloud audio, segment progress, cancellation | `MeditationSpeechPlayer` |
| Live audio lifecycle and media controls | `MeditationPlaybackService`; playback outlives the fragment |
| Named text snapshots and saved ordering | `SavedMeditationsFragment`, `SavedMeditationsViewAdapter`, `OfflineMeditations.save` |
| Save/delete confirmation dialogs | `DialogUtil` |
| Android preference compatibility | `PreferenceUtil` |

The activity-scoped ViewModels expose current draft/configuration state via LiveData. Their setters persist preferences and post LiveData updates, so a caller must not immediately read a posted value to construct a request. The Create action reads the visible topic directly. `getStoredData()` refreshes existing ViewModels after restoring a saved entry.

The baseline extraction `MeditationPrompt` contains the existing literal substitution only; it does not change validation, provider routing or request behavior. An absent stored template falls back to the topic itself. A user-entered template must include `@TEXT@` before ConfigurationViewModel will save it.

## Persisted data

Preferences use `PreferenceManager`'s application-default SharedPreferences. Existing key strings must remain compatible: installed users already have them. Resource names and stored strings sometimes differ.

| Data | Stored key/format |
| --- | --- |
| Current prompts | `system_prompt`, `query_template` strings |
| Current topic/text | `meditation_content`, `meditation_text` strings |
| Original pause setting | `pause_duration` integer; used if the newer `pause_seconds` string is absent/invalid |
| Saved order | `saved_meditation_ids`: newline-delimited integer IDs, preserving user order; legacy CRLF is accepted |
| Highest allocated ID | `max_saved_meditation_id`: monotonic integer, not reused after deletion |
| Saved name | `saved_meditation_name[id]` |
| Saved prompts | `saved_system_prompt[id]`, `saved_query_template[id]` |
| Saved topic/text | `meditation_content[id]`, `meditation_text[id]` |

A plain-text save captures four text fields and a name. It does not generate audio, but can attach an already complete sentence recording with matching current settings. The restore action copies those four fields back into current preferences, refreshes both ViewModels, and stops previous playback. Recorded entries additionally restore their synthesis settings and speed while retaining the independently selected pause duration. Editing/deleting/reordering uses saved IDs, not a displayed row's name. Duplicate names therefore remain independent entries.

Saved IDs may link through `sentence_audio_<id>` to an immutable sentence recording under `files/sentence-audio/<identity>/`; several names may share a recording. `SentenceAudioStore` owns manifests and MP3 publication, `SentenceAudio` owns generation/rendering, `SavedAudioLibrary` owns links/settings restoration, and `SentenceAudioAssembler` creates whole MP3s only for sharing. Legacy `files/offline-meditations/<id>.mp3` and `offline_settings_<id>` metadata remain supported; text-only entries remain valid without audio. See [AI integration](../AI-INTEGRATION.md) for recording format, cache, foreground-service and credential details.

## Boundaries of this baseline

Tests characterize retained behavior, not every public helper inherited from the original utility library. In particular, there is no UI for PreferenceUtil's byte-array export/import helpers, so this baseline does not advertise a library backup feature. Device speech tests simulate Android TTS callbacks: they validate sequencing, pauses, cancellation and errors, not voice quality or a vendor engine's implementation. Actual provider HTTP behavior remains an integration concern even where request/response parsing has local tests.
