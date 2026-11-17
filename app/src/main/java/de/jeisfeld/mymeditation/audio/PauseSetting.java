package de.jeisfeld.mymeditation.audio;

import java.text.NumberFormat;
import java.util.Locale;

/** Values below one are a fraction of waits plus a one-second base gap; larger values are gaps. */
public final class PauseSetting {
    private PauseSetting() { }
    public static double parse(String text) {
        text = text.trim();
        boolean percentage = text.endsWith("%");
        if (percentage) { text = text.substring(0, text.length() - 1).trim(); }
        double value = Double.parseDouble(text.replace(',', '.'));
        if (!Double.isFinite(value) || value < 0 || value > (percentage ? 100 : 30)) {
            throw new IllegalArgumentException("Invalid pause setting");
        }
        return percentage ? value / 100 : value;
    }
    public static String input(double value, Locale locale) {
        NumberFormat format = NumberFormat.getNumberInstance(locale);
        format.setGroupingUsed(false); format.setMaximumFractionDigits(3);
        return value < 1 ? format.format(value * 100) + "%" : format.format(value);
    }
    public static String display(double value, Locale locale) {
        return input(value, locale) + (value < 1 ? "" : " s");
    }
}
