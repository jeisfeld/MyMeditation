package de.jeisfeld.mymeditation.http;

import de.jeisfeld.mymeditation.i18n.UiText;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Provider-specific request/response formats, independent of networking and Android UI. */
final class AiProtocol {
    private AiProtocol() { }

    static JSONObject request(AiProvider provider, String model, String system, String prompt) throws JSONException {
        JSONObject body = new JSONObject();
        boolean hasSystem = system != null && !system.isEmpty();
        switch (provider) {
            case CLAUDE:
                body.put("model", model).put("max_tokens", 8192)
                        .put("messages", new JSONArray().put(message("user", prompt)));
                if (hasSystem) { body.put("system", system); }
                break;
            case OPENAI:
                body.put("model", model).put("max_output_tokens", 16384).put("input", prompt);
                if (hasSystem) { body.put("instructions", system); }
                break;
            case GEMINI:
                body.put("contents", new JSONArray().put(new JSONObject().put("role", "user")
                        .put("parts", new JSONArray().put(new JSONObject().put("text", prompt)))))
                        .put("generationConfig", new JSONObject().put("maxOutputTokens", 16384));
                if (hasSystem) {
                    body.put("systemInstruction", new JSONObject().put("parts",
                            new JSONArray().put(new JSONObject().put("text", system))));
                }
                break;
            case GROK:
            case DEEPSEEK:
            case KIMI:
            case QWEN:
                JSONArray messages = new JSONArray();
                if (hasSystem) { messages.put(message("system", system)); }
                body.put("model", model).put("max_tokens", provider == AiProvider.GROK ? 16384 : 8192)
                        .put("messages", messages.put(message("user", prompt)));
                // Qwen thinking mode may require streaming; request a non-thinking answer.
                if (provider == AiProvider.QWEN) { body.put("enable_thinking", false); }
                break;
            default:
                throw new IllegalArgumentException(UiText.UNSUPPORTED_PROVIDER.text());
        }
        return body;
    }

    private static JSONObject message(String role, String text) throws JSONException {
        return new JSONObject().put("role", role).put("content", text);
    }

    static String response(AiProvider provider, JSONObject response) throws JSONException {
        StringBuilder text = new StringBuilder();
        switch (provider) {
            case CLAUDE:
                requireComplete(provider, "end_turn".equals(response.optString("stop_reason")));
                JSONArray blocks = response.getJSONArray("content");
                for (int i = 0; i < blocks.length(); i++) {
                    JSONObject block = blocks.getJSONObject(i);
                    if ("text".equals(block.optString("type"))) { text.append(block.getString("text")); }
                }
                break;
            case OPENAI:
                requireComplete(provider, "completed".equals(response.optString("status")));
                JSONArray output = response.getJSONArray("output");
                for (int i = 0; i < output.length(); i++) {
                    JSONObject item = output.getJSONObject(i);
                    if (!"message".equals(item.optString("type")) || !"assistant".equals(item.optString("role"))) { continue; }
                    JSONArray content = item.getJSONArray("content");
                    for (int j = 0; j < content.length(); j++) {
                        JSONObject part = content.getJSONObject(j);
                        if ("output_text".equals(part.optString("type"))) { text.append(part.getString("text")); }
                    }
                }
                break;
            case GEMINI:
                JSONArray candidates = response.optJSONArray("candidates");
                requireComplete(provider, candidates != null && candidates.length() > 0);
                JSONObject candidate = candidates.getJSONObject(0);
                requireComplete(provider, "STOP".equals(candidate.optString("finishReason")));
                JSONArray parts = candidate.getJSONObject("content").getJSONArray("parts");
                for (int i = 0; i < parts.length(); i++) {
                    JSONObject part = parts.getJSONObject(i);
                    if (!part.optBoolean("thought", false)) { text.append(part.optString("text", "")); }
                }
                break;
            case GROK:
            case DEEPSEEK:
            case KIMI:
            case QWEN:
                JSONObject choice = response.getJSONArray("choices").getJSONObject(0);
                requireComplete(provider, "stop".equals(choice.optString("finish_reason")));
                text.append(choice.getJSONObject("message").optString("content", ""));
                break;
            default:
                throw new IllegalArgumentException(UiText.UNSUPPORTED_PROVIDER.text());
        }
        String result = text.toString().trim();
        if (result.isEmpty()) {
            throw new IllegalStateException(UiText.NO_TEXT.text(provider.displayName));
        }
        return result;
    }

    private static void requireComplete(AiProvider provider, boolean complete) {
        if (!complete) {
            throw new IllegalStateException(UiText.INCOMPLETE_MEDITATION.text(provider.displayName));
        }
    }
}
