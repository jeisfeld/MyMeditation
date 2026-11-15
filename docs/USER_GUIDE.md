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

For an activity that needs more time, put a timed pause directly in the meditation text:

```text
Take three more comfortable breaths. [wait 18s]
Now notice how you feel.
```

The app stays silent for 18 seconds before continuing. Commands always use `wait` and `s`, including in German or other languages. Durations must be greater than zero and at most 300 seconds; decimal points with up to three decimal places are supported, for example `[wait 2.5s]`. Voice speed and the pause slider do not change these durations. An explicit pause replaces the ordinary gap at that location. Leading, trailing and consecutive commands are supported; consecutive durations add together. Previous/Next and the position slider can select or skip a pause. Pausing or an audio interruption restarts the current pause on resume.

The dropdown to the right of **Meditation content** selects the pacing of newly generated meditations and saves immediately:

- **No pauses / Keine Pausen:** continuous guidance, with no wait commands or activities that require the listener to do something between sentences.
- **Few pauses / Wenige Pausen** (default): wait commands only rarely, preferring brief pauses of 3–8 seconds and avoiding repeated-breath exercises or extended silence.
- **Many pauses / Viele Pausen:** regular pauses for breathing repetitions, reflection and silent activities, with realistic time to complete them.

The choice persists across navigation and restarts. It affects the hidden instructions added to every AI request, including requests with a custom or empty System instruction. Changing it does not rewrite an existing meditation, change its wait commands, or change the ordinary playback pause slider. Saving a meditation stores the selected pacing with it; restoring the entry restores that selection. Older entries without a stored selection use Few pauses. Your editable system instruction, templates and saved prompts remain as entered. Models still choose where to pause and for how long; review those choices when editing. Invalid `wait` commands in an AI response reject the response and preserve the previous meditation. In pasted or edited text they show an error when you press Speak. Commands are never sent to speech synthesis. Saved recordings retain them, and shared MP3s include the requested silence independently of the Share dialog’s ordinary gap setting.

## Configuration and defaults

The configuration page contains System instruction, Query template and AI settings. The model/provider/voice dropdowns save immediately. **Additional settings / Weitere Einstellungen** at the bottom opens the full dialog for manual model IDs, delivery instructions, ElevenLabs voice management and settings files; that dialog uses Save/Cancel.

First launch supplies missing prompts, a topic and the original 15-second pause default. Later starts preserve existing user values. The UI defaults to English and uses German on German-language devices. Prompt text is user data and is not automatically translated when the UI language changes.

When the screen keyboard opens for a meditation topic/text or a system instruction/query template, that text section expands to fill the available page above the keyboard. The bottom tabs and unrelated controls are hidden. Dismiss the keyboard with Back to restore the normal page; your text and cursor remain in place. With a hardware keyboard, the normal layout stays visible.

Each prompt heading in Settings has a **Templates…** dropdown and a pencil button. The two fields have independent template libraries, listed alphabetically. Choosing a template fills that field and saves it as the active prompt; you can edit it normally afterward. The dropdown returns to Templates… when the field no longer matches a saved template.

Open the pencil button to manage that library. **Save current text** asks for a name and captures the visible field, including edits you have just typed. Select a saved template in the management dialog to **Rename**, **Delete**, or **Update selected template** with the current field text. Updating and deleting ask for confirmation. Names must be nonempty and unique within that library. Query templates must contain the literal `@TEXT@`. Renaming/deleting a template leaves the current field and saved meditations unchanged; ordinary edits do not overwrite a stored template. Libraries persist locally across restarts.

## Save and restore text meditations

Open **Saved meditations** and press **Save current meditation**. Enter a name and confirm Save; Cancel adds nothing. This stores a snapshot of:

- the name;
- the system instruction and query template;
- the topic and meditation text;
- the No/Few/Many pauses selection.

This original text-save operation does not generate audio. If a complete sentence recording already exists with the current text and speech settings, the snapshot is linked to it; otherwise it stays text-only. You can save several snapshots with the same name; choose descriptive names to distinguish them.

