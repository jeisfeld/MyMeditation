# Meditation AI integration

Create Meditation calls the selected provider directly over HTTPS. Configure its native model ID in **Configuration → AI settings**. Existing prompts, the `@TEXT@` query template, and saved model selection are retained. Speak Meditation still uses Android TTS.

## Local API keys

In your local `app/src/main/java/de/jeisfeld/mymeditation/http/HttpCredentials.java`, add the constants for whichever providers you use:

```java
package de.jeisfeld.mymeditation.http;

public final class HttpCredentials {
    public static final String CLAUDE_API_KEY = "your-anthropic-api-key";
    public static final String OPENAI_API_KEY = "your-openai-api-key";
    public static final String GEMINI_API_KEY = "your-google-ai-studio-api-key";
    public static final String GROK_API_KEY = "your-xai-api-key";
    public static final String DEEPSEEK_API_KEY = "your-deepseek-api-key";
    public static final String KIMI_API_KEY = "your-moonshot-api-key";
    public static final String QWEN_API_KEY = "your-dashscope-international-api-key";
}
```

Keep existing constants in that class. This exact file is Git-ignored; no real keys are committed. Unused constants may be omitted or empty. Clean checkouts build without the credentials class. A missing selected provider key displays an error specifying the constant to add. Rebuild and reinstall after changing keys.

There are no API key controls in the app. Previously saved device keys are no longer read or used. Credentials come exclusively from `HttpCredentials` and are embedded in the APK, where they can be extracted. Keep builds containing personal keys private.

## Model routing

| Model ID prefix | Provider | Constant | API |
| --- | --- | --- | --- |
| `claude-` | Anthropic | `CLAUDE_API_KEY` | Messages |
| `gpt-`, `chatgpt-`, or o-series such as `o3` / `o4-mini` | OpenAI | `OPENAI_API_KEY` | Responses |
| `gemini-` | Google Gemini | `GEMINI_API_KEY` | generateContent |
| `grok-` | xAI | `GROK_API_KEY` | Chat Completions |
| `deepseek-` | DeepSeek | `DEEPSEEK_API_KEY` | Chat Completions |
| `kimi-`, `moonshot-` | Moonshot / Kimi | `KIMI_API_KEY` | Chat Completions |
| `qwen-`, versioned prefixes such as `qwen3-` / `qwen2.5-` | Alibaba Qwen | `QWEN_API_KEY` | Chat Completions |

Enter a text generation model available to your API account; a recognized prefix does not guarantee that the model exists or supports the selected API. Example IDs include `claude-sonnet-4-5`, `gpt-4.1`, `gemini-2.5-flash`, `grok-4`, `deepseek-chat`, `kimi-k2-0905-preview`, and `qwen-plus`. The existing Claude default is unchanged. Unsupported prefixes are rejected locally rather than sent to another provider. API billing is separate from chat subscriptions.

Kimi uses the international Moonshot endpoint (`api.moonshot.ai`). Qwen uses Alibaba Model Studio's international DashScope endpoint (`dashscope-intl.aliyuncs.com`, Singapore); use keys and models provisioned for that region, rather than China or US regional credentials. DeepSeek uses `api.deepseek.com`. These are direct provider services, not third-party aggregators. Qwen requests disable thinking mode to support non-streaming meditation generation. DeepSeek and Kimi reasoning fields are ignored; only the final answer is used.

System and user prompts are sent only to the selected provider. Requests use its own key and fixed HTTPS endpoint, with redirects disabled. Failed, incomplete, blocked, or empty responses preserve the previous meditation. Provider response bodies and keys are not logged. Reasoning/thought blocks are excluded from the meditation. Requests are not automatically retried.

## Verification

`bash gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest`

Protocol tests cover routing, provider request formats, system prompts, text extraction, reasoning exclusion, and incomplete/blocked/refused responses. On a device, verify real requests for each configured provider, missing keys, unavailable models, network failures, and switching models. No real API keys are needed to build or run protocol tests.
