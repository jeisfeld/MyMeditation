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

Both settings apply to live playback, including device TTS. Live speed settings are remembered per cloud voice. Settings captured in an already rendered single MP3 are part of that file and cannot be changed during its playback.

## Configuration and defaults

The configuration page contains System instruction, Query template and AI settings. The model/provider/voice dropdowns save immediately. **More** opens the full dialog for manual model IDs, delivery instructions and ElevenLabs voice management; that dialog uses Save/Cancel.

First launch supplies missing prompts, a topic and the original 15-second pause default. Later starts preserve existing user values. The UI defaults to English and uses German on German-language devices. Prompt text is user data and is not automatically translated when the UI language changes.

## Save and restore text meditations

Open **Saved meditations** and press **Save current meditation**. Enter a name and confirm Save; Cancel adds nothing. This stores a snapshot of:

- the name;
- the system instruction and query template;
- the topic and meditation text.

This original text-save operation does not generate audio or capture the playback speed, pause duration or selected AI model/voice. You can save several snapshots with the same name; choose descriptive names to distinguish them.

Tap a saved entry's **name** to restore its prompts, topic and text into the current draft. This changes the current configuration as well as the meditation text. It does not automatically start speaking. Go to Meditation to read, edit or play the restored text.

Drag the handle at the far right to change saved order. Use the pencil immediately before Trash to rename an entry. Use Trash to delete, then confirm; Cancel keeps it. Deleting one entry does not remove the others or reset their order. Renaming preserves the associated text and audio.

The name filter matches part of a name without case sensitivity. **Audio only** limits the list to entries with a completed local recording. Clear both filters to enable drag reordering. Filters do not delete hidden entries.

## Recorded audio and background playback

The save icon beside Speak creates an offline MP3 with the current cloud speech settings. It is separate from saving a text snapshot. Supported providers and export progress/cancellation are described in [AI integration](../AI-INTEGRATION.md).

Entries with audio show Play and Share before Edit and Trash. Play uses the local file; Share opens Android's share sheet. Restoring a saved name still restores text/prompts; use its Play icon to listen to the recording. The current single-MP3 player does not highlight individual sentences or change the recorded pauses.

Playback continues with the screen locked. Temporary navigation interruptions pause and then resume audio when focus returns; live speech repeats the interrupted segment, while MP3 playback continues at the paused audio position. Manual pause/stop or headphone disconnection cancels automatic resume. Notification taps reuse the app and open the relevant playback tab.

Saved data and recordings are private app data. Uninstalling the app removes them. Share an MP3 to retain a copy outside the app; large recordings are excluded from cloud backup. There is no text-library import/export screen.
