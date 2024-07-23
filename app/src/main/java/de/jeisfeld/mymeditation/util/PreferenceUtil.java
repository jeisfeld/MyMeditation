package de.jeisfeld.mymeditation.util;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import androidx.preference.PreferenceManager;
import de.jeisfeld.mymeditation.Application;

/**
 * Utility class for handling the shared preferences.
 */
public final class PreferenceUtil {

	/**
	 * Hide default constructor.
	 */
	private PreferenceUtil() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Retrieve the default shared preferences of the application.
	 *
	 * @return the default shared preferences.
	 */
	private static SharedPreferences getSharedPreferences() {
		return PreferenceManager.getDefaultSharedPreferences(Application.getAppContext());
	}

	/**
	 * Retrieve a String shared preference.
	 *
	 * @param preferenceId the id of the shared preference.
	 * @return the corresponding preference value.
	 */
	public static String getSharedPreferenceString(final int preferenceId) {
		return PreferenceUtil.getSharedPreferences().getString(Application.getAppContext().getString(preferenceId), null);
	}

	/**
	 * Retrieve a String shared preference, setting a default value if the preference is not set.
	 *
	 * @param preferenceId the id of the shared preference.
	 * @param defaultId the String key of the default value.
	 * @return the corresponding preference value.
	 */
	public static String getSharedPreferenceString(final int preferenceId, final int defaultId) {
		String result = PreferenceUtil.getSharedPreferenceString(preferenceId);
		if (result == null) {
			result = Application.getAppContext().getString(defaultId);
			PreferenceUtil.setSharedPreferenceString(preferenceId, result);
		}
		return result;
	}

	/**
	 * Set a String shared preference.
	 *
	 * @param preferenceId the id of the shared preference.
	 * @param s the target value of the preference.
	 */
	public static void setSharedPreferenceString(final int preferenceId, final String s) {
		Editor editor = PreferenceUtil.getSharedPreferences().edit();
		editor.putString(Application.getAppContext().getString(preferenceId), s);
		editor.apply();
	}

	/**
	 * Retrieve a boolean shared preference.
	 *
	 * @param preferenceId the id of the shared preference.
	 * @return the corresponding preference value.
	 */
	public static boolean getSharedPreferenceBoolean(final int preferenceId) {
		return getSharedPreferenceBoolean(preferenceId, false);
	}

	/**
	 * Retrieve a boolean shared preference.
	 *
	 * @param preferenceId the id of the shared preference.
	 * @param defaultValue the default value
	 * @return the corresponding preference value.
	 */
	public static boolean getSharedPreferenceBoolean(final int preferenceId, final boolean defaultValue) {
		return PreferenceUtil.getSharedPreferences().getBoolean(Application.getAppContext().getString(preferenceId), defaultValue);
	}

	/**
	 * Set a Boolean shared preference.
	 *
	 * @param preferenceId the id of the shared preference.
	 * @param b the target value of the preference.
	 */
	public static void setSharedPreferenceBoolean(final int preferenceId, final boolean b) {
		Editor editor = PreferenceUtil.getSharedPreferences().edit();
		editor.putBoolean(Application.getAppContext().getString(preferenceId), b);
		editor.apply();
	}

	/**
	 * Retrieve an integer shared preference.
	 *
	 * @param preferenceId the id of the shared preference.
	 * @param defaultValue the default value of the shared preference.
	 * @return the corresponding preference value.
	 */
	public static int getSharedPreferenceInt(final int preferenceId, final int defaultValue) {
		return PreferenceUtil.getSharedPreferences().getInt(Application.getAppContext().getString(preferenceId), defaultValue);
	}

	/**
	 * Set an integer shared preference.
	 *
	 * @param preferenceId the id of the shared preference.
	 * @param i the target value of the preference.
	 */
	public static void setSharedPreferenceInt(final int preferenceId, final int i) {
		Editor editor = PreferenceUtil.getSharedPreferences().edit();
		editor.putInt(Application.getAppContext().getString(preferenceId), i);
		editor.apply();
	}

