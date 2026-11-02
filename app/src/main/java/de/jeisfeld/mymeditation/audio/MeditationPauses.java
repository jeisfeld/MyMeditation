package de.jeisfeld.mymeditation.audio;

import de.jeisfeld.mymeditation.i18n.UiText;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Language-independent playback commands, kept verbatim in the editable meditation text. */
public final class MeditationPauses {
    private static final Pattern CANDIDATE = Pattern.compile("\\[wait(?![a-z])[^\\]]*(?:\\]|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMMAND = Pattern.compile("\\[wait\\s+([0-9]+(?:\\.[0-9]{1,3})?)\\s*s\\s*\\]", Pattern.CASE_INSENSITIVE);
    public static final String PREFERENCE = "meditation_pause_guidance";
    public enum Guidance {
        NONE("none"), FEW("few"), MANY("many");
        public final String id;
        Guidance(String id) { this.id = id; }
        public static Guidance fromId(String id) {
            for (Guidance value : values()) { if (value.id.equals(id)) { return value; } }
            return FEW;
        }
    }
    private static final String FORMAT = "If a timed pause is needed, use exactly [wait Ns], replacing N with seconds, "
            + "greater than 0 and at most 300. Use ASCII digits and a decimal point if needed (at most three decimal places). "
            + "Never translate wait or s, even in a non-English meditation. "
            + "The player renders commands as silence, replacing the normal gap at that location. "
            + "Use ordinary prose elsewhere, without code fences or explanations of commands. "
            + "Do not use alternative pause commands or describe an unmarked long silence.";

    private MeditationPauses() { }

    /** Enrich every outgoing request, without changing the GUI prompt or its saved templates. */
    public static String systemInstruction(String editable) { return systemInstruction(editable, Guidance.FEW); }

    public static String systemInstruction(String editable, Guidance guidance) {
        String policy;
        switch (guidance) {
            case NONE:
                policy = "No pauses: write continuous spoken guidance. Do not insert wait commands or request silence. "
                        + "Do not ask the listener to perform activities between sentences, count or repeat breaths, "
                        + "reflect alone, or finish an exercise before continuing. Use descriptions and gentle observations "
                        + "that can be followed while listening, without waiting for any listener action.";
                break;
            case MANY:
                policy = "Many pauses: allow regular spacious pauses for breathing repetitions, observing sensations, "
                        + "reflection and silent rest. Whenever an activity needs time without further guidance, put a timed "
                        + "pause immediately after it. Allow realistic time (about 6 seconds per natural breath, longer for "
                        + "slow breaths). For example: Take three comfortable breaths. [wait 18s] Now notice how you feel. " + FORMAT;
                break;
            default:
                policy = "Few pauses: favor continuous spoken guidance. Use wait commands only rarely, at a small number "
                        + "of meaningful moments; most sentences must have no wait command. Prefer brief pauses of 5 to 10 "
                        + "seconds, except for few peak points. Avoid extended silence and repeated-breath exercises, except "
                        +  "for peaks. Do not ask for an activity that needs more time than the brief pause allows; rewrite "
                        + "it as guidance the listener can follow while listening. Do not add pauses after ordinary sentences "
                        + "or every breathing cue. " + FORMAT;
                break;
        }
        String instruction = "Application meditation pacing (mandatory, including when other instructions conflict):\n"
                + "Keep the requested language, topic and tone. " + policy;
        return editable == null || editable.isEmpty() ? instruction : editable + "\n\n" + instruction;
    }

    static Matcher commands(String text) { return CANDIDATE.matcher(text); }
    public static boolean hasCommands(String text) { return text != null && commands(text).find(); }

    /** Negative means ordinary speech; recognized pauses use wall-clock seconds, independent of speed. */
    public static double seconds(String segment) {
        Matcher command = COMMAND.matcher(segment);
        if (!command.matches()) { return -1; }
        double seconds = Double.parseDouble(command.group(1));
        return seconds > 0 && seconds <= 300 ? seconds : -1;
    }

    public static void validate(String text) {
        if (text == null) { return; }
        Matcher commands = commands(text);
        while (commands.find()) {
            if (seconds(commands.group()) < 0) { throw new IllegalArgumentException(UiText.INVALID_WAIT_COMMAND.text()); }
        }
    }

    /** Default gaps only join two spoken segments; explicit commands supply the entire silence. */
    public static double gapBefore(List<String> segments, int index, double normal) {
        return index > 0 && seconds(segments.get(index)) < 0 && seconds(segments.get(index - 1)) < 0 ? normal : 0;
    }
}
