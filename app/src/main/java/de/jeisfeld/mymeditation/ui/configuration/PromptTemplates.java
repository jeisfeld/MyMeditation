package de.jeisfeld.mymeditation.ui.configuration;

import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Independent, named prompt libraries. Text is kept verbatim; IDs survive renaming and sorting. */
public final class PromptTemplates {
    public enum Kind {
        SYSTEM("system_prompt_templates"), QUERY("query_prompt_templates");
        public final String preference;
        Kind(String preference) { this.preference = preference; }
    }
    public enum Problem { NAME_REQUIRED, DUPLICATE_NAME, TEXT_REQUIRED, PLACEHOLDER_REQUIRED, MISSING }
    public static final class Invalid extends IllegalArgumentException {
        public final Problem problem;
        Invalid(Problem problem) { super(problem.name()); this.problem = problem; }
    }
    public static final class Entry {
        public final String id, name, text;
        Entry(String id, String name, String text) { this.id = id; this.name = name; this.text = text; }
    }
    private final Kind kind;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    public PromptTemplates(Kind kind, String json) {
        this.kind = kind;
        try {
            JSONObject root = new JSONObject(json);
            if (root.optInt("version") != 1) { return; }
            JSONArray items = root.getJSONArray("templates");
            for (int i = 0; i < items.length(); i++) {
                try {
                    JSONObject item = items.getJSONObject(i);
                    String id = item.getString("id");
                    if (id.isEmpty() || entries.containsKey(id)) { continue; }
                    String name = item.getString("name").trim(), text = item.getString("text");
                    validateName(null, name); validateText(kind, text);
                    entries.put(id, new Entry(id, name, text));
                } catch (JSONException | Invalid ignored) { /* Keep other valid entries. */ }
            }
        } catch (JSONException ignored) { }
    }
    public Entry get(String id) { return entries.get(id); }
    public List<Entry> list() {
        List<Entry> result = new ArrayList<>(entries.values());
        Collator alphabet = Collator.getInstance(); alphabet.setStrength(Collator.PRIMARY);
        result.sort((a, b) -> { int order = alphabet.compare(a.name, b.name); return order == 0 ? a.id.compareTo(b.id) : order; });
        return result;
    }
    public static void validateText(Kind kind, String text) {
        if (text == null || text.trim().isEmpty()) { throw new Invalid(Problem.TEXT_REQUIRED); }
        if (kind == Kind.QUERY && !text.contains("@TEXT@")) { throw new Invalid(Problem.PLACEHOLDER_REQUIRED); }
    }
    private void validateName(String id, String name) {
        if (name.isEmpty()) { throw new Invalid(Problem.NAME_REQUIRED); }
        for (Entry entry : entries.values()) {
            if (!entry.id.equals(id) && entry.name.equalsIgnoreCase(name)) { throw new Invalid(Problem.DUPLICATE_NAME); }
        }
    }
    public String save(String id, String name, String text) {
        if (id != null && !entries.containsKey(id)) { throw new Invalid(Problem.MISSING); }
        name = name == null ? "" : name.trim();
        validateName(id, name); validateText(kind, text);
        if (id == null) { id = UUID.randomUUID().toString(); }
        entries.put(id, new Entry(id, name, text));
        return id;
    }
    public void rename(String id, String name) {
        Entry entry = get(id);
        if (entry == null) { throw new Invalid(Problem.MISSING); }
        save(id, name, entry.text);
    }
    public void remove(String id) { entries.remove(id); }
    public String json() {
        try {
            JSONArray items = new JSONArray();
            for (Entry entry : entries.values()) {
                items.put(new JSONObject().put("id", entry.id).put("name", entry.name).put("text", entry.text));
            }
            return new JSONObject().put("version", 1).put("templates", items).toString();
        } catch (JSONException e) { throw new IllegalStateException(e); }
    }
}
