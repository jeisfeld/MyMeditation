package de.jeisfeld.mymeditation.audio;
import org.junit.Test;
import static org.junit.Assert.*;
public class PlaybackScalesTest {
    @Test public void speedAnchorsMatchRequestedWidthDistribution() {
        int[] positions = {0,100,200,500,800,900,1000};
        double[] speeds = {0.25,0.5,0.7,1,1.4,2,4};
        for (int i=0;i<positions.length;i++) { assertEquals(speeds[i], PlaybackScales.speed(positions[i]), 0.00001); }
    }
    @Test public void speedIsMonotonicAndAllFiveHundredthStepsAreReachable() {
        double previous=0;
        for(int i=0;i<=1000;i++) { double value=PlaybackScales.speed(i); assertTrue(value>=previous); previous=value; }
        for(int i=5;i<=80;i++) { double value=i/20.0; assertEquals(value,PlaybackScales.speed(PlaybackScales.speedProgress(value)),0.00001); }
    }
    @Test public void pauseHasEveryHalfSecondThroughTenAndWholeSecondsAfterwards() {
        for(int i=0;i<=20;i++) { assertEquals(i*0.5,PlaybackScales.pause(i),0.0001); }
        for(int i=0;i<=40;i++) { assertEquals(i,PlaybackScales.pauseProgress(PlaybackScales.pause(i))); }
        assertEquals(30,PlaybackScales.pause(40),0.0001);
    }
}
