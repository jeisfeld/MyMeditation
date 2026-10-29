package de.jeisfeld.mymeditation.http;

import de.jeisfeld.mymeditation.i18n.UiText;
/** Provider selection uses native model IDs and never guesses for an unknown prefix. */
public enum AiProvider {
    CLAUDE("Claude", "CLAUDE_API_KEY", "https://api.anthropic.com/v1/messages"),
    OPENAI("OpenAI", "OPENAI_API_KEY", "https://api.openai.com/v1/responses"),
    GEMINI("Gemini", "GEMINI_API_KEY", "https://generativelanguage.googleapis.com/v1beta/models/"),
    GROK("Grok", "GROK_API_KEY", "https://api.x.ai/v1/chat/completions"),
    DEEPSEEK("DeepSeek", "DEEPSEEK_API_KEY", "https://api.deepseek.com/chat/completions"),
    KIMI("Kimi", "KIMI_API_KEY", "https://api.moonshot.ai/v1/chat/completions"),
    QWEN("Qwen", "QWEN_API_KEY", "https://dashscope-intl.aliyuncs.com/compatible-mode/v1/chat/completions");

    final String displayName;
    final String keyField;
    final String endpoint;

    AiProvider(String displayName, String keyField, String endpoint) {
        this.displayName = displayName;
        this.keyField = keyField;
        this.endpoint = endpoint;
    }

    public static AiProvider forModel(String model) {
        if (model != null) {
            if (model.startsWith("claude-")) { return CLAUDE; }
            if (model.startsWith("gemini-")) { return GEMINI; }
            if (model.startsWith("grok-")) { return GROK; }
            if (model.startsWith("deepseek-")) { return DEEPSEEK; }
            if (model.startsWith("kimi-") || model.startsWith("moonshot-")) { return KIMI; }
            if (model.matches("qwen(?:[0-9]+(?:\\.[0-9]+)?)?-.*")) { return QWEN; }
            if (model.startsWith("gpt-") || model.startsWith("chatgpt-") || model.matches("o[0-9]+(?:-.*)?")) {
                return OPENAI;
            }
        }
        throw new IllegalArgumentException(UiText.UNSUPPORTED_MODEL.text());
    }

    String url(String model) {
        if (this == GEMINI) {
            if (!model.matches("gemini-[A-Za-z0-9._-]+")) {
                throw new IllegalArgumentException(UiText.INVALID_GEMINI_MODEL.text());
            }
            return endpoint + model + ":generateContent";
        }
        return endpoint;
    }
}