	/**
	 * Increment a counter shared preference, and return the new value.
	 *
	 * @param preferenceId the id of the shared preference.
	 * @return the new value.
	 */
	public static int incrementCounter(final int preferenceId) {
		int newValue = PreferenceUtil.getSharedPreferenceInt(preferenceId, 0) + 1;
		PreferenceUtil.setSharedPreferenceInt(preferenceId, newValue);
		return newValue;
	}

	/**
	 * Retrieve an integer from a shared preference string.
	 *
	 * @param preferenceId the id of the shared preference.
	 * @param defaultId the String key of the default value. If not existing, value -1 is returned.
	 * @return the corresponding preference value.
	 */
	public static int getSharedPreferenceIntString(final int preferenceId, final Integer defaultId) {
		String resultString;

		if (defaultId == null) {
			resultString = PreferenceUtil.getSharedPreferenceString(preferenceId);
		}
		else {
			resultString = PreferenceUtil.getSharedPreferenceString(preferenceId, defaultId);
		}
		if (resultString == null || resultString.length() == 0) {
			return -1;
		}
		try {
			return Integer.parseInt(resultString);
		}
		catch (NumberFormatException e) {
			return -1;
		}
	}

	/**
	 * Set a string shared preference from an integer.
	 *
	 * @param preferenceId the id of the shared preference.
	 * @param i the target value of the preference.
	 */
	public static void setSharedPreferenceIntString(final int preferenceId, final int i) {
		PreferenceUtil.setSharedPreferenceString(preferenceId, Integer.toString(i));
	}

	/**
	 * Retrieve a long shared preference.
	 *
	 * @param preferenceId the id of the shared preference.
	 * @param defaultValue the default value of the shared preference.
	 * @return the corresponding preference value.
	 */
	public static long getSharedPreferenceLong(final int preferenceId, final long defaultValue) {
		return PreferenceUtil.getSharedPreferences().getLong(Application.getAppContext().getString(preferenceId), defaultValue);
	}

	/**
	 * Set a long shared preference.
	 *
	 * @param preferenceId the id of the shared preference.
	 * @param i the target value of the preference.
	 */
	public static void setSharedPreferenceLong(final int preferenceId, final long i) {
		Editor editor = PreferenceUtil.getSharedPreferences().edit();
		editor.putLong(Application.getAppContext().getString(preferenceId), i);
		editor.apply();
	}

	/**
	 * Retrieve a long from a shared preference string.
	 *
	 * @param preferenceId the id of the shared preference.
	 * @param defaultId the String key of the default value. If not existing, value -1 is returned.
	 * @return the corresponding preference value.
	 */
	public static long getSharedPreferenceLongString(final int preferenceId, final Integer defaultId) {
		String resultString;

		if (defaultId == null) {
			resultString = PreferenceUtil.getSharedPreferenceString(preferenceId);
		}
		else {
			resultString = PreferenceUtil.getSharedPreferenceString(preferenceId, defaultId);
		}
		if (resultString == null || resultString.length() == 0) {
			return -1;
		}
		try {
			return Long.parseLong(resultString);
		}
		catch (NumberFormatException e) {
			return -1;
		}
	}

	/**
	 * Set a string shared preference from a long.
	 *
	 * @param preferenceId the id of the shared preference.
	 * @param i the target value of the preference.
	 */
	public static void setSharedPreferenceLongString(final int preferenceId, final long i) {
		PreferenceUtil.setSharedPreferenceString(preferenceId, Long.toString(i));
	}

	/**
	 * Retrieve a double shared preference.
	 *
	 * @param preferenceId the id of the shared preference.
	 * @param defaultValue the default value of the shared preference.
	 * @return the corresponding preference value.
	 */
	public static double getSharedPreferenceDouble(final int preferenceId, final double defaultValue) {
		return Double.longBitsToDouble(getSharedPreferenceLong(preferenceId, Double.doubleToLongBits(defaultValue)));
	}

