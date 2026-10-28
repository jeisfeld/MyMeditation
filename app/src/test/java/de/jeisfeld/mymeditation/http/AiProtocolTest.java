package de.jeisfeld.mymeditation.http;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class AiProtocolTest {
    @Test public void routesNativeModelIds() {
        assertEquals(AiProvider.CLAUDE, AiProvider.forModel("claude-sonnet-4-5"));
        assertEquals(AiProvider.OPENAI, AiProvider.forModel("gpt-4.1"));
        assertEquals(AiProvider.OPENAI, AiProvider.forModel("chatgpt-4o-latest"));
        assertEquals(AiProvider.OPENAI, AiProvider.forModel("o3"));
        assertEquals(AiProvider.OPENAI, AiProvider.forModel("o4-mini"));
        assertEquals(AiProvider.GEMINI, AiProvider.forModel("gemini-2.5-flash"));
        assertEquals(AiProvider.GROK, AiProvider.forModel("grok-4"));
    }

    @Test public void rejectsUnknownModelsAndUnsafeGeminiPaths() {
        for (String model : new String[] {null, "", "other-model", "openai", "ocean"}) {
            assertThrows(IllegalArgumentException.class, () -> AiProvider.forModel(model));
        }
        assertThrows(IllegalArgumentException.class, () -> AiProvider.GEMINI.url("gemini-foo?key=bad"));
    }

    @Test public void buildsProviderSpecificPrompts() throws Exception {
        JSONObject claude = AiProtocol.request(AiProvider.CLAUDE, "claude-test", "system", "prompt");
        assertEquals("system", claude.getString("system"));
        assertEquals("prompt", claude.getJSONArray("messages").getJSONObject(0).getString("content"));
        JSONObject openai = AiProtocol.request(AiProvider.OPENAI, "o3", "system", "prompt");
        assertEquals("system", openai.getString("instructions"));
        assertEquals("prompt", openai.getString("input"));
        assertFalse(openai.has("max_tokens"));
        JSONObject gemini = AiProtocol.request(AiProvider.GEMINI, "gemini-test", "system", "prompt");
        assertEquals("system", gemini.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text"));
        assertEquals("prompt", gemini.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text"));
        JSONObject grok = AiProtocol.request(AiProvider.GROK, "grok-test", "system", "prompt");
        assertEquals("system", grok.getJSONArray("messages").getJSONObject(0).getString("role"));
        assertEquals("prompt", grok.getJSONArray("messages").getJSONObject(1).getString("content"));
    }

    @Test public void omitsEmptySystemPrompts() throws Exception {
        assertFalse(AiProtocol.request(AiProvider.CLAUDE, "claude-test", "", "prompt").has("system"));
        assertFalse(AiProtocol.request(AiProvider.OPENAI, "gpt-test", null, "prompt").has("instructions"));
        assertFalse(AiProtocol.request(AiProvider.GEMINI, "gemini-test", "", "prompt").has("systemInstruction"));
        assertEquals(1, AiProtocol.request(AiProvider.GROK, "grok-test", "", "prompt").getJSONArray("messages").length());
    }

    @Test public void readsClaudeTextWithoutThinking() throws Exception {
        assertEquals("Breathe.", AiProtocol.response(AiProvider.CLAUDE, new JSONObject(
                "{\"stop_reason\":\"end_turn\",\"content\":[{\"type\":\"thinking\",\"thinking\":\"private\"},{\"type\":\"text\",\"text\":\" Breathe. \"}]}")));
    }

    @Test public void readsOpenAiTextWithoutReasoning() throws Exception {
        assertEquals("Breathe.", AiProtocol.response(AiProvider.OPENAI, new JSONObject(
                "{\"status\":\"completed\",\"output\":[{\"type\":\"reasoning\"},{\"type\":\"message\",\"role\":\"assistant\",\"content\":[{\"type\":\"output_text\",\"text\":\"Breathe.\"}]}]}")));
    }

    @Test public void readsGeminiTextWithoutThoughts() throws Exception {
        assertEquals("Breathe.", AiProtocol.response(AiProvider.GEMINI, new JSONObject(
                "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"thought\":true,\"text\":\"private\"},{\"text\":\"Breathe.\"}]}}]}")));
    }

    @Test public void readsGrokText() throws Exception {
        assertEquals("Breathe.", AiProtocol.response(AiProvider.GROK, new JSONObject(
                "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"Breathe.\",\"reasoning_content\":\"private\"}}]}")));
    }

    @Test public void rejectsTruncatedOrBlockedResponses() throws Exception {
        assertThrows(IllegalStateException.class, () -> AiProtocol.response(AiProvider.CLAUDE,
                new JSONObject("{\"stop_reason\":\"max_tokens\"}")));
        assertThrows(IllegalStateException.class, () -> AiProtocol.response(AiProvider.OPENAI,
                new JSONObject("{\"status\":\"incomplete\"}")));
        assertThrows(IllegalStateException.class, () -> AiProtocol.response(AiProvider.GEMINI,
                new JSONObject("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}")));
        assertThrows(IllegalStateException.class, () -> AiProtocol.response(AiProvider.GEMINI,
                new JSONObject("{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\"}]}")));
        assertThrows(IllegalStateException.class, () -> AiProtocol.response(AiProvider.GROK,
                new JSONObject("{\"choices\":[{\"finish_reason\":\"length\"}]}")));
    }

    @Test public void rejectsEmptyTextAndRefusals() throws Exception {
        assertThrows(IllegalStateException.class, () -> AiProtocol.response(AiProvider.CLAUDE,
                new JSONObject("{\"stop_reason\":\"end_turn\",\"content\":[]}")));
        assertThrows(IllegalStateException.class, () -> AiProtocol.response(AiProvider.OPENAI,
                new JSONObject("{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"role\":\"assistant\",\"content\":[{\"type\":\"refusal\",\"refusal\":\"No\"}]}]}")));
        assertThrows(IllegalStateException.class, () -> AiProtocol.response(AiProvider.GROK,
                new JSONObject("{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":null}}]}")));
    }
    @Test public void routesAdditionalProvidersAndVersions() {
        assertEquals(AiProvider.DEEPSEEK, AiProvider.forModel("deepseek-chat"));
        assertEquals(AiProvider.DEEPSEEK, AiProvider.forModel("deepseek-reasoner"));
        assertEquals(AiProvider.KIMI, AiProvider.forModel("kimi-k2-0905-preview"));
        assertEquals(AiProvider.KIMI, AiProvider.forModel("moonshot-v1-8k"));
        assertEquals(AiProvider.QWEN, AiProvider.forModel("qwen-plus"));
        assertEquals(AiProvider.QWEN, AiProvider.forModel("qwen3-max"));
        assertEquals(AiProvider.QWEN, AiProvider.forModel("qwen2.5-72b-instruct"));
        assertThrows(IllegalArgumentException.class, () -> AiProvider.forModel("qwenonsense"));
    }

    @Test public void additionalProvidersUseTheirOwnEndpointsAndKeys() {
        assertEquals("DEEPSEEK_API_KEY", AiProvider.DEEPSEEK.keyField);
        assertEquals("KIMI_API_KEY", AiProvider.KIMI.keyField);
        assertEquals("QWEN_API_KEY", AiProvider.QWEN.keyField);
        assertEquals("https://api.deepseek.com/chat/completions", AiProvider.DEEPSEEK.url("deepseek-chat"));
        assertEquals("https://api.moonshot.ai/v1/chat/completions", AiProvider.KIMI.url("kimi-k2-0905-preview"));
        assertEquals("https://dashscope-intl.aliyuncs.com/compatible-mode/v1/chat/completions", AiProvider.QWEN.url("qwen-plus"));
    }

    @Test public void buildsAdditionalProviderRequests() throws Exception {
        for (AiProvider provider : new AiProvider[] {AiProvider.DEEPSEEK, AiProvider.KIMI, AiProvider.QWEN}) {
            JSONObject body = AiProtocol.request(provider, "test-model", "system", "prompt");
            assertEquals("test-model", body.getString("model"));
            assertEquals(8192, body.getInt("max_tokens"));
            assertEquals("system", body.getJSONArray("messages").getJSONObject(0).getString("content"));
            assertEquals("user", body.getJSONArray("messages").getJSONObject(1).getString("role"));
            assertEquals("prompt", body.getJSONArray("messages").getJSONObject(1).getString("content"));
            assertEquals(1, AiProtocol.request(provider, "test-model", "", "prompt").getJSONArray("messages").length());
            if (provider == AiProvider.QWEN) {
                assertFalse(body.getBoolean("enable_thinking"));
            } else {
                assertFalse(body.has("enable_thinking"));
            }
        }
    }

    @Test public void additionalProvidersReadFinalTextAndRejectIncompleteOrEmptyAnswers() throws Exception {
        for (AiProvider provider : new AiProvider[] {AiProvider.DEEPSEEK, AiProvider.KIMI, AiProvider.QWEN}) {
            JSONObject message = new JSONObject().put("content", "Breathe.").put("reasoning_content", "private");
            JSONObject choice = new JSONObject().put("finish_reason", "stop").put("message", message);
            JSONObject response = new JSONObject().put("choices", new org.json.JSONArray().put(choice));
            assertEquals("Breathe.", AiProtocol.response(provider, response));
            choice.put("finish_reason", "length");
            assertThrows(IllegalStateException.class, () -> AiProtocol.response(provider, response));
            choice.put("finish_reason", "content_filter");
            assertThrows(IllegalStateException.class, () -> AiProtocol.response(provider, response));
            choice.put("finish_reason", "stop");
            message.put("content", JSONObject.NULL);
            assertThrows(IllegalStateException.class, () -> AiProtocol.response(provider, response));
        }
    }

}
