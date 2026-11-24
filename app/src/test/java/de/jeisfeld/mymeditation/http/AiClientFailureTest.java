package de.jeisfeld.mymeditation.http;

import de.jeisfeld.mymeditation.i18n.UiText;
import org.junit.Test;
import static org.junit.Assert.*;

public class AiClientFailureTest {
    @Test public void transportAndResponseFailuresHaveSpecificMessagesWithoutLeakingRemoteDetails() {
        String secret = "https://example.test/?key=secret private prompt";
        assertEquals(UiText.AI_TIMEOUT.text(), AiClient.failureMessage(new java.net.SocketTimeoutException(secret)));
        assertEquals(UiText.AI_TIMEOUT.text(), AiClient.failureMessage(new javax.net.ssl.SSLException(
                new java.net.SocketTimeoutException(secret))));
        assertEquals(UiText.AI_NETWORK_FAILED.text(), AiClient.failureMessage(new java.net.UnknownHostException(secret)));
        assertEquals(UiText.AI_NETWORK_FAILED.text(), AiClient.failureMessage(new java.io.EOFException(secret)));
        assertEquals(UiText.AI_TLS_FAILED.text(), AiClient.failureMessage(new javax.net.ssl.SSLException(secret)));
        assertEquals(UiText.AI_INVALID_RESPONSE.text(), AiClient.failureMessage(new org.json.JSONException(secret)));
        assertEquals(UiText.CREATE_FAILED.text(), AiClient.failureMessage(new Exception(secret)));
        assertFalse(AiClient.failureMessage(new java.net.SocketTimeoutException(secret)).contains("secret"));
        assertEquals("Provider failed (HTTP 429)", AiClient.failureMessage(new IllegalStateException("Provider failed (HTTP 429)")));
    }
}
