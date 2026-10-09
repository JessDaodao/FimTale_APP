package com.fimtale;

import com.fimtale.utils.MdiIcons;

import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.util.TypedValue;
import androidx.appcompat.app.AppCompatActivity;
import com.fimtale.ui.SettingsFragment;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;

public class SettingsActivity extends AppCompatActivity {

    private MaterialCardView toolbarContainer;
    private boolean isToolbarElevated = false;
    private ObjectAnimator elevationAnimator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.colorBackground, typedValue, true);
        getWindow().setStatusBarColor(typedValue.data);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbarContainer = findViewById(R.id.toolbarContainer);
        
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeAsUpIndicator(MdiIcons.drawable(this, "arrow-left"));
            getSupportActionBar().setTitle(getString(R.string.settings_title));
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings_container, new SettingsFragment())
                .commit();
    }

    public void onScroll(int scrollY) {
        boolean shouldElevate = scrollY > 0;
        if (shouldElevate != isToolbarElevated) {
            isToolbarElevated = shouldElevate;
            if (elevationAnimator != null && elevationAnimator.isRunning()) {
                elevationAnimator.cancel();
            }
            float targetElevation = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics());
            float start = toolbarContainer.getCardElevation();
            float end = shouldElevate ? targetElevation : 0;
            elevationAnimator = ObjectAnimator.ofFloat(toolbarContainer, "cardElevation", start, end);
            elevationAnimator.setDuration(200);
            elevationAnimator.start();
        }
    }
}
