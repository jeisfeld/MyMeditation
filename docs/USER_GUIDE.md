# User guide

## Start with your own text

1. Open **Meditation**. The upper **Meditation content** field describes the topic; the lower **Meditation text** field holds the words that will be spoken.
2. Paste or write a short meditation in **Meditation text**. Creating a meditation with AI is optional.
3. In **Configuration → AI settings**, choose **Device** for speech output. Android's installed TTS engine supplies the voice; its installation, language and voice settings are managed in Android settings.
4. Return to Meditation and press **Speak Meditation**. Use Pause/Play to pause or continue, Previous/Next or the position slider to move between spoken segments, and Stop to finish while keeping your current position. Hold Stop to finish and return to the beginning. When playback finishes naturally, it also returns to the beginning. Releasing the position slider places the text cursor at the selected sentence and scrolls it into view without opening the keyboard. Placing the cursor in editable meditation text also moves the slider to that sentence, so Read starts there; a cursor inside a wait command selects the next speech for playback.

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

The pause slider sets the gap between spoken segments. The midpoint is 1 second. The left half shows 0–100% in 5% steps: it scales wait commands plus a 1-second base pause, and scales ordinary gaps by the same factor. At one quarter (50%), `[wait 10s]` takes `(10 + 1) × 0.5 = 5.5` seconds and an ordinary gap takes 0.5 seconds. At 0%, waits and gaps are skipped. The right half offers larger gaps up to 30 seconds, in half-second choices through 10 seconds and whole seconds afterward. The voice-speed slider uses 0.05 steps from 0.25 to 1.5, 0.1 steps from 1.5 to 2.6, and 0.2 steps from 2.6 to 4. Tap either displayed value to enter an exact value; decimal commas and decimal points are accepted, as are percentages such as `50%` for the pause setting.

Both settings apply to live playback, including device TTS. Live speed settings are remembered per cloud voice. Recorded sentence audio uses the same player and adjustable pauses. Changing effective voice/model settings selects different source audio. Native-provider speed changes are local and keep the same source identity; OpenAI speed changes select speed-specific audio. Matching sentence sources are reused across scripts.

For an activity that needs more time, put a timed pause directly in the meditation text:

```text
Take three more comfortable breaths. [wait 18s]
Now notice how you feel.
```

At pause settings of 1 second or more, the app stays silent for 18 seconds plus the configured sentence pause before continuing. Below that, it scales the wait plus the 1-second base pause by the displayed percentage. Commands always use `wait` and `s`, including in German or other languages. Durations must be greater than zero and at most 300 seconds; decimal points with up to three decimal places are supported, for example `[wait 2.5s]`. Voice speed does not change wait durations. Pause settings below 1 second proportionally shorten waits and their base gap. At settings of 1 second or more, the configured sentence pause is added once to each uninterrupted group of wait commands, with no additional gap after the group. Leading, trailing and consecutive commands are supported; consecutive durations add together and receive the configured sentence pause only once. Manual starts, seeking, resume and Next skip wait commands and begin at the next spoken sentence; Previous skips back across waits to the previous speech. If no speech remains, playback ends. Waits encountered during uninterrupted playback still run normally. Leading waits are also omitted from shared MP3s and the total offline duration.

The dropdown to the right of **Meditation content** selects the pacing of newly generated meditations and saves immediately:

- **No pauses / Keine Pausen:** continuous guidance, with no wait commands or activities that require the listener to do something between sentences.
- **Few pauses / Wenige Pausen** (default): wait commands only rarely, preferring brief pauses of 3–8 seconds and avoiding repeated-breath exercises or extended silence.
- **Many pauses / Viele Pausen:** regular pauses for breathing repetitions, reflection and silent activities, with realistic time to complete them.

The choice persists across navigation and restarts. It affects the hidden instructions added to every AI request, including requests with a custom or empty System instruction. Changing it does not rewrite an existing meditation, change its wait commands, or change the ordinary playback pause slider. Saving a meditation stores the selected pacing with it; restoring the entry restores that selection. Older entries without a stored selection use Few pauses. Your editable system instruction, templates and saved prompts remain as entered. Models still choose where to pause and for how long; review those choices when editing. Invalid `wait` commands in an AI response reject the response and preserve the previous meditation. In pasted or edited text they show an error when you press Speak. Commands are never sent to speech synthesis. Saved recordings retain them, and shared MP3s include the requested silence plus the Share dialog’s ordinary gap setting once per uninterrupted group of wait commands.

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

