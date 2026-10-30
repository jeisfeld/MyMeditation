# User guide

## Start with your own text

1. Open **Meditation**. The upper **Meditation content** field describes the topic; the lower **Meditation text** field holds the words that will be spoken.
2. Paste or write a short meditation in **Meditation text**. Creating a meditation with AI is optional.
3. In **Configuration → AI settings**, choose **Device** for speech output. Android's installed TTS engine supplies the voice; its installation, language and voice settings are managed in Android settings.
4. Return to Meditation and press **Speak Meditation**. Use Pause/Play to pause or continue, Previous/Next or the position slider to move between spoken segments, and Stop to finish and return to the start.

Live playback highlights the current segment. While playing or paused, the creation controls are hidden; Stop displays them again. Pausing live speech and resuming may repeat the current segment. Text is split using sentence-ending punctuation, not a language-aware parser, so abbreviations and decimal numbers can also create boundaries.

Draft edits are stored when a field loses focus. Move focus out of an editor before leaving the app if you want to ensure the current draft has been stored. Speak uses the currently visible meditation text; Create uses the currently visible topic even if it still has focus.

## Create meditation text

In **Configuration**, set **System instruction** to describe the desired role, tone or constraints. **Query template** supplies the request and must retain the literal, case-sensitive placeholder `@TEXT@`. Every occurrence is replaced with the topic from Meditation content; punctuation, line breaks and non-English characters in that topic are preserved. A template without `@TEXT@` is not saved; the last valid template remains in use.

For example:

- System instruction: `Guide a gentle, grounded meditation. Use clear, short sentences.`
- Query template: `Write a spoken meditation about @TEXT@. End with a quiet return to the room.`
- Meditation content: `A peaceful walk through a forest`.

Select a text model under AI settings and configure its local API key as described in [AI integration](../AI-INTEGRATION.md). Back on Meditation, press **Create Meditation**. The button is disabled while the request runs. On success, the generated text replaces the draft and playback position returns to the beginning. On failure, the previous meditation text is restored and an error is shown. You can edit generated text before speaking or saving it.

The old coachat.de web relay is no longer used. Cloud text generation needs a network connection and access to the chosen provider. Device speech with an already installed offline voice does not need a provider API key.

## Pause duration and speed

The pause slider sets the gap between spoken segments. The current range is 0–30 seconds: half-second choices through 10 seconds, then whole seconds. The voice-speed slider is most precise around normal speed. Tap either displayed value to enter an exact value; decimal commas and decimal points are accepted.

Both settings apply to live playback, including device TTS. Live speed settings are remembered per cloud voice. Recorded sentence audio uses the same player and adjustable pauses. Changing voice settings or speed selects a new recording identity; matching sentence audio is reused.

## Configuration and defaults

The configuration page contains System instruction, Query template and AI settings. The model/provider/voice dropdowns save immediately. **More** opens the full dialog for manual model IDs, delivery instructions and ElevenLabs voice management; that dialog uses Save/Cancel.

First launch supplies missing prompts, a topic and the original 15-second pause default. Later starts preserve existing user values. The UI defaults to English and uses German on German-language devices. Prompt text is user data and is not automatically translated when the UI language changes.

## Save and restore text meditations

Open **Saved meditations** and press **Save current meditation**. Enter a name and confirm Save; Cancel adds nothing. This stores a snapshot of:

- the name;
- the system instruction and query template;
- the topic and meditation text.

This original text-save operation does not generate audio. If a complete sentence recording already exists with the current text and speech settings, the snapshot is linked to it; otherwise it stays text-only. You can save several snapshots with the same name; choose descriptive names to distinguish them.

Tap a saved entry's **name** to restore its prompts, topic and text into the current draft. This changes the current configuration as well as the meditation text. For audio entries it also restores recorded provider, model, voice, instructions and speed. The current pause duration stays unchanged. Loading stops the previous playback session and does not automatically start speaking. Go to Meditation to read, edit or play the restored text.

Drag the handle at the far right to change saved order. Use the pencil immediately before Trash to rename an entry. Use Trash to delete, then confirm; Cancel keeps it. Deleting one entry does not remove the others or reset their order. Renaming preserves the associated text and audio.

The name filter matches part of a name without case sensitivity. **Audio only** limits the list to entries with a completed local recording. Clear both filters to enable drag reordering. Filters do not delete hidden entries.

## Recorded audio and background playback

Cloud playback automatically retains an MP3 for each spoken sentence/segment. Once every segment has been played with consistent text and voice settings, the app keeps a complete audio meditation in Saved meditations. If you restored a saved text-only meditation, its recording is attached to that entry. If that entry already has different recorded audio, the new recording keeps its name and appears directly beneath it. Replaying the same recording reuses its entry. Unrestored drafts attach to a matching text-only snapshot or reuse matching audio before creating an entry. Seeking past unplayed sentences or mixing voice settings does not create a complete mixed recording. Partial work can be reused later.

There is no separate audio-save button: listening records cloud audio automatically. Device TTS can still speak pasted or generated text, but cannot create these saved audio recordings.

Entries with audio show Play and Share before Edit and Trash. Play opens Meditation and uses the existing sentence controls, highlighting and scrolling. Pauses can be changed during replay. Loading a saved name restores its text, prompts and recorded voice settings/speed. Exact matches play from local sentence files without provider credentials or a network request. Changing text, voice settings or speed selects new audio; the previous saved recording remains intact. The Meditation text heading shows **(offline)** when the complete recording for the current text and voice settings is available locally. Changing pauses does not affect offline availability. Partial recordings may still require an AI connection for missing sentences.

Share asks for the pause between sentences (default **2 seconds**) and assembles a single MP3 locally. This pause is independent of the playback pause preference. Wait for assembly, then use Android's share sheet. Sharing does not synthesize audio again or use provider credit. It can be cancelled through the sharing notification.

On upgrade, old full-length MP3 recordings are deleted from private app storage. Their saved text and names remain; play them with an AI voice to record the sentences again. Existing sentence recordings are preserved. MP3s previously shared outside the app are unaffected.

Playback continues with the screen locked. Temporary navigation interruptions pause and then resume audio when focus returns. Sentence playback repeats the interrupted segment. Manual pause/stop or headphone disconnection cancels automatic resume. Notification taps reuse the app and open the playback source tab.

Recordings are private app data. Deleting an entry removes its link; audio shared by another entry is retained. Unreferenced recordings are cleaned once active work has ended. Uninstalling removes private recordings. Share a completed MP3 to retain a copy outside the app. Large audio files are excluded from cloud backup. There is no text-library import/export screen. More storage/provider details are in [AI integration](../AI-INTEGRATION.md).
