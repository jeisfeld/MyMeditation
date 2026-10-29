package de.jeisfeld.mymeditation.audio;

import de.sciss.jump3r.mp3.*;
import de.sciss.jump3r.mpg.Common;
import de.sciss.jump3r.mpg.Interface;
import de.sciss.jump3r.mpg.MPGLib;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Streaming 44.1 kHz mono PCM-to-MP3 encoder, independent of Android and native ABIs. */
public final class Mp3Writer implements Closeable {
    public static final int SAMPLE_RATE = 44100;
    private final Lame lame = new Lame();
    private final LameGlobalFlags flags;
    private final OutputStream output;
    private final int[] samples = new int[4096];
    private final byte[] encoded = new byte[16384];
    private boolean closed;
    private long frames;

    public Mp3Writer(OutputStream output) throws IOException {
        this.output = output;
        GainAnalysis gain = new GainAnalysis(); BitStream bits = new BitStream(); Presets presets = new Presets();
        QuantizePVT privateQuantize = new QuantizePVT(); Quantize quantize = new Quantize();
        VBRTag tags = new VBRTag(); Version version = new Version(); ID3Tag id3 = new ID3Tag();
        Reservoir reservoir = new Reservoir(); Takehiro takehiro = new Takehiro();
        MPGLib decoder = new MPGLib(); Interface decoderInterface = new Interface(); Common common = new Common();
        lame.setModules(gain, bits, presets, privateQuantize, quantize, tags, version, id3, decoder);
        bits.setModules(gain, decoder, version, tags); id3.setModules(bits, version); presets.setModules(lame);
        quantize.setModules(bits, reservoir, privateQuantize, takehiro);
        privateQuantize.setModules(takehiro, reservoir, lame.enc.psy);
        reservoir.setModules(bits); takehiro.setModules(privateQuantize); tags.setModules(lame, bits, version);
        decoder.setModules(decoderInterface, common); decoderInterface.setModules(tags, common);
        flags = lame.lame_init(); flags.num_channels = 1; flags.in_samplerate = SAMPLE_RATE;
        flags.out_samplerate = SAMPLE_RATE; flags.mode = MPEGMode.MONO; flags.brate = 128; flags.quality = 2;
        flags.bWriteVbrTag = false; flags.write_id3tag_automatic = false; flags.findReplayGain = false;
        id3.id3tag_init(flags);
        if (lame.lame_init_params(flags) < 0) { lame.lame_close(flags); throw new IOException("MP3 encoder initialization failed"); }
    }
    public long frames() { return frames; }
    public void write(ByteBuffer pcm) throws IOException {
        if (closed || (pcm.remaining() & 1) != 0) { throw new IOException("Invalid PCM data"); }
        pcm.order(ByteOrder.LITTLE_ENDIAN);
        while (pcm.hasRemaining()) {
            int count = Math.min(samples.length, pcm.remaining() / 2);
            for (int i = 0; i < count; i++) { samples[i] = pcm.getShort() << 16; }
            encode(count); frames += count;
        }
    }
    public void silence(double seconds, Runnable cancellation) throws IOException {
        long remaining = Math.round(seconds * SAMPLE_RATE);
        java.util.Arrays.fill(samples, 0);
        while (remaining > 0) {
            cancellation.run(); int count = (int) Math.min(samples.length, remaining);
            encode(count); frames += count; remaining -= count;
        }
    }
    private void encode(int count) throws IOException {
        int size = lame.lame_encode_buffer_int(flags, samples, samples, count, encoded, 0, encoded.length);
        if (size < 0) { throw new IOException("MP3 encoding failed"); }
        output.write(encoded, 0, size);
    }
    @Override public void close() throws IOException {
        if (closed) { return; }
        closed = true;
        try {
            int size = lame.lame_encode_flush(flags, encoded, 0, encoded.length);
            if (size < 0) { throw new IOException("MP3 encoding failed"); }
            output.write(encoded, 0, size); output.flush();
        } finally { lame.lame_close(flags); output.close(); }
    }
}