Tap a saved entry's **name** to restore its prompts, topic and text into the current draft. This changes the current configuration as well as the meditation text. For audio entries it also restores recorded provider, model, voice, instructions and speed. The current pause duration stays unchanged. Loading stops the previous playback session and does not automatically start speaking. Go to Meditation to read, edit or play the restored text.

Drag the handle at the far right to change saved order. Use the pencil immediately before Trash to rename an entry. Use Trash to delete, then confirm; Cancel keeps it. Entries with audio also offer **Audio only / Nur Audio**, which keeps the name, text, prompts, pause selection and recorded speech settings. Shared recording files remain while another saved entry uses them; the last removal releases the recording after active work has ended. Stop playback and sharing before removing audio only. Deleting one entry does not remove the others or reset their order. Renaming preserves the associated text and audio.

The name filter matches part of a name without case sensitivity. **Audio only** limits the list to entries with a completed local recording. Drag reordering remains available with either or both filters active. Dragging upward puts the meditation immediately before the visible entry you cross; dragging downward puts it immediately after that entry in the full saved list. Hidden entries keep their order relative to each other. Clear the filters to see the resulting full order. Filters do not delete hidden entries.

## Recorded audio and background playback

Cloud playback automatically retains an MP3 for each spoken sentence/segment. Once every spoken segment has valid audio with consistent text and voice settings, the app keeps a complete audio meditation in Saved meditations. If you restored a saved text-only meditation, its recording is attached to that entry. If that entry already has different recorded audio, the new recording keeps its name and appears directly beneath it. Replaying the same recording reuses its entry. Unrestored drafts attach to a matching text-only snapshot or reuse matching audio before creating an entry. Listening to every sentence is not required: generated and prefetched clips count toward offline availability. When three or fewer speech clips are missing during cloud playback, the app fills those gaps in the background, using the same voice settings and normal provider credit. Existing clips and wait commands need no synthesis. At the natural end, any remaining completion work shows “Completing offline audio…” and releases audio focus. Pause/Stop or changing speech settings cancels pending creation. Missing clips or mixed voice settings do not qualify as a complete recording. Partial work can be reused later.

There is no separate audio-save button: cloud playback creates and retains audio automatically. Device TTS can still speak pasted or generated text, but cannot create these saved audio recordings.

Entries with audio show Play and Share before Edit and Trash. Play opens Meditation and uses the existing sentence controls, highlighting and scrolling. Pauses can be changed during replay. Loading a saved name restores its text, prompts and recorded voice settings/speed. Exact matches play from local sentence files without provider credentials or a network request. Changing text, voice settings or speed selects new audio; the previous saved recording remains intact. The Meditation text heading displays the saved entry’s name during playback and while paused. It also keeps that name when a saved meditation is loaded but stopped, as long as the displayed text matches that entry. Changing speech or generation settings does not remove the name; editing the text returns to the generic heading. Entries with identical text use the name of the entry you loaded. The heading shows **(offline)** when the complete recording for the current text and voice settings is available locally. Changing pauses does not affect offline availability. Partial recordings may still require an AI connection for missing sentences.

Share asks for the pause between sentences (default **2 seconds**) and assembles a single MP3 locally. This pause is independent of the playback pause preference. Wait for assembly, then use Android's share sheet. Sharing does not synthesize audio again or use provider credit. It can be cancelled through the sharing notification.

On upgrade, old full-length MP3 recordings are deleted from private app storage. Their saved text and names remain; play them with an AI voice to record the sentences again. Existing sentence recordings are preserved. MP3s previously shared outside the app are unaffected.

Playback continues with the screen locked. Temporary navigation interruptions pause and then resume audio when focus returns. Sentence playback repeats the interrupted segment. Manual pause/stop or headphone disconnection cancels automatic resume. Notification taps reuse the app and open the playback source tab.

