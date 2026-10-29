package de.jeisfeld.mymeditation.audio;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CancellationException;
import org.junit.Test;
import static org.junit.Assert.*;

public class Mp3WriterTest {
    @Test public void encodesRealMp3FramesWithExactPcmAndPauseAccounting() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (Mp3Writer writer = new Mp3Writer(output)) {
            ByteBuffer tone = ByteBuffer.allocate(Mp3Writer.SAMPLE_RATE * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < Mp3Writer.SAMPLE_RATE; i++) { tone.putShort((short) (10000 * Math.sin(2 * Math.PI * 440 * i / Mp3Writer.SAMPLE_RATE))); }
            tone.flip(); writer.write(tone); writer.silence(0.5, () -> { });
            assertEquals(Mp3Writer.SAMPLE_RATE * 3 / 2, writer.frames());
        }
        byte[] mp3 = output.toByteArray();
        assertTrue(mp3.length > 20000);
        assertEquals(0xff, mp3[0] & 255);
        assertEquals(0xe0, mp3[1] & 0xe0); // MPEG frame sync, rather than WAV bytes renamed as MP3.
        assertFalse(new String(mp3, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF"));
    }
    @Test public void cancellationInterruptsLongPausesAndIncompletePcmIsRejected() throws Exception {
        try (Mp3Writer writer = new Mp3Writer(new ByteArrayOutputStream())) {
            assertThrows(java.io.IOException.class, () -> writer.write(ByteBuffer.wrap(new byte[1])));
            int[] checks = {0};
            assertThrows(CancellationException.class, () -> writer.silence(30, () -> {
                if (++checks[0] == 3) { throw new CancellationException(); }
            }));
            assertTrue(writer.frames() < Mp3Writer.SAMPLE_RATE);
        }
    }
}