Drag the handle at the far right to change saved order. Use the pencil immediately before Trash to edit the name and inspect or complete its offline audio. Use Trash to delete, then confirm; Cancel keeps it. Entries with audio also offer **Audio only / Nur Audio**, which keeps the name, text, prompts, pause selection and recorded speech settings. Shared recording files remain while another saved entry uses them; the last removal releases the recording after active work has ended. Stop playback and sharing before removing audio only. Deleting one entry does not remove the others or reset their order. Renaming preserves the associated text and audio.

The name filter matches part of a name without case sensitivity. Its clear icon appears when text is entered; tap it to clear only the name filter while keeping the Audio only selection. **Audio only** limits the list to entries with a completed local recording. Drag reordering remains available with either or both filters active. Dragging upward puts the meditation immediately before the visible entry you cross; dragging downward puts it immediately after that entry in the full saved list. Hidden entries keep their order relative to each other. Clear the filters to see the resulting full order. Filters do not delete hidden entries.

## Recorded audio and background playback

Cloud playback automatically retains an MP3 for each spoken sentence/segment. Once every spoken segment has valid audio with consistent text and voice settings, the app keeps a complete audio meditation in Saved meditations. If you restored a saved text-only meditation, its recording is attached to that entry. If that entry already has different recorded audio, the new recording keeps its name and appears directly beneath it. Replaying the same recording reuses its entry. Unrestored drafts attach to a matching text-only snapshot or reuse matching audio before creating an entry. Listening to every sentence is not required: generated and prefetched clips count toward offline availability. When three or fewer speech clips are missing during cloud playback, the app fills those gaps in the background, using the same voice settings and normal provider credit. Existing clips and wait commands need no synthesis. At the natural end, any remaining completion work shows “Completing offline audio…” and releases audio focus. Pause/Stop or changing speech settings cancels pending creation. Missing clips or mixed voice settings do not qualify as a complete recording. Partial work can be reused later.

There is no separate audio-save button: cloud playback creates and retains audio automatically. Device TTS can still speak pasted or generated text, but cannot create these saved audio recordings.

Entries with audio show Play and Share before Edit and Trash. Play opens Meditation and uses the existing sentence controls, highlighting and scrolling. Pauses can be changed during replay. Loading a saved name restores its text, prompts and recorded voice settings/speed. Exact matches play from local sentence files without provider credentials or a network request. Unchanged sentences are reused across meditations for the same voice/model and effective delivery settings. Changing a sentence only requires new audio for missing segments. ElevenLabs, Gemini and Qwen speed changes happen locally and preserve offline availability; OpenAI speed changes require speed-specific audio. The Meditation text heading displays the saved entry’s name during playback and while paused. It also keeps that name when a saved meditation is loaded but stopped, as long as the displayed text matches that entry. Changing speech or generation settings does not remove the name; editing the text returns to the generic heading. Entries with identical text use the name of the entry you loaded. The heading shows **(offline)** when the complete recording for the current text and voice settings is available locally. Changing pauses does not affect offline availability. Partial recordings may still require an AI connection for missing sentences.

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

- **Offline meditations:** all audio associated with saved meditations, including partial sentence sources, matching provider-cache files and recording metadata. Each physical file counts once, even when several entries use it.
- **Other audio:** audio not associated with saved meditations: unused sentence sources/cache files, unlinked recording data, retained migration backups, legacy audio and temporary MP3s prepared for sharing.
- **Other app data:** settings, prompt libraries, saved texts, settings-export staging files and other private data/cache files.

The installed APK and copies you shared outside the app are excluded. Use **Refresh storage / Speicheranzeige aktualisieren** to recalculate sizes.

**Delete other audio / Sonstige Audiodaten löschen** asks for confirmation and preserves saved offline recordings, text and settings. It is available when playback and sharing have stopped; new audio work cannot start during cleanup. Clearing provider caches or partial recordings may require generating that audio again, using normal provider credit. Deleting audio from a saved entry does not necessarily remove provider caches; those can be cleared separately here. Files shared outside the app are never deleted.


