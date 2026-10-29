package de.jeisfeld.mymeditation.audio;

/** Main-thread policy: only an unresolved temporary interruption may cause automatic replay. */
public final class AudioFocusRecovery {
    public enum Change { GAIN, TEMPORARY_LOSS, PERMANENT_LOSS }
    public enum Action { NONE, SUSPEND, RESUME, STOP }
    private boolean enabled, waiting;
    private int sentence;
    public void start() { enabled = true; waiting = false; }
    public void cancel() { enabled = false; waiting = false; }
    public int sentence() { return sentence; }
    public Action change(Change change, int currentSentence) {
        if (!enabled) { return Action.NONE; }
        switch (change) {
            case TEMPORARY_LOSS:
                if (waiting) { return Action.NONE; }
                waiting = true; sentence = currentSentence; return Action.SUSPEND;
            case GAIN:
                if (!waiting) { return Action.NONE; }
                waiting = false; return Action.RESUME;
            case PERMANENT_LOSS:
                cancel(); return Action.STOP;
            default: return Action.NONE;
        }
    }
}
