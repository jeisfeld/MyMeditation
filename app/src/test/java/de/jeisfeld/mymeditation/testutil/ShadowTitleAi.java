package de.jeisfeld.mymeditation.testutil;

import de.jeisfeld.mymeditation.http.AiClient;
import java.util.ArrayList;
import java.util.List;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;

/** Title prefetch tests never contact a provider, including when local credentials exist. */
@Implements(value = AiClient.class, isInAndroidSdk = false)
public class ShadowTitleAi {
    public static boolean deferred;
    public static final List<String> contents = new ArrayList<>();
    public static final List<AiClient.Callback> callbacks = new ArrayList<>();
    @Implementation public static void createTitle(String model, String content, AiClient.Callback callback) {
        contents.add(content); callbacks.add(callback);
        if (!deferred) { callback.complete(null, "Offline test"); }
    }
    @Resetter public static void reset() { deferred = false; contents.clear(); callbacks.clear(); }
}
