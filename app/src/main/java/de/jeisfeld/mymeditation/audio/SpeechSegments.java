package de.jeisfeld.mymeditation.audio;

import de.jeisfeld.mymeditation.i18n.UiText;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Sentence controls also retain trailing text and bound individual API requests. */
public final class SpeechSegments {
    private SpeechSegments() { }

    public static List<String> split(String text) {
        return split(text, 3500);
    }

    public static List<String> split(String text, int limit) {
        if (limit < 2 || limit > 3500) { throw new IllegalArgumentException(UiText.INVALID_SEGMENT_LIMIT.text()); }
        List<String> result = new ArrayList<>();
        if (text == null) { return result; }
        Matcher matcher = Pattern.compile("[^.!?。！？]+(?:[.!?。！？]+|$)").matcher(text);
        while (matcher.find()) {
            String sentence = matcher.group().trim();
            while (sentence.length() > limit) {
                int cut = sentence.lastIndexOf(' ', limit);
                if (cut <= 0) { cut = limit; }
                if (Character.isHighSurrogate(sentence.charAt(cut - 1))) { cut--; }
                result.add(sentence.substring(0, cut).trim());
                sentence = sentence.substring(cut).trim();
            }
            if (!sentence.isEmpty()) { result.add(sentence); }
        }
        return result;
    }
}
