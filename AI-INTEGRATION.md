# Meditation AI integration

Create Meditation calls the selected provider directly over HTTPS. Configure its native model ID in **Configuration → AI settings**. Existing prompts, the `@TEXT@` query template, and saved model selection are retained. Speak Meditation supports device TTS or OpenAI speech.

## Local API keys

In your local `app/src/main/java/de/jeisfeld/mymeditation/http/HttpCredentials.java`, add the constants for whichever providers you use:

```java
package de.jeisfeld.mymeditation.http;

public final class HttpCredentials {
    public static final String CLAUDE_API_KEY = "your-anthropic-api-key";
    public static final String OPENAI_API_KEY = "your-openai-api-key";
    public static final String GEMINI_API_KEY = "your-google-ai-studio-api-key";
    public static final String GROK_API_KEY = "your-xai-api-key";
    public static final String DEEPSEEK_API_KEY = "your-deepseek-api-key";
    public static final String KIMI_API_KEY = "your-moonshot-api-key";
    public static final String QWEN_API_KEY = "your-dashscope-international-api-key";
}
```

Keep existing constants in that class. This exact file is Git-ignored; no real keys are committed. Unused constants may be omitted or empty. Clean checkouts build without the credentials class. A missing selected provider key displays an error specifying the constant to add. Rebuild and reinstall after changing keys.

There are no API key controls in the app. Previously saved device keys are no longer read or used. Credentials come exclusively from `HttpCredentials` and are embedded in the APK, where they can be extracted. Keep builds containing personal keys private.

## Model routing

| Model ID prefix | Provider | Constant | API |
| --- | --- | --- | --- |
| `claude-` | Anthropic | `CLAUDE_API_KEY` | Messages |
| `gpt-`, `chatgpt-`, or o-series such as `o3` / `o4-mini` | OpenAI | `OPENAI_API_KEY` | Responses |
| `gemini-` | Google Gemini | `GEMINI_API_KEY` | generateContent |
| `grok-` | xAI | `GROK_API_KEY` | Chat Completions |
| `deepseek-` | DeepSeek | `DEEPSEEK_API_KEY` | Chat Completions |
| `kimi-`, `moonshot-` | Moonshot / Kimi | `KIMI_API_KEY` | Chat Completions |
| `qwen-`, versioned prefixes such as `qwen3-` / `qwen2.5-` | Alibaba Qwen | `QWEN_API_KEY` | Chat Completions |

Enter a text generation model available to your API account; a recognized prefix does not guarantee that the model exists or supports the selected API. Example IDs include `claude-sonnet-4-5`, `gpt-4.1`, `gemini-2.5-flash`, `grok-4`, `deepseek-chat`, `kimi-k2-0905-preview`, and `qwen-plus`. The existing Claude default is unchanged. Unsupported prefixes are rejected locally rather than sent to another provider. API billing is separate from chat subscriptions.

Kimi uses the international Moonshot endpoint (`api.moonshot.ai`). Qwen uses Alibaba Model Studio's international DashScope endpoint (`dashscope-intl.aliyuncs.com`, Singapore); use keys and models provisioned for that region, rather than China or US regional credentials. DeepSeek uses `api.deepseek.com`. These are direct provider services, not third-party aggregators. Qwen requests disable thinking mode to support non-streaming meditation generation. DeepSeek and Kimi reasoning fields are ignored; only the final answer is used.

System and user prompts are sent only to the selected provider. Requests use its own key and fixed HTTPS endpoint, with redirects disabled. Failed, incomplete, blocked, or empty responses preserve the previous meditation. Provider response bodies and keys are not logged. Reasoning/thought blocks are excluded from the meditation. Text-generation requests are not automatically retried; speech retries are described below.

## Verification

`bash gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest`

Protocol tests cover routing, provider request formats, system prompts, text extraction, reasoning exclusion, and incomplete/blocked/refused responses. On a device, verify real requests for each configured provider, missing keys, unavailable models, network failures, and switching models. No real API keys are needed to build or run protocol tests.

## OpenAI voice output

In **Configuration → AI settings**, choose **Device text-to-speech** (the default) or **OpenAI speech**. Text generation and speech models are independent: for example, Claude can create the meditation and OpenAI can speak it. OpenAI speech reuses `HttpCredentials.OPENAI_API_KEY`; no additional secret or in-app key entry is needed.

