package de.jeisfeld.mymeditation.audio;

/** Slider positions are independent of persisted physical values. */
public final class PlaybackScales {
    private PlaybackScales() { }
    public static double pause(int progress) { return progress <= 20 ? progress * 0.5 : 10 + progress - 20; }
    public static int pauseProgress(double seconds) { return seconds <= 10 ? (int) Math.round(seconds * 2) : 20 + (int) Math.round(seconds - 10); }
    private static final double[] X = {0, 0.1, 0.2, 0.5, 0.8, 0.9, 1};
    private static final double[] Y = {0.25, 0.5, 0.7, 1, 1.4, 2, 4};
    public static double speed(int progress) {
        double x = Math.max(0, Math.min(1000, progress)) / 1000.0;
        for (int i = 1; i < X.length; i++) {
            if (x <= X[i]) {
                double raw = Y[i-1] * Math.pow(Y[i] / Y[i-1], (x-X[i-1])/(X[i]-X[i-1]));
                return Math.max(0.25, Math.min(4, Math.round(raw * 20) / 20.0));
            }
        }
        return 4;
    }
    public static int speedProgress(double speed) {
        speed = Math.max(0.25, Math.min(4, speed));
        for (int i = 1; i < Y.length; i++) {
            if (speed <= Y[i]) { return (int) Math.round(1000 * (X[i-1] + (X[i]-X[i-1]) * Math.log(speed/Y[i-1])/Math.log(Y[i]/Y[i-1]))); }
        }
        return 1000;
    }
}
