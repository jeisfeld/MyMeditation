package de.jeisfeld.mymeditation.audio;
import org.junit.Test;
import static org.junit.Assert.*;
public class PlaybackScalesTest {
    @Test public void speedAnchorsMatchRequestedWidthDistribution() {
        int[] positions = {0,100,200,500,800,900,1000};
        double[] speeds = {0.25,0.5,0.7,1,1.4,2,4};
        for (int i=0;i<positions.length;i++) { assertEquals(speeds[i], PlaybackScales.speed(positions[i]), 0.00001); }
    }
    @Test public void speedIsMonotonicAndOffersExactlyTheRequestedSteps() {
        java.util.Set<Double> expected = new java.util.TreeSet<>(), actual = new java.util.TreeSet<>();
        for (int i = 5; i <= 30; i++) { expected.add(i / 20.0); }
        for (int i = 16; i <= 26; i++) { expected.add(i / 10.0); }
        for (int i = 14; i <= 20; i++) { expected.add(i / 5.0); }
        double previous = 0;
        for (int i = 0; i <= 1000; i++) {
            double value = PlaybackScales.speed(i);
            assertTrue(value >= previous); previous = value; actual.add(value);
        }
        assertEquals(expected, actual);
        for (double value : expected) {
            assertEquals(value, PlaybackScales.speed(PlaybackScales.speedProgress(value)), 0.00001);
        }
    }
    @Test public void pauseMidpointAndQuarterMatchOneSecondAndFiftyPercent() {
        assertEquals(0, PlaybackScales.pause(0), 0);
        assertEquals(0.5, PlaybackScales.pause(250), 0);
        assertEquals(1, PlaybackScales.pause(500), 0);
        assertEquals(30, PlaybackScales.pause(1000), 0);
        double previous = -1;
        for (int i = 0; i <= 1000; i++) {
            double value = PlaybackScales.pause(i); assertTrue(value >= previous); previous = value;
            if (i <= 500) { assertEquals(Math.round(value * 20), value * 20, 0.00001); }
        }
        for (int i = 0; i <= 20; i++) { roundTrip(i / 20.0); }
        for (int i = 2; i <= 20; i++) { roundTrip(i * 0.5); }
        for (int i = 11; i <= 30; i++) { roundTrip(i); }
    }
    private void roundTrip(double value) {
        assertEquals(value, PlaybackScales.pause(PlaybackScales.pauseProgress(value)), 0.00001);
    }
}
