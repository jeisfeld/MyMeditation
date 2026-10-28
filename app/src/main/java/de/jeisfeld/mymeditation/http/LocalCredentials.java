package de.jeisfeld.mymeditation.http;

/** Optional Git-ignored credentials class; builds do not require real keys. */
final class LocalCredentials {
    private LocalCredentials() { }

    static String read(String field) throws ReflectiveOperationException {
        try {
            Object value = Class.forName("de.jeisfeld.mymeditation.http.HttpCredentials").getField(field).get(null);
            return value instanceof String ? ((String) value).trim() : "";
        } catch (ClassNotFoundException | NoSuchFieldException e) {
            return "";
        }
    }
}