OpenAI defaults to `gpt-4o-mini-tts` with voice `coral`, a calm meditation instruction, and adjustable speed (default 1.0, normal pace). The model field also accepts dated `gpt-4o-mini-tts` variants, `tts-1`, and `tts-1-hd`. Legacy `tts-1` models require alloy, echo, fable, onyx, nova, or shimmer and omit the unsupported instructions parameter. Choose another built-in voice from the dialog. The dialog explicitly labels these voices as AI-generated.

Audio is generated per sentence via `https://api.openai.com/v1/audio/speech`. Existing pause duration, pause/resume from the current sentence, and sentence seeking apply to both engines. Long sentences are divided into segments of at most 3500 UTF-16 characters. The next sentence is prefetched during playback; therefore a cancelled session can still incur charges for a request already submitted. Temporary network failures, HTTP 408/429 and selected 5xx responses get up to three total attempts with 1s/2s backoff and Retry-After handling (waits capped at 30s). Authentication, invalid models/voices, and other permanent request errors fail immediately. Pausing cancels retries, including backoff waits. There is no silent fallback to another engine. A request that succeeds at OpenAI but loses its response may be billed again when retried. First playback needs connectivity and API credit. If generation falls behind playback, a network wait can extend the selected pause.

Completed MP3 segments are cached privately in the app's disposable cache, keyed by text, model, voice, instructions, format and speed. Replaying unchanged segments uses the cache without a new API request. Completed audio is bounded to approximately 50 MiB (temporary downloads are additional and each limited to 20 MiB), excluded from Android backup as cache data, and may be cleared by Android or via the app's cache settings. It contains the spoken meditation, so treat the device accordingly. Changing text, voice or speech model generates new audio. Incomplete downloads never become cache entries.

Pausing, seeking and audio focus loss invalidate stale playback callbacks. Playback is now owned by the foreground service described below, independently of the meditation view.

Speech tests cover request compatibility, cache identity and offline reuse, cancellation, and sentence segmentation (including trailing text and long input). The playback and API classes are also compiled against Android API 36. An isolated copy of the complete app also builds and passes lint with the cached SDK 36 / Android Gradle plugin 8.13.0 and matching older AndroidX dependencies; the actual project remains on SDK 37 and its current dependencies. Full application build/lint requires the project's Android Gradle plugin 9.4.1 and SDK 37; this workspace does not have that toolchain. Device validation should include both engines, voice changes, airplane mode with cached/uncached sentences, missing keys, seeking while downloading, pausing during silence/audio, phone-call audio focus loss, and navigating away before a response arrives.

### Speech pace, delivery and voice choice

AI settings now exposes **Speech speed** (`0.25`–`4.0`, default `1.0`) and **Voice instructions**. Try `1.1` or `1.2` if normal pace is still slow. The old built-in instruction to speak slowly is removed. Example style: “Use a feminine voice, warm and sensual, with clear articulation and a steady natural pace,” or “Firm, confident and commanding, with crisp articulation; avoid a theatrical delivery.” The model follows delivery hints approximately, so audition them in the meditation language. Voice instructions apply only to the gpt-4o-mini-tts family; tts-1/tts-1-hd ignore them. Changing speed or effective instructions changes the audio cache identity.

All 13 currently listed OpenAI built-in voices are already available. Perceived gender varies; coral, nova, shimmer, sage and marin are useful candidates for a feminine delivery. Voices do not have an official gender selector. See OpenAI's [voice previews and guide](https://developers.openai.com/api/docs/guides/text-to-speech#voice-options).

The official OpenAI SDK lists `gpt-4o-mini-tts-2025-12-15` as an additional dated snapshot; the model field accepts it already. A dated snapshot is not a guarantee of better sound than the alias. `tts-1-hd` is an older alternative worth comparing (use a compatible voice such as nova), but it lacks style instructions. Gemini TTS and Qwen TTS are now integrated as described below. ElevenLabs is also available as described below and requires its own account/API key. OpenAI model availability and lifecycle can change; check the current provider documentation before selecting a model for long-term use.

## Gemini and Qwen voice output

AI settings now offers Device TTS, OpenAI speech, Gemini speech and Qwen TTS (international). Model and voice selections are kept separately for each provider, including the existing OpenAI selection. Speed and style preferences are shared. Device TTS remains the default.

