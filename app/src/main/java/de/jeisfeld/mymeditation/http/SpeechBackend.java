package de.jeisfeld.mymeditation.http;

import java.util.Arrays;
import java.util.List;

/** Native speech providers, with separate persisted model/voice selections. */
public enum SpeechBackend {
    DEVICE("device", "", "", "", 3500),
    OPENAI("openai", "OPENAI_API_KEY", "gpt-4o-mini-tts", "coral", 3500),
    GEMINI("gemini", "GEMINI_API_KEY", "gemini-2.5-flash-preview-tts", "Kore", 1500),
    QWEN("qwen", "QWEN_API_KEY", "qwen3-tts-flash", "Cherry", 500),
    ELEVENLABS("elevenlabs", "ELEVENLABS_API_KEY", "eleven_multilingual_v2", "", 3500);

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
            case GEMINI: return Arrays.asList("Kore", "Aoede", "Leda", "Zephyr", "Sulafat", "Puck", "Charon", "Fenrir", "Orus", "Gacrux");
            case ELEVENLABS: return java.util.Collections.emptyList();
            case QWEN: return Arrays.asList("Cherry", "Serena", "Chelsie", "Ethan");
            default: return SpeechProtocol.VOICES;
        }
    }
    public void validate(String model, String voice) {
        if (this == OPENAI) { SpeechProtocol.validate(model, voice); return; }
        if (this == ELEVENLABS) {
            if (model == null || !model.matches("eleven_[A-Za-z0-9_]+")) {
                throw new IllegalArgumentException("Use an ElevenLabs TTS model such as eleven_multilingual_v2 or eleven_v3.");
            }
            if (voice == null || !voice.matches("[A-Za-z0-9_-]{10,128}")) {
                throw new IllegalArgumentException("Paste a voice ID from your ElevenLabs Voices page.");
            }
            return;
        }
        boolean valid = model != null && model.matches("[A-Za-z0-9._-]+") && !model.contains("realtime");
        valid &= this == GEMINI ? model != null && model.startsWith("gemini-2.5-") && model.contains("-tts")
                : model != null && (model.startsWith("qwen3-tts-") || model.startsWith("qwen-tts-"));
        if (this == GEMINI && !valid) {
            throw new IllegalArgumentException("Use gemini-2.5-flash-preview-tts or gemini-2.5-pro-preview-tts. Newer Interactions speech models are not supported here.");
        }
        if (!valid || !voices().contains(voice)) {
            throw new IllegalArgumentException("Choose a native " + id + " TTS model and a supported voice (not a realtime/audio-understanding model).");
        }
    }
}