	/**
	 * Set a double shared preference.
	 *
	 * @param preferenceId the id of the shared preference.
	 * @param d the target value of the preference.
	 */
	public static void setSharedPreferenceDouble(final int preferenceId, final double d) {
		setSharedPreferenceLong(preferenceId, Double.doubleToLongBits(d));
	}

	/**
	 * Retrieve a String List shared preference.
	 *
	 * @param preferenceId the id of the shared preference.
	 * @return the corresponding preference value.
	 */
	public static ArrayList<String> getSharedPreferenceStringList(final int preferenceId) {
		String restoreString = PreferenceUtil.getSharedPreferenceString(preferenceId);
		if (restoreString == null || restoreString.length() == 0) {
			return new ArrayList<>();
		}

		String[] folderArray = restoreString.split("\\r?\\n");
		return new ArrayList<>(Arrays.asList(folderArray));
	}

	/**
	 * Set a String List shared preference.
	 *
	 * @param preferenceId the id of the shared preference.
	 * @param stringList the target value of the preference.
	 */
	public static void setSharedPreferenceStringList(final int preferenceId, final List<String> stringList) {
		if (stringList == null || stringList.size() == 0) {
			PreferenceUtil.removeSharedPreference(preferenceId);
		}
		else {
			StringBuilder saveStringBuffer = new StringBuilder();
			for (String string : stringList) {
				if (saveStringBuffer.length() > 0) {
					saveStringBuffer.append("\n");
				}
				saveStringBuffer.append(string);
			}
			PreferenceUtil.setSharedPreferenceString(preferenceId, saveStringBuffer.toString());
		}
	}

	/**
	 * Retrieve an Integer List shared preference.
	 *
	 * @param preferenceId the id of the shared preference.
	 * @return the corresponding preference value.
	 */
	public static ArrayList<Integer> getSharedPreferenceIntList(final int preferenceId) {
		List<String> stringList = PreferenceUtil.getSharedPreferenceStringList(preferenceId);
		ArrayList<Integer> intList = new ArrayList<>();
		for (String intString : stringList) {
			intList.add(Integer.valueOf(intString));
		}
		return intList;
	}

	/**
	 * Set an Integer List shared preference.
	 *
	 * @param preferenceId the id of the shared preference.
	 * @param intList the target value of the preference.
	 */
	public static void setSharedPreferenceIntList(final int preferenceId, final List<Integer> intList) {
		List<String> stringList = new ArrayList<>();
		for (int id : intList) {
			stringList.add(Integer.toString(id));
		}
		PreferenceUtil.setSharedPreferenceStringList(preferenceId, stringList);
	}

	/**
	 * Retrieve a Long List shared preference.
	 *
	 * @param preferenceId the id of the shared preference.
	 * @return the corresponding preference value.
	 */
	public static ArrayList<Long> getSharedPreferenceLongList(final int preferenceId) {
		List<String> stringList = PreferenceUtil.getSharedPreferenceStringList(preferenceId);
		ArrayList<Long> longList = new ArrayList<>();
		for (String longString : stringList) {
			longList.add(Long.valueOf(longString));
		}
		return longList;
	}

	/**
	 * Set a Long List shared preference.
	 *
	 * @param preferenceId the id of the shared preference.
	 * @param longList the target value of the preference.
	 */
	public static void setSharedPreferenceLongList(final int preferenceId, final List<Long> longList) {
		List<String> stringList = new ArrayList<>();
		for (long longValue : longList) {
			stringList.add(Long.toString(longValue));
		}
		PreferenceUtil.setSharedPreferenceStringList(preferenceId, stringList);
	}

