package de.jeisfeld.mymeditation.audio;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class SpokenTextRangesTest {
    @Test public void repeatedSentencesHighlightTheirOwnOccurrenceWithOriginalWhitespace() {
        String text="  Breathe.\n\nBreathe. Relax";
        List<String> segments=SpeechSegments.split(text);
        assertArrayEquals(new int[]{2,10},SpokenTextRanges.range(text,segments,0));
        assertArrayEquals(new int[]{12,20},SpokenTextRanges.range(text,segments,1));
        assertArrayEquals(new int[]{21,26},SpokenTextRanges.range(text,segments,2));
    }
    @Test public void respectsProviderChunksAndInvalidPositionsWithoutChangingText() {
        String text=String.join("",Collections.nCopies(1200,"x"));
        List<String> segments=SpeechSegments.split(text,500);
        assertArrayEquals(new int[]{500,1000},SpokenTextRanges.range(text,segments,1));
        assertArrayEquals(new int[]{-1,-1},SpokenTextRanges.range(text,segments,-1));
        assertArrayEquals(new int[]{-1,-1},SpokenTextRanges.range("changed",segments,0));
        assertArrayEquals(new int[]{-1,-1},SpokenTextRanges.range(text,segments,10));
    }
    @Test public void cursorOffsetsResolveRepeatedSentencesWhitespaceAndEndOfText() {
        String text = "  Breathe.\n\nBreathe. Relax  ";
        List<String> parts = SpeechSegments.split(text);
        assertEquals(0, SpokenTextRanges.position(text, parts, 0));
        assertEquals(1, SpokenTextRanges.position(text, parts, 10));
        assertEquals(1, SpokenTextRanges.position(text, parts, 15));
        assertEquals(2, SpokenTextRanges.position(text, parts, text.length()));
        assertEquals(-1, SpokenTextRanges.position(text, parts, -1));
        assertEquals(-1, SpokenTextRanges.position(text, parts, text.length() + 1));
        assertEquals(-1, SpokenTextRanges.position("", Collections.emptyList(), 0));
    }
    @Test public void cursorMappingUsesProviderChunkOffsetsAndWaitSegments() {
        String text = String.join("", Collections.nCopies(1200, "呼吸")) + "[wait 10s]Rest.";
        List<String> parts = SpeechSegments.split(text, 500);
        assertEquals(2, SpokenTextRanges.position(text, parts, 1001));
        assertEquals(5, SpokenTextRanges.position(text, parts, text.indexOf("10s")));
        assertEquals(6, SpokenTextRanges.position(text, parts, text.indexOf("Rest")));
    }

}
