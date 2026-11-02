package de.jeisfeld.mymeditation.ui.configuration;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import androidx.appcompat.app.AlertDialog;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.databinding.DialogPromptTemplatesBinding;
import de.jeisfeld.mymeditation.util.PreferenceUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Compact inline selection; library edits happen in dialogs, independently of the active draft. */
final class InlinePromptTemplates {
    private final Context context;
    private final PromptTemplates.Kind kind;
    private final EditText editor;
    private final Spinner picker;
    private final View manage;
    private final Consumer<String> apply;
    private final List<AlertDialog> dialogs = new ArrayList<>();
    private List<PromptTemplates.Entry> entries = new ArrayList<>();
    private boolean disposed;
    private final TextWatcher changed = new TextWatcher() {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { selectMatching(null); }
        @Override public void afterTextChanged(Editable s) { }
    };
    InlinePromptTemplates(PromptTemplates.Kind kind, EditText editor, Spinner picker, View manage, Consumer<String> apply) {
        this.kind = kind; this.editor = editor; this.picker = picker; this.manage = manage; this.apply = apply;
        context = editor.getContext();
        refresh(null);
        picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(AdapterView<?> parent) { }
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (disposed || position <= 0 || position != picker.getSelectedItemPosition() || position > entries.size()) { return; }
                PromptTemplates.Entry entry = entries.get(position - 1);
                if (!entry.text.equals(editor.getText().toString())) { apply.accept(entry.text); }
            }
        });
        editor.addTextChangedListener(changed);
        manage.setOnClickListener(v -> new Manager());
    }
    private PromptTemplates read() { return new PromptTemplates(kind, PreferenceUtil.getSharedPreferenceString(kind.preference, "{}")); }
    private void write(PromptTemplates library) { PreferenceUtil.setSharedPreferenceString(kind.preference, library.json()); }
    private ArrayAdapter<String> adapter(List<PromptTemplates.Entry> items) {
        List<String> names = new ArrayList<>(); names.add(context.getString(R.string.prompt_templates_hint));
        for (PromptTemplates.Entry entry : items) { names.add(entry.name); }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, R.layout.spinner_settings_item, names);
        adapter.setDropDownViewResource(R.layout.dropdown_voice_item); return adapter;
    }
    private void refresh(String preferred) {
        if (disposed) { return; }
        entries = read().list(); picker.setAdapter(adapter(entries)); picker.setEnabled(!entries.isEmpty()); selectMatching(preferred);
    }
    private void selectMatching(String preferred) {
        if (disposed) { return; }
        String text = editor.getText().toString();
        int current = picker.getSelectedItemPosition(), match = 0;
        for (int i = 0; i < entries.size(); i++) {
            PromptTemplates.Entry entry = entries.get(i);
            if (entry.text.equals(text)) {
                if (match == 0) { match = i + 1; }
                if (entry.id.equals(preferred) || preferred == null && current == i + 1) { match = i + 1; break; }
            }
        }
        picker.setSelection(match);
    }
    private int error(PromptTemplates.Invalid invalid) {
        switch (invalid.problem) {
            case NAME_REQUIRED: return R.string.saved_meditation_name_required;
            case DUPLICATE_NAME: return R.string.prompt_template_duplicate_name;
            case TEXT_REQUIRED: return R.string.prompt_template_text_required;
            case PLACEHOLDER_REQUIRED: return R.string.prompt_template_placeholder_required;
            default: return R.string.prompt_template_missing;
        }
    }
    private void show(AlertDialog dialog) {
        if (disposed) { return; }
        dialogs.add(dialog); dialog.setOnDismissListener(d -> dialogs.remove(dialog)); dialog.show();
    }
    private void nameDialog(int title, String initial, Consumer<String> saved) {
        EditText name = new EditText(context);
        name.setSingleLine(true); name.setHint(R.string.prompt_template_name); name.setText(initial); name.selectAll();
        name.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        LinearLayout container = new LinearLayout(context);
        int padding = context.getResources().getDimensionPixelSize(R.dimen.activity_horizontal_margin);
        container.setPadding(padding, 0, padding, 0);
        container.addView(name, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog = new AlertDialog.Builder(context).setTitle(title).setView(container)
                .setNegativeButton(R.string.button_cancel, null).setPositiveButton(R.string.button_save, null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (disposed) { return; }
            try { saved.accept(name.getText().toString()); dialog.dismiss(); }
            catch (PromptTemplates.Invalid invalid) { name.setError(context.getString(error(invalid))); }
        }));
        show(dialog);
    }
    private final class Manager {
        private final DialogPromptTemplatesBinding view = DialogPromptTemplatesBinding.inflate(android.view.LayoutInflater.from(context));
        private final String currentText = editor.getText().toString();
        private List<PromptTemplates.Entry> items;
        private String selected;
        Manager() {
            int position = picker.getSelectedItemPosition();
            if (position > 0 && position <= entries.size()) { selected = entries.get(position - 1).id; }
            view.spinnerManagedPromptTemplate.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onNothingSelected(AdapterView<?> parent) { }
                @Override public void onItemSelected(AdapterView<?> parent, View child, int position, long id) {
                    int actual = view.spinnerManagedPromptTemplate.getSelectedItemPosition();
                    selected = actual > 0 && actual <= items.size() ? items.get(actual - 1).id : null;
                    buttons();
                }
            });
            reload(selected);
            view.buttonSavePromptTemplate.setOnClickListener(v -> {
                try { PromptTemplates.validateText(kind, currentText); }
                catch (PromptTemplates.Invalid invalid) {
                    show(new AlertDialog.Builder(context).setMessage(error(invalid)).setPositiveButton(R.string.button_ok, null).create()); return;
                }
                nameDialog(R.string.prompt_template_save_current, "", name -> {
                    PromptTemplates library = read(); String id = library.save(null, name, currentText); write(library); reload(id); refresh(id);
                });
            });
            view.buttonRenamePromptTemplate.setOnClickListener(v -> {
                PromptTemplates.Entry entry = read().get(selected); if (entry == null) { return; }
                nameDialog(R.string.prompt_template_rename, entry.name, name -> {
                    PromptTemplates library = read(); library.rename(entry.id, name); write(library); reload(entry.id); refresh(entry.id);
                });
            });
            view.buttonDeletePromptTemplate.setOnClickListener(v -> {
                PromptTemplates.Entry entry = read().get(selected); if (entry == null) { return; }
                show(new AlertDialog.Builder(context).setTitle(R.string.button_delete)
                        .setMessage(context.getString(R.string.prompt_template_delete_confirmation, entry.name))
                        .setNegativeButton(R.string.button_cancel, null).setPositiveButton(R.string.button_delete, (d, which) -> {
                            if (disposed) { return; }
                            PromptTemplates library = read(); library.remove(entry.id); write(library); reload(null); refresh(null);
                        }).create());
            });
            view.buttonUpdatePromptTemplate.setOnClickListener(v -> {
                PromptTemplates.Entry entry = read().get(selected); if (entry == null) { return; }
                show(new AlertDialog.Builder(context).setTitle(R.string.prompt_template_update)
                        .setMessage(context.getString(R.string.prompt_template_update_confirmation, entry.name))
                        .setNegativeButton(R.string.button_cancel, null).setPositiveButton(R.string.button_save, (d, which) -> {
                            if (disposed) { return; }
                            try {
                                PromptTemplates library = read(); library.save(entry.id, entry.name, currentText); write(library); reload(entry.id); refresh(entry.id);
                            } catch (PromptTemplates.Invalid invalid) {
                                show(new AlertDialog.Builder(context).setMessage(error(invalid)).setPositiveButton(R.string.button_ok, null).create());
                            }
                        }).create());
            });
            show(new AlertDialog.Builder(context).setTitle(kind == PromptTemplates.Kind.SYSTEM ? R.string.system_prompt_templates : R.string.query_prompt_templates)
                    .setView(view.getRoot()).setPositiveButton(R.string.button_close, null).create());
        }
        private void reload(String id) {
            items = read().list(); selected = null;
            int position = 0;
            for (int i = 0; i < items.size(); i++) { if (items.get(i).id.equals(id)) { position = i + 1; selected = id; break; } }
            view.spinnerManagedPromptTemplate.setAdapter(adapter(items)); view.spinnerManagedPromptTemplate.setSelection(position); buttons();
        }
        private void buttons() {
            PromptTemplates.Entry entry = read().get(selected);
            view.buttonRenamePromptTemplate.setEnabled(entry != null); view.buttonDeletePromptTemplate.setEnabled(entry != null);
            view.buttonUpdatePromptTemplate.setEnabled(entry != null && !currentText.equals(entry.text));
        }
    }
    void dispose() {
        disposed = true; editor.removeTextChangedListener(changed); picker.setOnItemSelectedListener(null); manage.setOnClickListener(null);
        for (AlertDialog dialog : new ArrayList<>(dialogs)) { dialog.dismiss(); }
    }
}
