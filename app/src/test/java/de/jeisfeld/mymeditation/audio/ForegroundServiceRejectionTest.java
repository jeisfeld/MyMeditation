package de.jeisfeld.mymeditation.audio;

import android.content.Intent;
import de.jeisfeld.mymeditation.Application;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.testutil.BaselineTest;
import org.junit.Test;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.shadows.ShadowService;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

public class ForegroundServiceRejectionTest extends BaselineTest {
    @Test public void playbackRejectedDuringCleanupStillRegistersAndStopsItsForegroundStart() {
        assertTrue(AppStorage.beginCleaning());
        ServiceController<MeditationPlaybackService> controller = null;
        try {
            // Use the same foreground-start entry point as the Meditation page.
            MeditationPlaybackService.play(Application.getAppContext(), 0);
            Intent intent = shadowOf((android.app.Application) Application.getAppContext()).getNextStartedService();
            controller = Robolectric.buildService(MeditationPlaybackService.class).create();
            controller.get().onStartCommand(intent, 0, 1);
            assertEquals(Application.getAppContext().getString(R.string.storage_cleaning), MeditationPlaybackService.state().getValue().error);
            assertSafelyStopped(shadowOf(controller.get()), 42);
        } finally {
            if (controller != null) { controller.destroy(); }
            AppStorage.endCleaning();
        }
    }
    @Test public void exportRejectedDuringCleanupStillRegistersAndStopsItsForegroundStart() {
        assertTrue(AppStorage.beginCleaning());
        ServiceController<MeditationExportService> controller = null;
        try {
            MeditationExportService.share(Application.getAppContext(), 1, 1, 1);
            Intent intent = shadowOf((android.app.Application) Application.getAppContext()).getNextStartedService();
            controller = Robolectric.buildService(MeditationExportService.class).create();
            controller.get().onStartCommand(intent, 0, 1);
            assertEquals(Application.getAppContext().getString(R.string.storage_cleaning), MeditationExportService.state().getValue().error);
            assertSafelyStopped(shadowOf(controller.get()), 43);
        } finally {
            if (controller != null) { controller.destroy(); }
            AppStorage.endCleaning();
        }
    }
    private void assertSafelyStopped(ShadowService lifecycle, int notificationId) {
        // Stopping removes the notification; Robolectric retains its ID as the registration record.
        assertEquals(notificationId, lifecycle.getLastForegroundNotificationId());
        assertTrue(lifecycle.isForegroundStopped());
        assertTrue(lifecycle.isStoppedBySelf());
        assertFalse(lifecycle.isLastForegroundNotificationAttached());
    }
}