	/**
	 * Remove a shared preference.
	 *
	 * @param preferenceId the id of the shared preference.
	 */
	public static void removeSharedPreference(final int preferenceId) {
		Editor editor = PreferenceUtil.getSharedPreferences().edit();
		editor.remove(Application.getAppContext().getString(preferenceId));
		editor.apply();
	}

	/**
	 * Get an indexed preference key that allows to store a shared preference with index.
	 *
	 * @param preferenceId The base preference id
	 * @param index The index
	 * @return The indexed preference key.
	 */
	private static String getIndexedPreferenceKey(final int preferenceId, final Object index) {
		return Application.getAppContext().getString(preferenceId) + "[" + index + "]";
	}

	/**
	 * Retrieve an indexed String shared preference.
	 *
	 * @param preferenceId the id of the shared preference.
	 * @param index The index
	 * @return the corresponding preference value.
	 */
	public static String getIndexedSharedPreferenceString(final int preferenceId, final Object index) {
		return PreferenceUtil.getSharedPreferences().getString(PreferenceUtil.getIndexedPreferenceKey(preferenceId, index), null);
	}

	/**
	 * Set an indexed String shared preference.
	 *
	 * @param preferenceId the id of the shared preference.
	 * @param index The index
	 * @param s the target value of the preference.
	 */
	public static void setIndexedSharedPreferenceString(final int preferenceId, final Object index, final String s) {
		Editor editor = PreferenceUtil.getSharedPreferences().edit();
		editor.putString(PreferenceUtil.getIndexedPreferenceKey(preferenceId, index), s);
		editor.apply();
	}

	/**
	 * Retrieve an indexed boolean shared preference.
	 *
	 * @param preferenceId the id of the shared preference.
	 * @param index The index
	 * @param defaultValue the default value of the shared preference.
	 * @return the corresponding preference value.
	 */
	public static boolean getIndexedSharedPreferenceBoolean(final int preferenceId, final Object index, final boolean defaultValue) {
		return PreferenceUtil.getSharedPreferences().getBoolean(PreferenceUtil.getIndexedPreferenceKey(preferenceId, index), defaultValue);
	}

	/**
	 * Set an indexed boolean shared preference.
	 *
	 * @param preferenceId the id of the shared preference.
	 * @param index The index
	 * @param b the target value of the preference.
	 */
	public static void setIndexedSharedPreferenceBoolean(final int preferenceId, final Object index, final boolean b) {
		Editor editor = PreferenceUtil.getSharedPreferences().edit();
		editor.putBoolean(PreferenceUtil.getIndexedPreferenceKey(preferenceId, index), b);
		editor.apply();
	}

	/**
	 * Retrieve an indexed int shared preference.
	 *
	 * @param preferenceId the id of the shared preference.
	 * @param index The index
	 * @param defaultValue the default value of the shared preference.
	 * @return the corresponding preference value.
	 */
	public static int getIndexedSharedPreferenceInt(final int preferenceId, final Object index, final int defaultValue) {
		return PreferenceUtil.getSharedPreferences().getInt(PreferenceUtil.getIndexedPreferenceKey(preferenceId, index), defaultValue);
	}

	/**
	 * Set an indexed int shared preference.
	 *
	 * @param preferenceId the id of the shared preference.
	 * @param index The index
	 * @param i the target value of the preference.
	 */
	public static void setIndexedSharedPreferenceInt(final int preferenceId, final Object index, final int i) {
		Editor editor = PreferenceUtil.getSharedPreferences().edit();
		editor.putInt(PreferenceUtil.getIndexedPreferenceKey(preferenceId, index), i);
		editor.apply();
	}

	/**
	 * Retrieve an indexed long shared preference.
	 *
	 * @param preferenceId the id of the shared preference.
	 * @param index The index
	 * @param defaultValue the default value of the shared preference.
	 * @return the corresponding preference value.
	 */
	public static long getIndexedSharedPreferenceLong(final int preferenceId, final Object index, final long defaultValue) {
		return PreferenceUtil.getSharedPreferences().getLong(PreferenceUtil.getIndexedPreferenceKey(preferenceId, index), defaultValue);
	}

