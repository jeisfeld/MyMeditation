package de.jeisfeld.mymeditation;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.os.SystemClock;
import androidx.navigation.Navigation;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Exercises actual PendingIntent delivery and activity reuse without starting audio or making API calls. */
@RunWith(AndroidJUnit4.class)
public class NotificationNavigationTest {
    private PendingIntent notification(int tab) {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        return PendingIntent.getActivity(context, 0, MainActivity.notificationIntent(context, tab),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void awaitTarget(MainActivity original, int tab) {
        long deadline = SystemClock.elapsedRealtime() + 10000;
        while (SystemClock.elapsedRealtime() < deadline) {
            AtomicBoolean arrived = new AtomicBoolean();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                Collection<Activity> resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED);
                for (Activity activity : resumed) {
                    if (!(activity instanceof MainActivity)) { continue; }
                    androidx.navigation.NavDestination destination = Navigation.findNavController(activity,
                            R.id.nav_host_fragment_activity_main).getCurrentDestination();
                    if (destination != null && destination.getId() == tab) {
                        assertSame("Notification created another app activity", original, activity);
                        arrived.set(true);
                    }
                }
            });
            if (arrived.get()) { return; }
            SystemClock.sleep(50);
        }
        fail("Notification did not return to the original activity and requested tab");
    }

    @Test public void liveAndOfflineNotificationsReuseActivityAndKeepIndependentTabs() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            AtomicReference<MainActivity> original = new AtomicReference<>();
            scenario.onActivity(activity -> {
                original.set(activity);
                ((BottomNavigationView) activity.findViewById(R.id.nav_view)).setSelectedItemId(R.id.navigation_configuration);
            });
            PendingIntent live = notification(R.id.navigation_meditation);
            PendingIntent offline = notification(R.id.navigation_saved_meditations);
            assertNotEquals("Different playback sources must not overwrite each other", live, offline);
            offline.send();
            awaitTarget(original.get(), R.id.navigation_saved_meditations);
            live.send();
            awaitTarget(original.get(), R.id.navigation_meditation);
            // A second tap on the same notification also retains the activity.
            live.send();
            awaitTarget(original.get(), R.id.navigation_meditation);
        }
    }

    @Test public void notificationBringsBackgroundActivityBackToTheSavedTab() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            AtomicReference<MainActivity> original = new AtomicReference<>();
            scenario.onActivity(original::set);
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);
            notification(R.id.navigation_saved_meditations).send();
            awaitTarget(original.get(), R.id.navigation_saved_meditations);
        }
    }

    @Test public void coldNotificationLaunchSelectsSavedTabAndRecreationKeepsLaterUserSelection() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(
                MainActivity.notificationIntent(context, R.id.navigation_saved_meditations))) {
            scenario.onActivity(activity -> {
                BottomNavigationView tabs = activity.findViewById(R.id.nav_view);
                assertEquals(R.id.navigation_saved_meditations, tabs.getSelectedItemId());
                tabs.setSelectedItemId(R.id.navigation_configuration);
            });
            scenario.recreate();
            scenario.onActivity(activity -> assertEquals(R.id.navigation_configuration,
                    ((BottomNavigationView) activity.findViewById(R.id.nav_view)).getSelectedItemId()));
        }
    }
}
