package de.jeisfeld.mymeditation.i18n;

import android.content.Context;
import de.jeisfeld.mymeditation.R;

/** Application context only: formatting follows the app resource locale on every lookup. */
public final class LocalizedMessages {
    private LocalizedMessages() { }
    public static void install(Context context) {
        Context app = context.getApplicationContext();
        UiText.setTranslator((message, args) -> app.getString(resource(message), args));
    }
    static int resource(UiText message) {
        switch (message) {
            case WAITING_FOR_AUDIO_FOCUS: return R.string.message_waiting_for_audio_focus;
            case AUDIO_UNAVAILABLE: return R.string.message_audio_unavailable;
            case DEVICE_START_FAILED: return R.string.message_device_start_failed;
            case DEVICE_SPEECH_FAILED: return R.string.message_device_speech_failed;
            case PREPARING_ELEVEN: return R.string.message_preparing_eleven;
            case GENERATE_SPEECH_FAILED: return R.string.message_generate_speech_failed;
            case PLAYBACK_SPEED_FAILED: return R.string.message_playback_speed_failed;
            case GENERATED_PLAYBACK_FAILED: return R.string.message_generated_playback_failed;
            case SERVICE_START_FAILED: return R.string.message_service_start_failed;
            case VISIBLE_SERVICE_FAILED: return R.string.message_visible_service_failed;
            case CONNECTION_AGAIN: return R.string.message_connection_again;
            case CREATE_FAILED: return R.string.message_create_failed;
            case UNSUPPORTED_MODEL: return R.string.message_unsupported_model;
            case INVALID_GEMINI_MODEL: return R.string.message_invalid_gemini_model;
            case UNSUPPORTED_PROVIDER: return R.string.message_unsupported_provider;
            case CACHE_UNAVAILABLE: return R.string.message_cache_unavailable;
            case OPENAI_KEY_MISSING: return R.string.message_openai_key_missing;
            case EXPECTED_AUDIO: return R.string.message_expected_audio;
            case AUDIO_TOO_LARGE: return R.string.message_audio_too_large;
            case EMPTY_SPEECH: return R.string.message_empty_speech;
            case QWEN_REJECTED: return R.string.message_qwen_rejected;
            case GEMINI_NO_CANDIDATES: return R.string.message_gemini_no_candidates;
            case GEMINI_INCOMPLETE_REASON: return R.string.message_gemini_incomplete_reason;
            case GEMINI_INCOMPLETE: return R.string.message_gemini_incomplete;
            case GEMINI_NO_PARTS: return R.string.message_gemini_no_parts;
            case GEMINI_INVALID_WAV: return R.string.message_gemini_invalid_wav;
            case GEMINI_UNSUPPORTED_AUDIO: return R.string.message_gemini_unsupported_audio;
            case GEMINI_INVALID_RATE: return R.string.message_gemini_invalid_rate;
            case GEMINI_INVALID_SIZE: return R.string.message_gemini_invalid_size;
            case GEMINI_NO_AUDIO: return R.string.message_gemini_no_audio;
            case GEMINI_MALFORMED: return R.string.message_gemini_malformed;
            case ELEVEN_INCOMPLETE: return R.string.message_eleven_incomplete;
            case EMPTY_PROVIDER_AUDIO: return R.string.message_empty_provider_audio;
            case ELEVEN_NON_AUDIO: return R.string.message_eleven_non_audio;
            case SPEECH_RESPONSE_LARGE: return R.string.message_speech_response_large;
            case ELEVEN_MODEL: return R.string.message_eleven_model;
            case ELEVEN_VOICE_ID: return R.string.message_eleven_voice_id;
            case GEMINI_TTS_MODEL: return R.string.message_gemini_tts_model;
            case OPENAI_TTS_MODEL: return R.string.message_openai_tts_model;
            case OPENAI_VOICE: return R.string.message_openai_voice;
            case LEGACY_VOICES: return R.string.message_legacy_voices;
            case INVALID_SPEED: return R.string.message_invalid_speed;
            case SEGMENT_LENGTH: return R.string.message_segment_length;
            case INVALID_VOICE_ID: return R.string.message_invalid_voice_id;
            case VOICE_NAME_REQUIRED: return R.string.message_voice_name_required;
            case DUPLICATE_VOICE_NAME: return R.string.message_duplicate_voice_name;
            case SAVE_VOICES_FAILED: return R.string.message_save_voices_failed;
            case NO_MEDITATION: return R.string.message_no_meditation;
            case PRESERVE_TEXT: return R.string.message_preserve_text;
            case UNKNOWN_SAVED_PROVIDER: return R.string.message_unknown_saved_provider;
            case INVALID_SEGMENT_LIMIT: return R.string.message_invalid_segment_limit;
            case NO_TEXT: return R.string.message_no_text;
            case INCOMPLETE_MEDITATION: return R.string.message_incomplete_meditation;
            case LOCAL_KEY: return R.string.message_local_key;
            case AI_HTTP: return R.string.message_ai_http;
            case SPEECH_HTTP: return R.string.message_speech_http;
            case RETRY_EXHAUSTED: return R.string.message_retry_exhausted;
            case PROVIDER_SEGMENT: return R.string.message_provider_segment;
            case QWEN_ADDRESS: return R.string.message_qwen_address;
            case GEMINI_BLOCKED: return R.string.message_gemini_blocked;
            case GEMINI_FAILED: return R.string.message_gemini_failed;
            case NATIVE_MODEL: return R.string.message_native_model;
            case SPEECH_KEY: return R.string.message_speech_key;
            case NON_AUDIO: return R.string.message_non_audio;
            case PROVIDER_SPEECH_HTTP: return R.string.message_provider_speech_http;
            case SAVED_VOICE: return R.string.message_saved_voice;
            case ELEVEN_RETRY: return R.string.message_eleven_retry;
            default: throw new IllegalArgumentException("Unknown message resource.");
        }
    }
}
