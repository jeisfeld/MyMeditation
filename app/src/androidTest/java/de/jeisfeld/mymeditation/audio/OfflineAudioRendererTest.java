package de.jeisfeld.mymeditation.audio;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Exercises the device's WAV/MP3 extractor and decoder, not provider accounts or network. */
@RunWith(AndroidJUnit4.class)
public class OfflineAudioRendererTest {
    private File wav() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File file = File.createTempFile("offline-test-", ".wav", context.getCacheDir());
        int rate = 24000, bytes = rate * 2;
        ByteBuffer data = ByteBuffer.allocate(44 + bytes).order(ByteOrder.LITTLE_ENDIAN);
        data.put("RIFF".getBytes("US-ASCII")).putInt(bytes + 36).put("WAVEfmt ".getBytes("US-ASCII"))
                .putInt(16).putShort((short) 1).putShort((short) 1).putInt(rate).putInt(rate * 2)
                .putShort((short) 2).putShort((short) 16).put("data".getBytes("US-ASCII")).putInt(bytes);
        for (int i = 0; i < rate; i++) { data.putShort((short) (10000 * Math.sin(2 * Math.PI * 440 * i / rate))); }
        try (FileOutputStream output = new FileOutputStream(file)) { output.write(data.array()); }
        return file;
    }
    @Test public void wavResamplingAndSpeedProduceExpectedDurationWithIndependentPauses() throws Exception {
        File file = wav();
        try {
            for (double speed : new double[] {0.25, 0.5, 1, 2, 4}) {
                try (Mp3Writer writer = new Mp3Writer(new ByteArrayOutputStream())) {
                    OfflineAudioRenderer.append(file, speed, writer, () -> { });
                    assertEquals(Mp3Writer.SAMPLE_RATE / speed, writer.frames(), Mp3Writer.SAMPLE_RATE * 0.08);
                    long speechFrames = writer.frames(); writer.silence(0.5, () -> { });
                    assertEquals(speechFrames + Mp3Writer.SAMPLE_RATE / 2, writer.frames());
                }
            }
        } finally { file.delete(); }
    }
    @Test public void mp3InputDecodesAndCancellationStopsRendering() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File input = File.createTempFile("offline-test-", ".mp3", context.getCacheDir());
        try {
            try (Mp3Writer writer = new Mp3Writer(new FileOutputStream(input))) {
                writer.silence(1, () -> { });
            }
            try (Mp3Writer writer = new Mp3Writer(new ByteArrayOutputStream())) {
                OfflineAudioRenderer.append(input, 1, writer, () -> { });
                assertEquals(Mp3Writer.SAMPLE_RATE, writer.frames(), Mp3Writer.SAMPLE_RATE * 0.1);
                assertThrows(java.util.concurrent.CancellationException.class, () ->
                        OfflineAudioRenderer.append(input, 1, writer, () -> { throw new java.util.concurrent.CancellationException(); }));
            }
        } finally { input.delete(); }
    }
}
