package de.jeisfeld.mymeditation.audio;

import java.util.List;

/** Sequential matching distinguishes repeated sentences and preserves original whitespace offsets. */
public final class SpokenTextRanges {
    private SpokenTextRanges() { }
    /** Whitespace between segments belongs to the following segment; trailing space to the last. */
    public static int position(String text, List<String> segments, int offset) {
        if (text == null || offset < 0 || offset > text.length() || segments.isEmpty()) { return -1; }
        int cursor = 0;
        for (int i = 0; i < segments.size(); i++) {
            String segment = segments.get(i);
            int start = text.indexOf(segment, cursor);
            if (start < 0) { return -1; }
            cursor = start + segment.length();
            if (offset < cursor || i == segments.size() - 1) { return i; }
        }
        return -1;
    }
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
