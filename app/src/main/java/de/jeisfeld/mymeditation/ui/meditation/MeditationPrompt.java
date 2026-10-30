package de.jeisfeld.mymeditation.ui.meditation;

/** The original template contract, independent of the provider used to generate the text. */
public final class MeditationPrompt {
    private MeditationPrompt() { }
    public static String render(String template, String content) {
        return (template == null ? "@TEXT@" : template).replace("@TEXT@", content);
    }
}
