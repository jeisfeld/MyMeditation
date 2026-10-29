package de.jeisfeld.mymeditation.audio;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.SonicAudioProcessor;
import androidx.media3.common.util.UnstableApi;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Bounded decoding, mono downmix, pitch-preserving speed adjustment and resampling. */
@androidx.annotation.OptIn(markerClass = UnstableApi.class)
public final class OfflineAudioRenderer {
    private OfflineAudioRenderer() { }
    public static void append(File source, double speed, Mp3Writer writer, Runnable cancellation) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        SonicAudioProcessor processor = null;
        boolean started = false;
        try {
            extractor.setDataSource(source.getAbsolutePath());
            MediaFormat format = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat candidate = extractor.getTrackFormat(i);
                String mime = candidate.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) { format = candidate; extractor.selectTrack(i); break; }
            }
            if (format == null) { throw new IOException("No audio track"); }
            if ("audio/raw".equals(format.getString(MediaFormat.KEY_MIME))) {
                int rawRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                int rawChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                int rawEncoding = format.containsKey(MediaFormat.KEY_PCM_ENCODING)
                        ? format.getInteger(MediaFormat.KEY_PCM_ENCODING) : AudioFormat.ENCODING_PCM_16BIT;
                processor = processor(rawRate, rawChannels, rawEncoding, speed);
                int capacity = format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE) ? format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) : 1024 * 1024;
                ByteBuffer raw = ByteBuffer.allocateDirect(Math.max(65536, Math.min(20 * 1024 * 1024, capacity)));
                while (true) {
                    cancellation.run(); raw.clear();
                    int size = extractor.readSampleData(raw, 0);
                    if (size < 0) { break; }
                    raw.position(0); raw.limit(size);
                    queue(raw, rawChannels, rawEncoding, processor, writer);
                    extractor.advance();
                }
                finish(processor, writer, cancellation);
                return;
            }
            decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
            decoder.configure(format, null, null, 0); decoder.start(); started = true;
            boolean inputDone = false, outputDone = false;
            int rate = 0, channels = 0, encoding = AudioFormat.ENCODING_PCM_16BIT;
            long lastOutput = android.os.SystemClock.elapsedRealtime();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            while (!outputDone) {
                cancellation.run();
                if (!inputDone) {
                    int index = decoder.dequeueInputBuffer(10000);
                    if (index >= 0) {
                        ByteBuffer input = decoder.getInputBuffer(index); input.clear();
                        int size = extractor.readSampleData(input, 0);
                        if (size < 0) { decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true; }
                        else { decoder.queueInputBuffer(index, 0, size, Math.max(0, extractor.getSampleTime()), 0); extractor.advance(); }
                    }
                }
                int index = decoder.dequeueOutputBuffer(info, 10000);
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat output = decoder.getOutputFormat();
                    int nextRate = output.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    int nextChannels = output.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if (processor != null && (nextRate != rate || nextChannels != channels)) { throw new IOException("Audio format changed"); }
                    rate = nextRate; channels = nextChannels;
                    encoding = output.containsKey(MediaFormat.KEY_PCM_ENCODING) ? output.getInteger(MediaFormat.KEY_PCM_ENCODING) : AudioFormat.ENCODING_PCM_16BIT;
                    if (processor != null) { throw new IOException("Repeated audio format change"); }
                    processor = processor(rate, channels, encoding, speed);
                } else if (index >= 0) {
                    lastOutput = android.os.SystemClock.elapsedRealtime();
                    try {
                        if (info.size > 0) {
                            if (processor == null) { throw new IOException("Missing decoded audio format"); }
                            ByteBuffer decoded = decoder.getOutputBuffer(index).duplicate().order(ByteOrder.LITTLE_ENDIAN);
                            decoded.position(info.offset); decoded.limit(info.offset + info.size);
                            queue(decoded, channels, encoding, processor, writer);
                        }
                    } finally { decoder.releaseOutputBuffer(index, false); }
                    outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                }
                if (android.os.SystemClock.elapsedRealtime() - lastOutput > 30000) { throw new IOException("Audio decoder stalled"); }
            }
            if (processor != null) { finish(processor, writer, cancellation); }
        } finally {
            if (processor != null) { processor.reset(); }
            if (decoder != null) { try { if (started) { decoder.stop(); } } finally { decoder.release(); } }
            extractor.release();
        }
    }
    private static SonicAudioProcessor processor(int rate, int channels, int encoding, double speed) throws Exception {
        if (channels < 1 || channels > 8 || rate < 8000 || rate > 192000
                || (encoding != AudioFormat.ENCODING_PCM_16BIT && encoding != AudioFormat.ENCODING_PCM_FLOAT)) {
            throw new IOException("Unsupported decoded audio format");
        }
        SonicAudioProcessor processor = new SonicAudioProcessor();
        processor.setSpeed((float) speed); processor.setPitch(1f); processor.setOutputSampleRateHz(Mp3Writer.SAMPLE_RATE);
        processor.configure(new AudioProcessor.AudioFormat(rate, 1, C.ENCODING_PCM_16BIT)); processor.flush();
        return processor;
    }
    private static void queue(ByteBuffer decoded, int channels, int encoding, SonicAudioProcessor processor, Mp3Writer writer) throws Exception {
        decoded.order(ByteOrder.LITTLE_ENDIAN);
        int bytesPerFrame = channels * (encoding == AudioFormat.ENCODING_PCM_FLOAT ? 4 : 2);
        if (decoded.remaining() % bytesPerFrame != 0) { throw new IOException("Incomplete audio frame"); }
        ByteBuffer mono = ByteBuffer.allocateDirect(decoded.remaining() / bytesPerFrame * 2).order(ByteOrder.nativeOrder());
        while (decoded.hasRemaining()) {
            double sample = 0;
            for (int c = 0; c < channels; c++) {
                sample += encoding == AudioFormat.ENCODING_PCM_FLOAT ? decoded.getFloat() * 32767 : decoded.getShort();
            }
            mono.putShort((short) Math.max(-32768, Math.min(32767, Math.round(sample / channels))));
        }
        mono.flip();
        if (processor.isActive()) { processor.queueInput(mono); writer.write(processor.getOutput()); }
        else { writer.write(mono); }
    }
    private static void finish(SonicAudioProcessor processor, Mp3Writer writer, Runnable cancellation) throws Exception {
        if (!processor.isActive()) { return; }
        processor.queueEndOfStream();
        while (!processor.isEnded()) { cancellation.run(); writer.write(processor.getOutput()); }
    }

}
