package de.jeisfeld.mymeditation.audio;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import de.jeisfeld.mymeditation.http.SpeechBackend;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real Android WAV/MP3 decoding and local sharing assembly, with no provider access. */
@RunWith(AndroidJUnit4.class)
public class SentenceAudioDeviceTest {
    private void delete(File file) {
        File[] children = file.listFiles();
        if (children != null) { for (File child : children) { delete(child); } }
        file.delete();
    }
    @Test public void sentenceMp3sDecodeAndShareAtBakedSpeedWithIndependentPauses() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File root = new File(context.getCacheDir(), "sentence-device-test-" + System.nanoTime());
        assertTrue(root.mkdirs());
        try {
            File wav = new File(root, "input.wav");
            int rate = 24000, bytes = rate * 2;
            ByteBuffer data = ByteBuffer.allocate(44 + bytes).order(ByteOrder.LITTLE_ENDIAN);
            data.put("RIFF".getBytes("US-ASCII")).putInt(bytes + 36).put("WAVEfmt ".getBytes("US-ASCII"))
                    .putInt(16).putShort((short) 1).putShort((short) 1).putInt(rate).putInt(rate * 2)
                    .putShort((short) 2).putShort((short) 16).put("data".getBytes("US-ASCII")).putInt(bytes);
            for (int i = 0; i < rate; i++) { data.putShort((short) (10000 * Math.sin(2 * Math.PI * 440 * i / rate))); }
            try (FileOutputStream output = new FileOutputStream(wav)) { output.write(data.array()); }
            PlaybackRequest request = new PlaybackRequest("First. Second.", SpeechBackend.GEMINI,
                    "gemini-3.8-flash-tts", "Kore", 1.5, "", 0);
            SentenceAudioStore.Book book = new SentenceAudioStore(new File(root, "recordings")).open(request);
            for (int i = 0; i < 2; i++) {
                book.obtain(i, target -> {
                    try (FileOutputStream output = new FileOutputStream(target); Mp3Writer writer = new Mp3Writer(output)) {
                        OfflineAudioRenderer.append(wav, request.speed, writer, () -> { });
                    }
                }, () -> { });
            }
            book.finish();
            SentenceAudioAssembler.Decoder decoder = (file, writer, check) -> OfflineAudioRenderer.append(file, 1, writer, check);
            long noPause = SentenceAudioAssembler.write(book, 0, new ByteArrayOutputStream(), decoder, index -> { }, () -> { });
            long twoSeconds = SentenceAudioAssembler.write(book, 2, new ByteArrayOutputStream(), decoder, index -> { }, () -> { });
            // Applying the 1.5 speed again would shorten the output by about 0.44 seconds and fail.
            assertEquals(2 * Mp3Writer.SAMPLE_RATE / 1.5, noPause, Mp3Writer.SAMPLE_RATE * 0.15);
            assertEquals(noPause + 2L * Mp3Writer.SAMPLE_RATE, twoSeconds);
            assertTrue(book.isComplete());
        } finally { delete(root); }
    }
}