- **Gemini:** uses `HttpCredentials.GEMINI_API_KEY`, with `gemini-2.5-flash-preview-tts` and `Kore` by default. `gemini-2.5-pro-preview-tts` is another model to compare. This integration uses the Gemini 2.5 native generateContent speech API, not the newer Gemini 3.x Interactions API. The selected Google API account must have access to the model. A curated voice list includes Kore, Aoede, Leda, Zephyr, Sulafat, Puck, Charon, Fenrir, Orus and Gacrux. Delivery instructions are included in the speech prompt. Returned mono PCM is converted to a WAV container for Android playback; the sample rate is taken from the response.
- **Qwen:** uses `HttpCredentials.QWEN_API_KEY`, default model `qwen3-tts-flash`, voice `Cherry`, through DashScope's international Singapore endpoint. Other available voices include Serena, Chelsie and Ethan. Model availability, voice support and languages depend on the provider/region. Use hosted Qwen TTS model IDs, not Qwen-Audio understanding models, self-hosted model names or realtime WebSocket models. For model-supported instruction delivery, select an instruct model such as `qwen3-tts-instruct-flash`; standard Qwen TTS models ignore the style field. The generated audio URL must be an Alibaba storage URL; known HTTP OSS links are upgraded to HTTPS before downloading; provider authorization is never forwarded to that storage download, and redirects are disabled.

For Gemini and Qwen, the speed setting adjusts Android playback speed with pitch preserved; OpenAI continues using its API speed parameter. Changing local speed reuses cached native-provider audio. Gemini/Qwen caches are separate from the existing OpenAI cache and share an additional bounded native-audio cache. Qwen segments are limited to 500 characters, Gemini to 1500, so provider text limits do not receive arbitrarily long sentences. Sentence controls operate on these smaller segments where necessary. Generation, playback and cache reuse follow the same pause/seek/cancellation flow and bounded retry policy.

No additional keys are needed if your existing Gemini and international Qwen credentials already have speech model access. These accounts can bill speech separately from text generation. There is no live API validation without your credentials: validate model access, voice names and delivery on the device. ElevenLabs requires a separate ElevenLabs API key with speech access; a free or paid plan may be suitable depending on current model access, quotas and intended usage.

## Gemini reliability, Qwen storage links, and ElevenLabs

Gemini empty candidates, empty audio and transient incomplete-generation finishes now use the existing bounded three-attempt retry policy. Explicit safety/policy blocks are not retried. Gemini generation calls are serialized across player instances so prefetch does not issue simultaneous requests to a quota-limited speech model. PCM and valid WAV responses are supported. If an error persists, the exact message helps distinguish model restrictions, quota and playback failures.

Qwen links on recognized `*.aliyuncs.com` storage hosts may arrive with an HTTP scheme. These are upgraded to HTTPS while preserving the signed path/query; no unencrypted download is made. Other hostnames remain rejected, and the error now shows only protocol and hostname to aid diagnosis, without revealing signed URL tokens. This addresses the likely source of the reported unsupported-address failure; other hosts need to be checked against an actual returned hostname.

For ElevenLabs, add this constant inside your local, Git-ignored `HttpCredentials` and rebuild:

```java
public static final String ELEVENLABS_API_KEY = "your-elevenlabs-api-key";
```

Select **ElevenLabs speech** in AI settings and paste a **voice ID** from your ElevenLabs Voices page into the voice field. Use the ID, not a voice's display name. There is no preset ID: access to public, library and personal voices depends on the account. Default model: `eleven_multilingual_v2`; models such as `eleven_v3` or `eleven_flash_v2_5` can also be selected if available to the account. The key needs Text-to-Speech permission and sufficient credit/quota. A free or paid plan may be appropriate depending on provider limits and licensing requirements.

ElevenLabs uses the native `/v1/text-to-speech/{voice_id}` endpoint with `xi-api-key` authentication and MP3 output. It shares native-provider caching, cancellation, retries and local pitch-preserving speed control. Cache identity includes the ElevenLabs voice ID as well as text, model and voice settings. The free-text delivery-instructions field is intentionally not prepended to the spoken text or sent as an unsupported API parameter. For expressive v3 delivery, model-supported audio tags may be included in the meditation itself; other models may read those tags aloud. ElevenLabs live calls require the user's account and have not been tested here.

## Named ElevenLabs voices and speed profiles