	/**
	 * Set an indexed long shared preference.
	 *
	 * @param preferenceId the id of the shared preference.
	 * @param index The index
	 * @param i the target value of the preference.
	 */
	public static void setIndexedSharedPreferenceLong(final int preferenceId, final Object index, final long i) {
		Editor editor = PreferenceUtil.getSharedPreferences().edit();
		editor.putLong(PreferenceUtil.getIndexedPreferenceKey(preferenceId, index), i);
		editor.apply();
	}

	/**
	 * Retrieve an indexed double shared preference.
	 *
	 * @param preferenceId the id of the shared preference.
	 * @param index The index
	 * @param defaultValue the default value of the shared preference.
	 * @return the corresponding preference value.
	 */
	public static double getIndexedSharedPreferenceDouble(final int preferenceId, final Object index, final double defaultValue) {
		return Double.longBitsToDouble(PreferenceUtil.getIndexedSharedPreferenceLong(preferenceId, index, Double.doubleToLongBits(defaultValue)));
	}

	/**
	 * Set an indexed double shared preference.
	 *
	 * @param preferenceId the id of the shared preference.
	 * @param index The index
	 * @param d the target value of the preference.
	 */
	public static void setIndexedSharedPreferenceDouble(final int preferenceId, final Object index, final double d) {
		PreferenceUtil.setIndexedSharedPreferenceLong(preferenceId, index, Double.doubleToLongBits(d));
	}

	/**
	 * Retrieve a String List indexed shared preference.
	 *
	 * @param preferenceId the id of the shared preference.
	 * @param index The index
	 * @return the corresponding preference value.
	 */
	public static ArrayList<String> getIndexedSharedPreferenceStringList(final int preferenceId, final Object index) {
		String restoreString = PreferenceUtil.getIndexedSharedPreferenceString(preferenceId, index);
		if (restoreString == null || restoreString.length() == 0) {
			return new ArrayList<>();
		}

		String[] folderArray = restoreString.split("\\r?\\n");
		return new ArrayList<>(Arrays.asList(folderArray));
	}

	/**
	 * Set a String List indexed shared preference.
	 *
	 * @param preferenceId the id of the shared preference.
	 * @param index The index
	 * @param stringList the target value of the preference.
	 */
	public static void setIndexedSharedPreferenceStringList(final int preferenceId, final Object index, final List<String> stringList) {
		if (stringList == null || stringList.size() == 0) {
			PreferenceUtil.removeIndexedSharedPreference(preferenceId, index);
		}
		else {
			StringBuilder saveStringBuffer = new StringBuilder();
			for (String string : stringList) {
				if (saveStringBuffer.length() > 0) {
					saveStringBuffer.append("\n");
				}
				saveStringBuffer.append(string);
			}
			PreferenceUtil.setIndexedSharedPreferenceString(preferenceId, index, saveStringBuffer.toString());
		}
	}

	/**
	 * Retrieve an Integer List indexed shared preference.
	 *
	 * @param preferenceId the id of the shared preference.
	 * @param index The index
	 * @return the corresponding preference value.
	 */
	public static ArrayList<Integer> getIndexedSharedPreferenceIntList(final int preferenceId, final Object index) {
		List<String> stringList = PreferenceUtil.getIndexedSharedPreferenceStringList(preferenceId, index);
		ArrayList<Integer> intList = new ArrayList<>();
		for (String intString : stringList) {
			intList.add(Integer.valueOf(intString));
		}
		return intList;
	}

	/**
	 * Set an Integer List indexed shared preference.
	 *
	 * @param preferenceId the id of the shared preference.
	 * @param index The index
	 * @param intList the target value of the preference.
	 */
	public static void setIndexedSharedPreferenceIntList(final int preferenceId, final Object index, final List<Integer> intList) {
		List<String> stringList = new ArrayList<>();
		for (int intValue : intList) {
			stringList.add(Integer.toString(intValue));
		}
		PreferenceUtil.setIndexedSharedPreferenceStringList(preferenceId, index, stringList);
	}

