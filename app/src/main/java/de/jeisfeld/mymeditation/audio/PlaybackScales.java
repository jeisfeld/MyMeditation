package de.jeisfeld.mymeditation.audio;

/** Slider positions are independent of persisted physical values. */
public final class PlaybackScales {
    private PlaybackScales() { }
    public static double pause(int progress) {
        progress = Math.max(0, Math.min(1000, progress));
        if (progress <= 500) { return Math.round(progress / 25.0) / 20.0; }
        int step = (int) Math.round((progress - 500) * 38.0 / 500);
        return step <= 18 ? 1 + step * 0.5 : 10 + step - 18;
    }
    public static int pauseProgress(double value) {
        value = Math.max(0, Math.min(30, value));
        if (value <= 1) { return (int) Math.round(value * 500); }
        double step = value <= 10 ? (value - 1) * 2 : 18 + value - 10;
        return 500 + (int) Math.round(step * 500 / 38);
    }
    private static final double[] X = {0, 0.1, 0.2, 0.5, 0.8, 0.9, 1};
    private static final double[] Y = {0.25, 0.5, 0.7, 1, 1.4, 2, 4};
    public static double speed(int progress) {
        double x = Math.max(0, Math.min(1000, progress)) / 1000.0;
        for (int i = 1; i < X.length; i++) {
            if (x <= X[i]) {
                double raw = Y[i-1] * Math.pow(Y[i] / Y[i-1], (x-X[i-1])/(X[i]-X[i-1]));
                double stepsPerUnit = raw <= 1.5 ? 20 : raw <= 2.6 ? 10 : 5;
                return Math.max(0.25, Math.min(4, Math.round(raw * stepsPerUnit) / stepsPerUnit));
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
