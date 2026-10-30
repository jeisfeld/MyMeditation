package de.jeisfeld.mymeditation;

import android.os.Bundle;
import android.content.Context;
import android.content.Intent;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import de.jeisfeld.mymeditation.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private static final String OPEN_MEDITATION = "de.jeisfeld.mymeditation.OPEN_MEDITATION";
    private static final String OPEN_SAVED = "de.jeisfeld.mymeditation.OPEN_SAVED_MEDITATIONS";

    /** Distinct actions prevent live and offline PendingIntents from replacing each other's target. */
    public static Intent notificationIntent(Context context, int tab) {
        if (tab != R.id.navigation_meditation && tab != R.id.navigation_saved_meditations) {
            throw new IllegalArgumentException("Unsupported notification destination");
        }
        return new Intent(context, MainActivity.class)
                .setAction(tab == R.id.navigation_saved_meditations ? OPEN_SAVED : OPEN_MEDITATION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }

	private ActivityMainBinding binding;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		binding = ActivityMainBinding.inflate(getLayoutInflater());
		setContentView(binding.getRoot());

		BottomNavigationView navView = findViewById(R.id.nav_view);

		// Passing each menu ID as a set of Ids because each
		// menu should be considered as top level destinations.
		AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
				R.id.navigation_meditation, R.id.navigation_configuration, R.id.navigation_saved_meditations)
				.build();
		NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
		NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
		NavigationUI.setupWithNavController(binding.navView, navController);

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            binding.navView.setVisibility(insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())
                    ? android.view.View.GONE : android.view.View.VISIBLE);
            return insets;
        });

        // Recreation restores the tab chosen by the user after the original notification tap.
        if (savedInstanceState == null) { openNotificationTab(getIntent()); }
	}

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openNotificationTab(intent);
    }

    private void openNotificationTab(Intent intent) {
        if (intent == null) { return; }
        int tab;
        if (OPEN_MEDITATION.equals(intent.getAction())) { tab = R.id.navigation_meditation; }
        else if (OPEN_SAVED.equals(intent.getAction())) { tab = R.id.navigation_saved_meditations; }
        else { return; }
        if (binding.navView.getSelectedItemId() != tab) { binding.navView.setSelectedItemId(tab); }
    }
}
