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
}