	/**
	 * Retrieve a Long List indexed shared preference.
	 *
	 * @param preferenceId the id of the shared preference.
	 * @param index The index
	 * @return the corresponding preference value.
	 */
	public static ArrayList<Long> getIndexedSharedPreferenceLongList(final int preferenceId, final Object index) {
		List<String> stringList = PreferenceUtil.getIndexedSharedPreferenceStringList(preferenceId, index);
		ArrayList<Long> longList = new ArrayList<>();
		for (String longString : stringList) {
			longList.add(Long.valueOf(longString));
		}
		return longList;
	}

	/**
	 * Set a Long List indexed shared preference.
	 *
	 * @param preferenceId the id of the shared preference.
	 * @param index The index
	 * @param longList the target value of the preference.
	 */
	public static void setIndexedSharedPreferenceLongList(final int preferenceId, final Object index, final List<Long> longList) {
		List<String> stringList = new ArrayList<>();
		for (long longValue : longList) {
			stringList.add(Long.toString(longValue));
		}
		PreferenceUtil.setIndexedSharedPreferenceStringList(preferenceId, index, stringList);
	}

	/**
	 * Remove an indexed shared preference.
	 *
	 * @param preferenceId the id of the shared preference.
	 * @param index The index
	 */
	public static void removeIndexedSharedPreference(final int preferenceId, final Object index) {
		Editor editor = PreferenceUtil.getSharedPreferences().edit();
		editor.remove(PreferenceUtil.getIndexedPreferenceKey(preferenceId, index));
		editor.apply();
	}

	/**
	 * Check the existence of an indexed shared preference.
	 *
	 * @param preferenceId the id of the shared preference.
	 * @param index The index
	 * @return True if the preference exists.
	 */
	public static boolean hasIndexedSharedPreference(final int preferenceId, final Object index) {
		return PreferenceUtil.getSharedPreferences().contains(PreferenceUtil.getIndexedPreferenceKey(preferenceId, index));
	}

	/**
	 * Get a String containing representation of all shared preferences.
	 *
	 * @return A String containing representation of all shared preferences.
	 */
	public static byte[] getPreferencesExportByteArray() throws IOException {
		Map<String, ?> preferenceMap = getSharedPreferences().getAll();
		ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
		ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
		objectOutputStream.writeObject(preferenceMap);
		objectOutputStream.close();
		byteOutputStream.close();
		return byteOutputStream.toByteArray();
	}

	/**
	 * Import preferences from a byte array.
	 *
	 * @param byteArray The byte array.
	 */
	public static void importFromByteArray(final byte[] byteArray) throws IOException, ClassNotFoundException {
		ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(byteArray);
		ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);

		@SuppressWarnings("unchecked")
		Map<String, ?> preferences = (Map<String, ?>) objectInputStream.readObject();

		Editor editor = PreferenceUtil.getSharedPreferences().edit();
		for (String key : getSharedPreferences().getAll().keySet()) {
			editor.remove(key);
		}
		for (Entry<String, ?> entry : preferences.entrySet()) {
			if (entry.getValue() instanceof Boolean) {
				editor.putBoolean(entry.getKey(), (Boolean) entry.getValue());
			}
			else if (entry.getValue() instanceof Integer) {
				editor.putInt(entry.getKey(), (Integer) entry.getValue());
			}
			else if (entry.getValue() instanceof Long) {
				editor.putLong(entry.getKey(), (Long) entry.getValue());
			}
			else if (entry.getValue() instanceof Float) {
				editor.putFloat(entry.getKey(), (Float) entry.getValue());
			}
			else if (entry.getValue() instanceof String) {
				editor.putString(entry.getKey(), (String) entry.getValue());
			}
		}
		editor.apply();
	}
}
