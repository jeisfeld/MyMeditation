package de.jeisfeld.mymeditation.audio;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

/** Select visible IDs without changing the stored order or dropping hidden entries. */
public final class SavedMeditationFilter {
    private SavedMeditationFilter() { }

    /** Keep the topic before the first number or round parenthesis, including non-ASCII numbers. */
    public static String namePrefix(String name) {
        if (name == null) { return ""; }
        java.util.regex.Matcher suffix = java.util.regex.Pattern.compile("[\\p{N}()]").matcher(name);
        return (suffix.find() ? name.substring(0, suffix.start()) : name).trim();
    }

    public static List<Integer> select(List<Integer> ids, String query, boolean audioOnly,
                                IntFunction<String> name, IntPredicate hasAudio) {
        String search = query.trim().toLowerCase(Locale.ROOT);
        List<Integer> visible = new ArrayList<>();
        for (int id : ids) {
            String title = name.apply(id);
            if ((search.isEmpty() || title != null && title.toLowerCase(Locale.ROOT).contains(search))
                    && (!audioOnly || hasAudio.test(id))) {
                visible.add(id);
            }
        }
        return visible;
    }
}
