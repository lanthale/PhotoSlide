package org.photoslide;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Central access to the user visible texts. The language is taken from the
 * system (JVM default locale); German texts live in messages_de.properties,
 * every other language falls back to the English base bundle.
 */
public final class I18n {

    private static final String BASE_NAME = "org.photoslide.i18n.messages";
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle(BASE_NAME, Locale.getDefault(),
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));

    private I18n() {
    }

    public static ResourceBundle bundle() {
        return BUNDLE;
    }

    public static Locale locale() {
        return Locale.getDefault();
    }

    /** Returns the text for the key, or the key itself if it is missing. */
    public static String t(String key) {
        try {
            return BUNDLE.getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
    }

    /** Returns the text for the key, or the given fallback if the key is missing. */
    public static String tOrDefault(String key, String fallback) {
        return BUNDLE.containsKey(key) ? BUNDLE.getString(key) : fallback;
    }

    /** Returns the text for the key formatted with {@link String#format} placeholders. */
    public static String t(String key, Object... args) {
        return String.format(Locale.getDefault(), t(key), args);
    }
}
