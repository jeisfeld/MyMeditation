package de.jeisfeld.mymeditation;

import android.annotation.SuppressLint;
import android.content.Context;

import de.jeisfeld.mymeditation.util.PreferenceUtil;


/**
 * Utility class to retrieve base application resources.
 */
public class Application extends android.app.Application {
	/**
	 * A utility field to store a context statically.
	 */
	@SuppressLint("StaticFieldLeak")
	private static Context mContext;
	/**
	 * The default tag for logging.
	 */
	public static final String TAG = "MyMeditation.JE";

	/**
	 * Retrieve the application context.
	 *
	 * @return The (statically stored) application context
	 */
	public static Context getAppContext() {
		return Application.mContext;
	}

	@Override
	public final void onCreate() {
		super.onCreate();
		Application.mContext = getApplicationContext();

		if (PreferenceUtil.getSharedPreferenceString(R.string.key_system_prompt) == null) {
			PreferenceUtil.setSharedPreferenceString(R.string.key_system_prompt, "Du bist eine strenge Beraterin, die sich im Stile einer Domina verhält. Dein Gegenüber ist Dein Diener. Du gibst Befehle und sprichst streng. Deine Antworten sollen immer in dieser dominanten Rolle bleiben. Du gehst auf die Fragen Deines Gegenübers ein, gibst ihm Aufträge, die Lust oder Schmerz bereiten, oder Aufgaben zur Selbstreflexion. Deine Aufgabe ist es, Dein Gegenüber in Deiner Rolle als seine Herrin lustvoll dabei zu unterstützen, sich weiterzuentwickeln und dabei die Lust an Schmerz und Demütigung zu nutzen.");
		}
		if (PreferenceUtil.getSharedPreferenceString(R.string.key_query_template) == null) {
			PreferenceUtil.setSharedPreferenceString(R.string.key_query_template, "Bitte schreibe einen Text im Stile einer gesprochenen Meditation, die Du sprichst, und die folgendem dient: @TEXT@. Die Meditation darf gerne lang sein - mindestens 50 Sätze, besser 100.");
		}
		if (PreferenceUtil.getSharedPreferenceString(R.string.key_meditation_content) == null) {
			PreferenceUtil.setSharedPreferenceString(R.string.key_meditation_content, "Ich versetze mich in eine Zukunft hinein, in der ich anderen Menschen diene und finde darin meine Erfüllung.");
		}
		if (PreferenceUtil.getSharedPreferenceInt(R.string.key_pause_duration, -1) == -1) {
			PreferenceUtil.setSharedPreferenceInt(R.string.key_pause_duration, 15);
		}
	}
}
