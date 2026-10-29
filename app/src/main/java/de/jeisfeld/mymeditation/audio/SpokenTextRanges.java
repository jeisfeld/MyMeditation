package de.jeisfeld.mymeditation.audio;

import java.util.List;

/** Sequential matching distinguishes repeated sentences and preserves original whitespace offsets. */
public final class SpokenTextRanges {
    private SpokenTextRanges() { }
    public static int[] range(String text, List<String> segments, int position) {
        if (text == null || position < 0 || position >= segments.size()) { return new int[] {-1,-1}; }
        int cursor = 0;
        for (int i = 0; i <= position; i++) {
            String segment = segments.get(i);
            int start = text.indexOf(segment, cursor);
            if (start < 0) { return new int[] {-1,-1}; }
            if (i == position) { return new int[] {start, start + segment.length()}; }
            cursor = start + segment.length();
        }
        return new int[] {-1,-1};
    }
}