## Suggested meditation names

Manual saves now start with an editable name suggestion. When the current Meditation content was loaded from a saved meditation and that content is unchanged, the suggestion uses its saved name with the next number: `Forest` becomes `Forest 2`; if `Forest 1`, `Forest 2` and `Forest 3` exist, the next suggestion is `Forest 4`. A trailing number is replaced rather than stacked. Regenerating/editing the meditation text or changing voice settings does not change this content-based naming choice.

For new content, the app asks the selected text-generation model for a title around 20 characters in the topic’s language, focused on its distinctive subject rather than generic meditation themes. It prefetches after 1.5 seconds without content edits and when opening Saved meditations. This additional request uses the configured provider/account and its normal API billing. Suggestions are cached locally for the most recent 16 content/model combinations. The editable system instruction and query template do not affect title requests.

Saving never waits for the network. Until a title is available, or if generation fails, a short suggestion from the content (at most four words/30 characters) is used. A late AI result can update an untouched save-dialog suggestion or an automatically created entry; it never overwrites an edited name. Automatic saves use the same suggestions and numbering when they create a new entry. Attaching audio to an existing entry keeps its name, and repeated completion callbacks reuse the existing recording entry.


## Shared sentence audio and upgrade

New recordings keep the original provider audio per sentence, shared globally for matching voice/model/effective delivery settings. Speed is applied during playback and MP3 export for ElevenLabs, Gemini and Qwen. A new meditation whose spoken sentences are all already stored is immediately available offline, even when reordered or separated by different waits. OpenAI's API generates speed-adjusted speech, so changing its speed still requires matching speed-specific audio.

The one-time migration has been removed after completion. Existing migrated recordings are kept. Files copied from an original provider cache are unchanged; older speed-adjusted MP3s that were normalized locally may have additional artifacts. Retained migration backups remain under Other audio until you delete them. See the [historical migration and fresh-audio procedure](GLOBAL_AUDIO_MIGRATION.md) for recovery details and replacing degraded normalized audio while keeping saved texts/settings. New recordings always keep the original provider source.


## Prepare a saved meditation for offline use

Open a saved meditation's pencil/Edit dialog. For cloud speech, it shows **Offline audio** as a percentage of spoken words whose sentence sources are already stored. Repeated sentences count each time they occur; `[wait …]` commands have no words/audio and do not affect the percentage. Partial results stay below 100% until every speech segment is available. Word boundaries use Unicode text segmentation, so counts are an approximation for languages without spaces.

The voice/model shown in the dialog is used for both coverage and preparation. Saved speech settings are preferred, including settings retained after Audio only removal. Older text-only entries without a speech snapshot use the current settings. New cloud text saves now retain their speech settings even if audio is incomplete. For Device speech, select a cloud voice in Settings before preparing an entry without a saved cloud snapshot.

**Make available offline / Offline verfügbar machen** prepares only missing sentences, reusing global sources and provider caches. It can also attach an already-complete set of shared sources to this saved entry without synthesis. Normal provider charges apply for new speech. The operation is silent and does not replace the current draft or voice selection. A foreground notification and the edit dialog show word-weighted progress and offer cancellation. Closing the dialog leaves preparation running. Playback, sharing, other preparation jobs and audio cleanup wait until it finishes or is cancelled.

Cancellation, errors and process interruption keep completed sentences. Retry from the edit dialog to generate only what remains. Renaming is allowed during preparation; deleting or changing the saved entry prevents stale work from reattaching it. Successful preparation adds audio to the same saved entry, preserving its name, prompts, order and pause guidance.


Storage accounting uses each saved entry's retained speech settings and explicit recording links, independently of whether all its audio is ready. Incomplete/cancelled preparation still belongs to that saved entry, and Other-audio cleanup preserves its matching clips and provider caches. Changing the current GUI voice does not change these associations. **Audio only / Nur Audio** is also available for partial audio: it releases that entry's storage association while retaining speech settings for later restoration/retry. Sources still used by another saved entry remain protected. Explicitly preparing or playing that text-only entry again re-establishes its association. Old migration backups are not active recording links and remain Other audio.
