package de.jeisfeld.mymeditation.http;

import de.jeisfeld.mymeditation.i18n.UiText;
import java.io.IOException;
import java.net.ProtocolException;
import java.nio.file.FileSystemException;
import java.util.concurrent.CancellationException;
import javax.net.ssl.SSLHandshakeException;

/** Three total attempts; waits and transient failures are injectable for deterministic tests. */
final class SpeechRetry {
    interface Operation<T> { T run() throws Exception; }
    interface Cancellation { void check(); }
    interface Wait { void sleep(long milliseconds) throws InterruptedException; }
    static final class HttpFailure extends IOException {
        final int status;
        final long retryAfterMs;
        HttpFailure(int status, long retryAfterMs) {
            super(UiText.SPEECH_HTTP.text(status));
            this.status = status;
            this.retryAfterMs = retryAfterMs;
        }
    }
    static final class TransientGenerationFailure extends IOException {
        TransientGenerationFailure(String message) { super(message); }
    }
    private SpeechRetry() { }

    static <T> T run(Operation<T> operation, Cancellation cancellation, Wait wait) throws Exception {
        return run(operation, cancellation, wait, false, (attempt, delay) -> { });
    }

    static <T> T untilCancelled(Operation<T> operation, Cancellation cancellation, Wait wait,
            java.util.function.BiConsumer<Integer, Long> progress) throws Exception {
        return run(operation, cancellation, wait, true, progress);
    }

    private static <T> T run(Operation<T> operation, Cancellation cancellation, Wait wait, boolean unlimited,
            java.util.function.BiConsumer<Integer, Long> progress) throws Exception {
        for (int attempt = 0; ; attempt = Math.min(attempt + 1, 1000000)) {
            cancellation.check();
            try { return operation.run(); }
            catch (Exception failure) {
                cancellation.check();
                if (!retryable(failure)) {
                    if (failure instanceof HttpFailure) { throw new IllegalStateException(failure.getMessage()); }
                    throw failure;
                }
                if (!unlimited && attempt >= 2) {
                    throw new IllegalStateException(UiText.RETRY_EXHAUSTED.text(failure instanceof HttpFailure || failure instanceof TransientGenerationFailure
                            ? failure.getMessage() : UiText.CONNECTION_AGAIN.text()), failure);
                }
                long delay = Math.min(30000, 1000L << Math.min(attempt, 5));
                if (failure instanceof HttpFailure) {
                    delay = Math.max(delay, ((HttpFailure) failure).retryAfterMs);
                }
                progress.accept(attempt + 1, Math.min(delay, 30000));
                // Check cancellation during backoff rather than waiting for the entire delay.
                for (long remaining = Math.min(delay, 30000); remaining > 0; remaining -= 100) {
                    cancellation.check();
                    try { wait.sleep(Math.min(100, remaining)); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new CancellationException(); }
                }
            }
        }
    }

    static long retryAfter(String value) {
        if (value == null) { return 0; }
        try { return (long) Math.min(30000, Math.max(0, Double.parseDouble(value) * 1000)); }
        catch (NumberFormatException e) {
            try {
                return Math.min(30000, Math.max(0, java.time.ZonedDateTime.parse(value,
                        java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - System.currentTimeMillis()));
            } catch (java.time.format.DateTimeParseException ignored) { return 0; }
        }
    }

    static boolean retryable(Exception error) {
        if (error instanceof HttpFailure) {
            int status = ((HttpFailure) error).status;
            return status == 408 || status == 429 || status == 500 || status == 502 || status == 503 || status == 504;
        }
        return error instanceof IOException && !(error instanceof SSLHandshakeException)
                && !(error instanceof ProtocolException) && !(error instanceof FileSystemException)
                && !(error instanceof java.io.FileNotFoundException);
    }
}
