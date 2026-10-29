package de.jeisfeld.mymeditation.http;

import de.jeisfeld.mymeditation.i18n.UiText;
import java.util.Arrays;
import java.util.List;

/** Native speech providers, with separate persisted model/voice selections. */
public enum SpeechBackend {
    DEVICE("device", "", "", "", 3500),
    OPENAI("openai", "OPENAI_API_KEY", "gpt-4o-mini-tts", "coral", 3500),
    GEMINI("gemini", "GEMINI_API_KEY", "gemini-2.5-flash-preview-tts", "Kore", 1500),
    QWEN("qwen", "QWEN_API_KEY", "qwen3-tts-flash", "Cherry", 500),
    ELEVENLABS("elevenlabs", "ELEVENLABS_API_KEY", "eleven_v4", "", 3500);

    public final String id;
    final String key;
    public final String defaultModel;
    public final String defaultVoice;
    public final int segmentLimit;

    SpeechBackend(String id, String key, String model, String voice, int limit) {
        this.id = id; this.key = key; defaultModel = model; defaultVoice = voice; segmentLimit = limit;
    }
    public static SpeechBackend fromId(String id) {
        for (SpeechBackend backend : values()) { if (backend.id.equals(id)) { return backend; } }
        return DEVICE;
    }
    public String modelKey() { return this == OPENAI ? "speech_model" : id + "_speech_model"; }
    public String voiceKey() { return this == OPENAI ? "speech_voice" : id + "_speech_voice"; }
    public List<String> voices() {
        switch (this) {
            case GEMINI:
                // Perceived feminine voices first; retain the original ordering within each group.
                return Arrays.asList("Kore", "Aoede", "Leda", "Zephyr", "Sulafat", "Gacrux",
                        "Achernar", "Autonoe", "Callirrhoe", "Despina", "Erinome", "Laomedeia", "Pulcherrima", "Vindemiatrix",
                        "Puck", "Charon", "Fenrir", "Orus", "Achird", "Algenib", "Algieba", "Alnilam", "Enceladus", "Iapetus",
                        "Rasalgethi", "Sadachbia", "Sadaltager", "Schedar", "Umbriel", "Zubenelgenubi");
            case ELEVENLABS: return java.util.Collections.emptyList();
            case QWEN: return Arrays.asList("Cherry", "Serena", "Chelsie", "Ethan");
            default: return SpeechProtocol.VOICES;
        }
    }
    public void validate(String model, String voice) {
        if (this == OPENAI) { SpeechProtocol.validate(model, voice); return; }
        if (this == ELEVENLABS) {
            if (model == null || !model.matches("eleven_[A-Za-z0-9_]+")) {
                throw new IllegalArgumentException(UiText.ELEVEN_MODEL.text());
            }
            if (voice == null || !voice.matches("[A-Za-z0-9_-]{10,128}")) {
                throw new IllegalArgumentException(UiText.ELEVEN_VOICE_ID.text());
            }
            return;
        }
        boolean valid = model != null && model.matches("[A-Za-z0-9._-]+") && !model.contains("realtime");
        valid &= this == GEMINI ? model != null && model.matches("gemini-[A-Za-z0-9._-]+-tts(?:-[A-Za-z0-9._-]+)?")
                : model != null && (model.startsWith("qwen3-tts-") || model.startsWith("qwen-tts-"));
        if (this == GEMINI && !valid) {
            throw new IllegalArgumentException(UiText.GEMINI_TTS_MODEL.text());
        }
        if (!valid || !voices().contains(voice)) {
            throw new IllegalArgumentException(UiText.NATIVE_MODEL.text(id));
        }
    }
}