In AI settings, select ElevenLabs and press **Add voice**. Enter a friendly name and paste the voice ID once. The voice dropdown then displays the saved name. **Edit** updates the selected voice's name without changing its ID, cached audio or speed. Adding the same ID updates its name; different IDs cannot share the same name. An existing manually entered ID is carried over as “Saved voice 1” (or another available numbered name). Press the main AI settings **Save** button to persist additions, renames and speed changes; cancelling the main dialog discards these edits.

Speech speed is remembered per provider and voice ID for every cloud provider. Switching between voices or providers restores that voice's last saved speed. Renaming an ElevenLabs voice preserves its speed because the identity uses its ID, not its friendly name. New voices start at 1.0. The currently selected voice inherits the previous global speed during the first migration, so the current setup is preserved. Model selection does not reset voice speed. Playback reads the selected voice's speed profile; device TTS behavior is unchanged.

Names, IDs and speed profiles are stored in normal app preferences and participate in the existing preference export. They contain no API keys. Voice availability still depends on the ElevenLabs account; the local library does not automatically fetch the account's voice catalogue.

## Persistent ElevenLabs retries and paid-audio reuse

ElevenLabs transient failures no longer stop after three attempts. Connection failures, timeouts, temporary server responses, rate limits, and empty audio retry until playback is manually paused/stopped or playback is stopped. Backoff grows 1, 2, 4, 8, 16, then 30 seconds and remains bounded at 30 seconds between requests. The playback screen reports the retry count and next delay. Cancellation is checked every 100 ms during backoff, and late callbacks cannot restart stopped playback. Permanent key/model/voice errors still display immediately. Other providers retain their existing bounded retry policy.

ElevenLabs generation is serialized across player instances to avoid competing requests against provider concurrency limits. Up to three upcoming sentences are prefetched. Completed audio is retained in `getNoBackupFilesDir()/native-speech`, separate from Android's disposable cache, with an approximately 256 MiB LRU budget (temporary downloads additional). Older ElevenLabs files in the original cache are migrated on demand before any API call. Model, text, voice ID and effective synthesis settings identify the audio; renaming a voice, changing local playback speed or ignored style text reuses the same recording. Complete audio is reused after pausing, restarting playback and restarting the app. Android cache cleanup does not remove this retained audio; app data removal/uninstallation does. Old recordings can still be evicted at the size limit.

Incomplete responses are not cached: nonempty response bodies and declared Content-Length are checked before atomic publication. A paid request whose response is lost can still be billed again on retry; the app can reuse completed recordings but cannot recover a recording it never received. No automatic retry can repair an invalid key or inaccessible voice, so those errors remain visible.

## Background and screen-locked playback

`MeditationPlaybackService` now owns `MeditationSpeechPlayer`, audio focus and downloads. The fragment starts playback through a foreground-service command and observes immutable service state; destroying or recreating the view does not stop or restart the meditation. Returning to the app restores running state, sentence position and retry status. This applies to device TTS and all cloud engines, including persistent ElevenLabs retries and cached audio.

