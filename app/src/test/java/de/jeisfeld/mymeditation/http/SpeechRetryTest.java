package de.jeisfeld.mymeditation.http;

import org.junit.Test;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.CancellationException;
import static org.junit.Assert.*;

public class SpeechRetryTest {
    @Test public void transientFailureRetriesTwiceWithBackoffThenSucceeds() throws Exception {
        int[] attempts = {0};
        long[] waited = {0};
        String result = SpeechRetry.run(() -> {
            if (++attempts[0] < 3) { throw new SocketTimeoutException(); }
            return "audio";
        }, () -> { }, ms -> waited[0] += ms);
        assertEquals("audio", result);
        assertEquals(3, attempts[0]);
        assertEquals(3000, waited[0]);
    }

    @Test public void exhaustedRetriesSurfaceOneError() {
        int[] attempts = {0};
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> SpeechRetry.run(() -> {
            attempts[0]++;
            throw new IOException("Connection reset");
        }, () -> { }, ms -> { }));
        assertEquals(3, attempts[0]);
        assertTrue(error.getMessage().contains("3 attempts"));
    }

    @Test public void authenticationAndValidationErrorsAreNotRetried() {
        for (int status : new int[] {400, 401, 403, 404, 422}) {
            int[] attempts = {0};
            assertThrows(IllegalStateException.class, () -> SpeechRetry.run(() -> {
                attempts[0]++;
                throw new SpeechRetry.HttpFailure(status, 0);
            }, () -> { }, ms -> fail("Unexpected retry")));
            assertEquals(1, attempts[0]);
        }
        assertFalse(SpeechRetry.retryable(new IllegalArgumentException("Bad voice")));
        assertFalse(SpeechRetry.retryable(new javax.net.ssl.SSLHandshakeException("Bad certificate")));
    }

    @Test public void rateLimitsAndServerErrorsRetryAndRespectRetryAfter() throws Exception {
        for (int status : new int[] {408, 429, 500, 502, 503, 504}) {
            int[] attempts = {0};
            long[] waited = {0};
            SpeechRetry.run(() -> {
                if (++attempts[0] == 1) { throw new SpeechRetry.HttpFailure(status, 3000); }
                return "audio";
            }, () -> { }, ms -> waited[0] += ms);
            assertEquals(2, attempts[0]);
            assertEquals(3000, waited[0]);
        }
    }

    @Test public void cancellationDuringBackoffPreventsAnotherRequest() {
        boolean[] cancelled = {false};
        int[] attempts = {0};
        assertThrows(CancellationException.class, () -> SpeechRetry.run(() -> {
            attempts[0]++;
            throw new SocketTimeoutException();
        }, () -> { if (cancelled[0]) { throw new CancellationException(); } }, ms -> cancelled[0] = true));
        assertEquals(1, attempts[0]);
    }
    @Test public void retryAfterHeaderIsBoundedAndInvalidValuesAreIgnored() {
        assertEquals(3500, SpeechRetry.retryAfter("3.5"));
        assertEquals(30000, SpeechRetry.retryAfter("90"));
        assertEquals(0, SpeechRetry.retryAfter("invalid"));
        assertEquals(0, SpeechRetry.retryAfter(null));
    }

    @Test public void unlimitedRetriesSurviveManyFailuresWithBoundedBackoff() throws Exception {
        int[] attempts = {0};
        java.util.List<Long> delays = new java.util.ArrayList<>();
        String result = SpeechRetry.untilCancelled(() -> {
            if (++attempts[0] <= 12) { throw new java.net.SocketTimeoutException(); }
            return "audio";
        }, () -> { }, ms -> { }, (attempt, delay) -> delays.add(delay));
        assertEquals("audio", result);
        assertEquals(13, attempts[0]);
        assertEquals(12, delays.size());
        assertEquals(Long.valueOf(1000), delays.get(0));
        assertEquals(Long.valueOf(30000), delays.get(11));
        for (long delay : delays) { assertTrue(delay <= 30000); }
    }

    @Test public void unlimitedRetriesStillStopOnCancellationOrPermanentErrors() {
        int[] attempts = {0};
        boolean[] cancelled = {false};
        assertThrows(CancellationException.class, () -> SpeechRetry.untilCancelled(() -> {
            attempts[0]++;
            throw new IOException("offline");
        }, () -> { if (cancelled[0]) { throw new CancellationException(); } }, ms -> { },
                (attempt, delay) -> { if (attempt == 8) { cancelled[0] = true; } }));
        assertEquals(8, attempts[0]);
        int[] permanent = {0};
        assertThrows(IllegalStateException.class, () -> SpeechRetry.untilCancelled(() -> {
            permanent[0]++;
            throw new SpeechRetry.HttpFailure(401, 0);
        }, () -> { }, ms -> fail("Should not wait"), (attempt, delay) -> fail("Should not retry")));
        assertEquals(1, permanent[0]);
    }

}
