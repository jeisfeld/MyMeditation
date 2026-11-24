package de.jeisfeld.mymeditation.http;

import org.json.JSONObject;
import de.jeisfeld.mymeditation.audio.MeditationPauses;
import org.junit.Test;
import static org.junit.Assert.*;

public class AiProtocolTest {
    @Test public void titleRequestsUseDedicatedInstructionsForEveryProvider() throws Exception {
        for (AiProvider provider : AiProvider.values()) {
            JSONObject request = AiProtocol.titleRequest(provider, "test-model", "Ein ruhiger Wald");
            String serialized = request.toString();
            assertTrue(serialized.contains("Ein ruhiger Wald"));
            String instructions = provider == AiProvider.CLAUDE ? request.getString("system")
                    : provider == AiProvider.OPENAI ? request.getString("instructions")
                    : provider == AiProvider.GEMINI ? request.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text")
                    : request.getJSONArray("messages").getJSONObject(0).getString("content");
            assertTrue(instructions.contains("around 20 characters"));
            assertTrue(instructions.contains("never more than 30 including spaces"));
            assertTrue(instructions.contains("specific, distinctive subject"));
            assertTrue(instructions.contains("omit generic meditation themes"));
            assertFalse(instructions.contains(MeditationPauses.systemInstruction("")));
        }
        assertEquals(2048, AiProtocol.titleRequest(AiProvider.OPENAI, "o3", "Topic").getInt("max_output_tokens"));
        assertEquals(1024, AiProtocol.titleRequest(AiProvider.CLAUDE, "claude-test", "Topic").getInt("max_tokens"));
    }
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
        assertEquals(MeditationPauses.systemInstruction("system"), claude.getString("system"));
        assertEquals("prompt", claude.getJSONArray("messages").getJSONObject(0).getString("content"));
        JSONObject openai = AiProtocol.request(AiProvider.OPENAI, "o3", "system", "prompt");
        assertEquals(MeditationPauses.systemInstruction("system"), openai.getString("instructions"));
        assertEquals("prompt", openai.getString("input"));
        assertFalse(openai.has("max_tokens"));
        JSONObject gemini = AiProtocol.request(AiProvider.GEMINI, "gemini-test", "system", "prompt");
        assertEquals(MeditationPauses.systemInstruction("system"), gemini.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text"));
        assertEquals("prompt", gemini.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text"));
        JSONObject grok = AiProtocol.request(AiProvider.GROK, "grok-test", "system", "prompt");
        assertEquals("system", grok.getJSONArray("messages").getJSONObject(0).getString("role"));
        assertEquals("prompt", grok.getJSONArray("messages").getJSONObject(1).getString("content"));
    }

    @Test public void playbackInstructionsApplyToEveryProviderRegardlessOfEditablePrompt() throws Exception {
        for (AiProvider provider : AiProvider.values()) {
            for (String editable : new String[] {null, "", "Antworte auf Deutsch. Nur Fließtext, keine Befehle."}) {
                JSONObject body = AiProtocol.request(provider, "test-model", editable, "My topic");
                String system;
                switch (provider) {
                    case CLAUDE: system = body.getString("system"); break;
                    case OPENAI: system = body.getString("instructions"); break;
                    case GEMINI: system = body.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text"); break;
                    default: system = body.getJSONArray("messages").getJSONObject(0).getString("content"); break;
                }
                if (editable != null && !editable.isEmpty()) { assertTrue(system.startsWith(editable + "\n\n")); }
                assertTrue(system.contains("[wait Ns]"));
                assertTrue(system.contains("Never translate wait or s"));
                assertTrue(system.contains("at most 300"));
            }
        }
    }

    @Test public void everyProviderReceivesTheSelectedPacingPolicyWithoutChangingTheEditablePrompt() throws Exception {
        String editable = "Mein eigener Systemtext. Viele Atemübungen.";
        for (AiProvider provider : AiProvider.values()) {
            for (MeditationPauses.Guidance guidance : MeditationPauses.Guidance.values()) {
                JSONObject body = AiProtocol.request(provider, "test-model", editable, "Topic", guidance);
                String system;
                switch (provider) {
                    case CLAUDE: system = body.getString("system"); break;
                    case OPENAI: system = body.getString("instructions"); break;
                    case GEMINI: system = body.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text"); break;
                    default: system = body.getJSONArray("messages").getJSONObject(0).getString("content"); break;
                }
                assertTrue(system.startsWith(editable + "\n\n"));
                switch (guidance) {
                    case NONE:
                        assertTrue(system.contains("Do not insert wait commands"));
                        assertTrue(system.contains("Do not ask the listener to perform activities between sentences"));
                        assertFalse(system.contains("[wait Ns]"));
                        break;
                    case FEW:
                        assertTrue(system.contains("only rarely"));
                        assertTrue(system.contains("5 to 10"));
                        break;
                    case MANY:
                        assertTrue(system.contains("regular spacious pauses"));
                        assertTrue(system.contains("[wait 18s]"));
                        break;
                }
            }
        }
        assertEquals(MeditationPauses.Guidance.FEW, MeditationPauses.Guidance.fromId("unknown"));
        assertEquals(MeditationPauses.Guidance.FEW, MeditationPauses.Guidance.fromId(null));
    }

    @Test public void generatedPauseCommandsArePreservedAndMalformedCommandsRejectTheResponse() throws Exception {
        JSONObject message = new JSONObject().put("content", "Atme dreimal. [wait 18s] Ruh dich aus.");
        JSONObject response = new JSONObject().put("choices", new org.json.JSONArray().put(
                new JSONObject().put("finish_reason", "stop").put("message", message)));
        assertEquals("Atme dreimal. [wait 18s] Ruh dich aus.", AiProtocol.response(AiProvider.QWEN, response));
        message.put("content", "Breathe. [wait 100000s] Relax.");
        assertThrows(IllegalArgumentException.class, () -> AiProtocol.response(AiProvider.QWEN, response));
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

    @Test public void outputLimitsAreReportedSeparatelyFromOtherIncompleteResponses() throws Exception {
        AiProvider[] providers = {AiProvider.CLAUDE, AiProvider.OPENAI, AiProvider.GEMINI, AiProvider.GROK,
                AiProvider.DEEPSEEK, AiProvider.KIMI, AiProvider.QWEN};
        String[] responses = {
                "{\"stop_reason\":\"max_tokens\"}",
                "{\"status\":\"incomplete\",\"incomplete_details\":{\"reason\":\"max_output_tokens\"}}",
                "{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\"}]}",
                "{\"choices\":[{\"finish_reason\":\"length\"}]}"};
        for (int i = 0; i < providers.length; i++) {
            AiProvider provider = providers[i];
            JSONObject response = new JSONObject(responses[Math.min(i, 3)]);
            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> AiProtocol.response(provider, response));
            assertEquals(de.jeisfeld.mymeditation.i18n.UiText.AI_OUTPUT_LIMIT.text(provider.displayName), failure.getMessage());
        }
        assertEquals(de.jeisfeld.mymeditation.i18n.UiText.INCOMPLETE_MEDITATION.text(AiProvider.OPENAI.displayName),
                assertThrows(IllegalStateException.class, () -> AiProtocol.response(AiProvider.OPENAI,
                        new JSONObject("{\"status\":\"incomplete\",\"incomplete_details\":{\"reason\":\"content_filter\"}}"))).getMessage());
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
            assertEquals(MeditationPauses.systemInstruction("system"), body.getJSONArray("messages").getJSONObject(0).getString("content"));
            assertEquals("user", body.getJSONArray("messages").getJSONObject(1).getString("role"));
            assertEquals("prompt", body.getJSONArray("messages").getJSONObject(1).getString("content"));
            assertEquals(2, AiProtocol.request(provider, "test-model", "", "prompt").getJSONArray("messages").length());
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
