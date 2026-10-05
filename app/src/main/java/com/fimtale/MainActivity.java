package com.fimtale;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import com.fimtale.ui.ArticleFragment;
import com.fimtale.ui.HomeFragment;
import com.fimtale.ui.ProfileFragment;
import com.fimtale.utils.MdiIcons;
import com.fimtale.utils.UpdateChecker;
import com.fimtale.utils.UserPreferences;
import android.widget.ImageView;
import android.widget.TextView;
import android.graphics.Rect;
import android.view.View;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private int currentItemId = 0;
    private HomeFragment homeFragment;
    private ArticleFragment articleFragment;
    private ProfileFragment profileFragment;
    private Fragment currentFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.getMenu().findItem(R.id.nav_article).setIcon(
                MdiIcons.drawable(this, CommunityMaterial.Icon.cmd_book_open_page_variant));
        bottomNav.getMenu().findItem(R.id.nav_profile).setIcon(
                MdiIcons.drawable(this, CommunityMaterial.Icon.cmd_account));

        final View rootView = findViewById(android.R.id.content);
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect r = new Rect();
            rootView.getWindowVisibleDisplayFrame(r);
            int screenHeight = rootView.getRootView().getHeight();
            int keypadHeight = screenHeight - r.bottom;

            if (keypadHeight > screenHeight * 0.15) {
                if (currentItemId == R.id.nav_article) {
                    bottomNav.setVisibility(View.GONE);
                }
            } else {
                bottomNav.setVisibility(View.VISIBLE);
            }
        });

        if (savedInstanceState != null) {
            currentItemId = savedInstanceState.getInt("currentItemId", R.id.nav_home);
            homeFragment = (HomeFragment) getSupportFragmentManager().findFragmentByTag("HOME");
            articleFragment = (ArticleFragment) getSupportFragmentManager().findFragmentByTag("ARTICLE");
            profileFragment = (ProfileFragment) getSupportFragmentManager().findFragmentByTag("PROFILE");

            if (currentItemId == R.id.nav_home) currentFragment = homeFragment;
            else if (currentItemId == R.id.nav_article) currentFragment = articleFragment;
            else if (currentItemId == R.id.nav_profile) currentFragment = profileFragment;
        }

        updateToolbar(currentItemId);

        bottomNav.setOnItemSelectedListener(item -> {
            int newItemId = item.getItemId();
            if (currentItemId == newItemId && currentFragment != null) {
                return true;
            }

            Fragment targetFragment = null;
            String tag = "";

            updateToolbar(newItemId);

            if (newItemId == R.id.nav_home) {
                if (homeFragment == null) homeFragment = new HomeFragment();
                targetFragment = homeFragment;
                tag = "HOME";
            } else if (newItemId == R.id.nav_article) {
                if (articleFragment == null) articleFragment = new ArticleFragment();
                targetFragment = articleFragment;
                tag = "ARTICLE";
            } else if (newItemId == R.id.nav_profile) {
                if (profileFragment == null) profileFragment = new ProfileFragment();
                targetFragment = profileFragment;
                tag = "PROFILE";
            }

            if (targetFragment != null) {
                androidx.fragment.app.FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

                Map<Integer, Integer> menuOrder = new HashMap<>();
                menuOrder.put(R.id.nav_home, 0);
                menuOrder.put(R.id.nav_article, 1);
                menuOrder.put(R.id.nav_profile, 2);

                Integer currentOrder = menuOrder.get(currentItemId);
                Integer newOrder = menuOrder.get(newItemId);

                if (currentOrder != null && newOrder != null) {
                    if (newOrder > currentOrder) {
                        transaction.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left);
                    } else {
                        transaction.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right);
                    }
                }

                if (!targetFragment.isAdded()) {
                    transaction.add(R.id.fragment_container, targetFragment, tag);
                    if (currentFragment != null) {
                        transaction.hide(currentFragment);
                        transaction.setMaxLifecycle(currentFragment, Lifecycle.State.STARTED);
                    }
                } else {
                    if (currentFragment != null) {
                        transaction.hide(currentFragment);
                        transaction.setMaxLifecycle(currentFragment, Lifecycle.State.STARTED);
                    }
                    transaction.show(targetFragment);
                    transaction.setMaxLifecycle(targetFragment, Lifecycle.State.RESUMED);
                }
                
                transaction.commit();
                
                currentFragment = targetFragment;
                currentItemId = newItemId;
                return true;
            }
            return false;
        });

        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_home);
        }

        UpdateChecker.checkUpdate(this, false);
    }

    private void updateToolbar(int itemId) {
        ImageView toolbarIcon = findViewById(R.id.toolbar_icon);
        TextView toolbarTitle = findViewById(R.id.toolbar_title);
        
        if (toolbarIcon == null || toolbarTitle == null) return;

        if (itemId == R.id.nav_home) {
            toolbarTitle.setText("FimTale");
            toolbarIcon.setImageResource(R.drawable.ic_fimtale_logo);
        } else if (itemId == R.id.nav_article) {
            toolbarTitle.setText("文章列表");
            toolbarIcon.setImageDrawable(
                    MdiIcons.drawable(this, CommunityMaterial.Icon.cmd_book_open_page_variant));
        } else if (itemId == R.id.nav_profile) {
            toolbarTitle.setText("我的");
            toolbarIcon.setImageDrawable(
                    MdiIcons.drawable(this, CommunityMaterial.Icon.cmd_account));
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("currentItemId", currentItemId);
    }
}