Recordings are private app data. Deleting an entry removes its link; audio shared by another entry is retained. Unreferenced recordings are cleaned once active work has ended. Uninstalling removes private recordings. Share a completed MP3 to retain a copy outside the app. Large audio files are excluded from cloud backup. There is no text-library import/export screen. More storage/provider details are in [AI integration](../AI-INTEGRATION.md).

## Save and restore settings files

Open **Configuration → Additional settings / Weitere Einstellungen**. **Save settings to file / Einstellungen in Datei speichern** opens Android’s file picker to choose a location for a JSON backup. It includes the current system instruction and query template, both named prompt-template libraries, AI model, every speech provider’s remembered model/voice, delivery instructions, ElevenLabs voice IDs/names, per-voice speeds, and global pause preferences. Current valid edits in the dialog are included in the file; export does not commit those dialog edits to the app. Prompt edits on the main page are saved when opening Additional settings.

**Restore settings from file / Einstellungen aus Datei wiederherstellen** opens the file picker. Choose a MyMeditation settings JSON file, then confirm restoring active settings and adding new dropdown entries. Cancel keeps current settings. Files are validated before any settings change; invalid, unsupported or oversized files (over 2 MiB) are rejected. Restoring refreshes the visible prompt editors, template pickers and inline controls. Existing prompt templates and voices are kept. Identical prompt text and existing voice IDs are not added again. If a new template or voice needs a name that is already taken, it receives a numbered suffix such as `(2)`. Existing voice names and explicitly saved per-voice speeds win on conflicts. Both existing and imported custom model IDs remain available without duplicates. Repeating the same import does not add more copies. Other active settings are restored from the file; optional scalar values absent from a backup return to defaults. Version 1 files remain supported; new exports use version 2.

These files contain settings only. Saved meditation entries, current meditation topic/text, audio recordings and playback position are preserved. API keys embedded in the app are not included. This is separate from Android’s app-data backup.

## Storage and audio cleanup

Open **Configuration → Additional settings / Weitere Einstellungen** and scroll to **Storage / Speicherplatz**. The display divides approximate private app data into:

- **Offline meditations:** recording files and metadata linked to saved meditations. Shared files count once.
- **Other audio:** provider caches (including persistent ElevenLabs source audio), unfinished/unlinked recordings, legacy audio and temporary MP3s prepared for sharing.
- **Other app data:** settings, prompt libraries, saved texts, settings-export staging files and other private data/cache files.

The installed APK and copies you shared outside the app are excluded. Use **Refresh storage / Speicheranzeige aktualisieren** to recalculate sizes.

**Delete other audio / Sonstige Audiodaten löschen** asks for confirmation and preserves saved offline recordings, text and settings. It is available when playback and sharing have stopped; new audio work cannot start during cleanup. Clearing provider caches or partial recordings may require generating that audio again, using normal provider credit. Deleting audio from a saved entry does not necessarily remove provider caches; those can be cleared separately here. Files shared outside the app are never deleted.


## Suggested meditation names

Manual saves now start with an editable name suggestion. When the current Meditation content was loaded from a saved meditation and that content is unchanged, the suggestion uses its saved name with the next number: `Forest` becomes `Forest 2`; if `Forest 1`, `Forest 2` and `Forest 3` exist, the next suggestion is `Forest 4`. A trailing number is replaced rather than stacked. Regenerating/editing the meditation text or changing voice settings does not change this content-based naming choice.

For new content, the app asks the selected text-generation model for a title around 20 characters in the topic’s language, focused on its distinctive subject rather than generic meditation themes. It prefetches after 1.5 seconds without content edits and when opening Saved meditations. This additional request uses the configured provider/account and its normal API billing. Suggestions are cached locally for the most recent 16 content/model combinations. The editable system instruction and query template do not affect title requests.

Saving never waits for the network. Until a title is available, or if generation fails, a short suggestion from the content (at most four words/30 characters) is used. A late AI result can update an untouched save-dialog suggestion or an automatically created entry; it never overwrites an edited name. Automatic saves use the same suggestions and numbering when they create a new entry. Attaching audio to an existing entry keeps its name, and repeated completion callbacks reuse the existing recording entry.