The manifest declares a non-exported `mediaPlayback` foreground service, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` and `WAKE_LOCK`. It is promoted before requesting audio focus, as required on current Android versions. A CPU-only partial wake lock covers audio, intentional pauses and synthesis/retry waits, without keeping the screen on. It uses ten-minute leases renewed every five minutes only while running; pause, completion, error and destruction release it and cancel renewal.

A native MediaSession and media-style notification expose Previous, Pause/Resume, Next and Stop, plus headset/lock-screen transport controls. Notification content contains a generic title and sentence count/status, not the meditation text. Android media-session notifications are exempt from the Android 13 notification runtime permission requirement, so no extra permission prompt is introduced. Playback pauses on audio focus loss or headphone disconnection. Paused playback releases CPU/audio resources and foreground status; the session stays available for up to ten minutes before the idle service is stopped. The paused notification can restart the service and restore the saved session after idle shutdown. Stop/completion removes the notification. Swiping the app out of Recents does not stop active playback; use notification controls to stop it.

The private saved session stores meditation text, provider, model, voice, speed, style, pause duration and sentence position, never an API key. Notification Resume uses that session; pressing Speak in the app starts the current editor text/settings. Forced app termination is not automatically restarted (`START_NOT_STICKY`), avoiding unsolicited synthesis/API usage. Android force-stop and device reboot still end playback.

All JVM protocol/profile tests and new session serialization/validation tests run without provider credentials. The full service/UI/manifest are build- and lint-checked in the isolated SDK 36 copy. Physical Android validation is still required: play each engine, lock the screen through multiple sentence/pause transitions, background and reopen/recreate the activity, control playback from notification/headset, test retry/cancellation while locked, test focus loss/headphone removal, pause/resume after idle service shutdown, and verify no playback restart after Stop. The actual project remains on SDK 37; its toolchain is not installed in this workspace.

## ElevenLabs voice editing and startup scheduling

Saved ElevenLabs voices are listed alphabetically using locale-aware, case-insensitive collation. Renaming a voice immediately reorders the draft dropdown while preserving selection by voice ID. The compact **Edit** button opens the voice editor, which supports renaming and **Remove voice**. Removal is local: it removes the saved name/ID and its ElevenLabs speed profile, not the provider-account voice or retained paid recordings. Removing the selected voice selects the first remaining voice alphabetically. Removing the last one switches the draft backend to device TTS so the empty library can be saved. The main AI settings Save/Cancel still commits/discards the whole draft.

ElevenLabs now has an ordered generation queue. The current sentence is fetched before any speculative later sentences; prefetch starts only once its recording is available, then continues during playback. This prevents prefetch from taking a provider slot ahead of the first audible sentence, while preserving the one-request concurrency limit, persistent cache and continuous transient retries. The UI displays “Preparing ElevenLabs audio…” during uncached startup.

New voices necessarily need new recordings; the selected model's synthesis time and server/voice startup remain outside the app's control. This implementation still buffers a complete sentence before playing it. The selected model (including the user's eleven_v4 default) is preserved. For a lower-latency tradeoff, select `eleven_flash_v2_5` in the speech model field if available to the account; voice quality/delivery may differ. No paid synthesis is triggered merely by adding or selecting a voice in settings.

## Compact voice dropdown and live AI settings

The voice dropdown now uses compact 40dp rows with reduced vertical padding for all providers. The collapsed selector and provider dropdown remain unchanged.

After the main AI settings Save, the running foreground service applies the newly saved speech provider, model, voice, speed and effective delivery instructions. Output/voice/synthesis changes cancel obsolete generation and restart only the current sentence/segment using the active meditation's original text. If a provider has a different segment limit, the current location is mapped by character offset, including repeated sentences; playback does not jump to the beginning or switch to unrelated editor text. Previously generated recordings remain reusable in the normal provider caches.

For Gemini, Qwen and ElevenLabs, speed-only changes adjust the playing audio locally without another API request or a sentence restart; pending/prepared audio uses the updated speed when it starts. OpenAI API-speed changes require a new recording or matching cache entry, so they restart the current sentence. Device TTS continues using the device engine's rate settings. Saving unchanged effective synthesis settings, renaming a voice, or changing instructions ignored by the selected provider does not interrupt audio.

Cancel does not apply draft changes. Saving while paused does not start the meditation. Resume/notification play refreshes the saved output settings while preserving the saved meditation text and mapped position. The foreground service, notification controls, wake-lock lifetime and background playback remain in place during changes. A newly selected uncached cloud voice can still need generation time before sound resumes.

## English default and German localization

Unqualified `values/` resources remain English. `values-de/` provides the full German UI for German device/app locales: navigation, dialogs, voice settings, saved-meditation messages, notifications/media controls, retry statuses and app-owned validation/provider errors. Android 13+ app-language settings lists English and German via `locales_config.xml`; earlier Android versions follow the device locale. Other languages fall back to English. App dialog buttons use app resources rather than unrelated system-locale labels.

Platform-independent protocol classes use `UiText` with English defaults for JVM tests. `Application` installs a resource-backed translator using application context, with explicit resource references so resource shrinking can retain all messages. Localization never changes API request fields, model IDs, provider/voice identifiers, preference keys, saved names, meditation text or user prompts. Newly seeded delivery-style instructions follow the UI locale; existing saved instructions are preserved.

Speed inputs display the app locale's decimal separator and accept either a comma or a dot. Stored values, JSON numbers and API arguments remain canonical numeric values. The German Add-voice button uses the compact label “Hinzufügen” to fit portrait rows. Resource-coverage tests require a German counterpart for every translatable string/array, matching format placeholders and array lengths, while rejecting translated technical keys. English message fallbacks are checked against their resource values. UI/device locale switching still needs visual verification on Android; the isolated SDK 36 build/lint packages both locales successfully.
