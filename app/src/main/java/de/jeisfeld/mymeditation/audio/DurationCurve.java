package de.jeisfeld.mymeditation.audio;

import java.util.List;

/** Inverse of the two continuous pause ranges, with speech time fixed at the current speed. */
public final class DurationCurve {
    private final double speech, waits, gaps, currentPause;
    public DurationCurve(PlaybackRequest request, long currentMilliseconds) {
        if (currentMilliseconds < 0 || !Double.isFinite(request.pauseSeconds)) { throw new IllegalArgumentException(); }
        currentPause = request.pauseSeconds;
        List<String> segments = SpeechSegments.split(request.text, request.backend.segmentLimit);
        double waitTotal = 0, gapTotal = 0;
        for (int i = SpeechSegments.nextSpeech(segments, 0); i < segments.size(); i++) {
            gapTotal += MeditationPauses.gapBefore(segments, i, 1) * 1000;
            double wait = MeditationPauses.seconds(segments.get(i));
            if (wait >= 0) { waitTotal += wait * 1000; }
        }
        waits = waitTotal; gaps = gapTotal;
        speech = Math.max(0, currentMilliseconds - silence(currentPause));
    }
    private double silence(double pause) { return pause <= 1 ? (waits + gaps) * pause : waits + gaps * pause; }
    public long milliseconds(double pause) {
        if (!Double.isFinite(pause) || pause < 0 || pause > 30) { throw new IllegalArgumentException(); }
        return Math.round(speech + silence(pause));
    }
    public long minimum() { return milliseconds(0); }
    public long maximum() { return milliseconds(30); }
    public long current() { return milliseconds(currentPause); }
    public boolean adjustable() { return waits + gaps > 0; }

    /** Endpoints accept the same rounded second displayed in the heading/range. */
    public double pauseFor(long targetMilliseconds) {
        if (targetMilliseconds < 0) { throw new IllegalArgumentException(); }
        double minimum = speech, maximum = speech + silence(30);
        double target = targetMilliseconds;
        if (target < minimum) {
            if (Math.round(minimum / 1000) != Math.round(target / 1000)) { throw new IllegalArgumentException(); }
            target = minimum;
        }
        if (target > maximum) {
            if (Math.round(maximum / 1000) != Math.round(target / 1000)) { throw new IllegalArgumentException(); }
            target = maximum;
        }
        if (!adjustable()) { return currentPause; }
        double pause = target <= speech + silence(1) ? (target - speech) / (waits + gaps)
                : (target - speech - waits) / gaps;
        return Math.max(0, Math.min(30, pause));
    }

    public static long parse(String text) {
        text = text.trim();
        if (!text.matches("[0-9]+:[0-5][0-9]")) { throw new IllegalArgumentException(); }
        String[] parts = text.split(":");
        try {
            return Math.addExact(Math.multiplyExact(Long.parseLong(parts[0]), 60000), Long.parseLong(parts[1]) * 1000);
        } catch (ArithmeticException | NumberFormatException error) { throw new IllegalArgumentException(); }
    }
}
