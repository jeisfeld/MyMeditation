package de.jeisfeld.mymeditation.ui.savedmeditations;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

/** Select visible IDs without changing the stored order or dropping hidden entries. */
final class SavedMeditationFilter {
    private SavedMeditationFilter() { }

    static List<Integer> select(List<Integer> ids, String query, boolean audioOnly,
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
