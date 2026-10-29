package de.jeisfeld.mymeditation.http;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Curated API model IDs checked October 2026; presets do not restrict manual AI IDs. */
public final class ModelCatalog {
    private ModelCatalog() { }
    private static final List<String> AI = Collections.unmodifiableList(Arrays.asList(
            "claude-sonnet-5-5", "claude-haiku-5-5", "claude-opus-5-5",
            "gpt-6.1-sol", "gpt-6-luna", "gpt-6-astra", "gpt-5.4-mini",
            "gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-3.1-pro-preview",
            "grok-4.7", "grok-4.6",
            "deepseek-v4-pro", "deepseek-v4-flash", "deepseek-chat", "deepseek-reasoner",
            "kimi-k3.1-pro", "kimi-k3", "kimi-k2.6",
            "qwen3.8-max", "qwen3.8-flash", "qwen-plus", "qwen-turbo"));

    public static List<String> aiModels() { return AI; }

    public static List<String> speechModels(SpeechBackend backend) {
        switch (backend) {
            case OPENAI: return Arrays.asList("gpt-4o-mini-tts", "gpt-4o-mini-tts-2025-12-15", "tts-1", "tts-1-hd");
            case GEMINI: return Arrays.asList("gemini-3.8-flash-tts", "gemini-3.8-flash-lite-tts",
                    "gemini-3.1-flash-tts-preview", "gemini-2.5-flash-preview-tts", "gemini-2.5-pro-preview-tts");
            case QWEN: return Arrays.asList("qwen3-tts-flash", "qwen3-tts-instruct-flash", "qwen-tts-latest");
            case ELEVENLABS: return Arrays.asList("eleven_v4", "eleven_v4_turbo", "eleven_v3", "eleven_multilingual_v2",
                    "eleven_flash_v2_5", "eleven_turbo_v2_5", "eleven_flash_v2", "eleven_turbo_v2");
            default: return Collections.emptyList();
        }
    }

    /** Keep saved dated/custom speech model IDs selectable when replacing the former text field. */
    public static List<String> speechModels(SpeechBackend backend, String savedModel) {
        List<String> result = new ArrayList<>(speechModels(backend));
        if (savedModel != null && !savedModel.isEmpty() && !result.contains(savedModel)) { result.add(savedModel); }
        return result;
    }
}
