package com.app.fimtale.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.annotation.NonNull;
import android.content.Intent;
import com.app.fimtale.AboutActivity;
import com.app.fimtale.R;
import com.app.fimtale.SettingsActivity;
import com.app.fimtale.AccountSessionsActivity;
import com.app.fimtale.ContentFiltersActivity;
import com.app.fimtale.db.CacheManager;
import com.app.fimtale.utils.UserPreferences;
import com.app.fimtale.utils.DialogHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import android.widget.Toast;
import androidx.preference.ListPreference;

public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

    private final Preference.OnPreferenceChangeListener gravityChangeListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
            boolean enabled = (boolean) newValue;
            if (enabled) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("警告")
                        .setMessage("该模式仅为娱乐使用，可能会导致一系列意想不到的BUG！确定开启吗？")
                        .setPositiveButton("确定", (dialog, which) -> {
                            preference.setOnPreferenceChangeListener(null);
                            ((SwitchPreferenceCompat) preference).setChecked(true);
                            preference.setOnPreferenceChangeListener(this);
                        })
                        .setNegativeButton("取消", null)
                        .show();
                return false;
            }
            return true;
        }
    };

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        setDivider(null);
        setDividerHeight(0);

        RecyclerView recyclerView = getListView();
        int paddingTop = (int) (88 * getResources().getDisplayMetrics().density);
        recyclerView.setPadding(0, paddingTop, 0, 0);
        recyclerView.setClipToPadding(false);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (getActivity() instanceof SettingsActivity) {
                    ((SettingsActivity) getActivity()).onScroll(recyclerView.computeVerticalScrollOffset());
                }
            }
        });
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        Preference account = findPreference("account");
        if (account != null) account.setOnPreferenceClickListener(preference -> {
            if (UserPreferences.isLoggedIn(requireContext())) startActivity(new Intent(requireContext(), AccountSessionsActivity.class));
            else DialogHelper.openLogin(requireContext());
            return true;
        });
        Preference filters = findPreference("content_filters");
        if (filters != null) filters.setOnPreferenceClickListener(preference -> {
            if (UserPreferences.isLoggedIn(requireContext())) startActivity(new Intent(requireContext(), ContentFiltersActivity.class));
            else DialogHelper.openLogin(requireContext());
            return true;
        });

        SwitchPreferenceCompat gravityPref = findPreference("gravity_mode");
        if (gravityPref != null) {
            gravityPref.setOnPreferenceChangeListener(gravityChangeListener);
        }
        
        Preference logoutPref = findPreference("logout");
        if (logoutPref != null) {
            if (UserPreferences.isLoggedIn(requireContext())) {
                logoutPref.setVisible(true);
                logoutPref.setOnPreferenceClickListener(preference -> {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("退出登录")
                            .setMessage("确定要退出登录吗？")
                            .setPositiveButton("确定", (dialog, which) -> {
                                com.app.fimtale.network.RetrofitClient.getInstance().logout(UserPreferences.getToken(requireContext())).enqueue(new retrofit2.Callback<okhttp3.ResponseBody>() {
                                    @Override public void onResponse(retrofit2.Call<okhttp3.ResponseBody> call, retrofit2.Response<okhttp3.ResponseBody> response) {
                                        if (response.body() != null) response.body().close();
                                    }
                                    @Override public void onFailure(retrofit2.Call<okhttp3.ResponseBody> call, Throwable t) {}
                                });
                                UserPreferences.clearSession(requireContext());
                                CacheManager.getInstance(requireContext()).clearAllCache(null);
                                Toast.makeText(requireContext(), "已退出登录", Toast.LENGTH_SHORT).show();
                                getActivity().finish();
                            })
                            .setNegativeButton("取消", null)
                            .show();
                    return true;
                });
            } else {
                logoutPref.setVisible(false);
            }
        }

        Preference aboutPref = findPreference("about");
        if (aboutPref != null) {
            aboutPref.setOnPreferenceClickListener(preference -> {
                startActivity(new Intent(requireContext(), AboutActivity.class));
                return true;
            });
        }

        // Cache management
        Preference cacheSizePref = findPreference("cache_size_display");
        if (cacheSizePref != null) {
            CacheManager.getInstance(requireContext()).getTotalCacheSize(size ->
                    cacheSizePref.setSummary(formatBytes(size)));
        }

        ListPreference maxCachePref = findPreference("max_cache_size");
        if (maxCachePref != null) {
            long currentMax = UserPreferences.getMaxCacheSize(requireContext());
            maxCachePref.setValue(String.valueOf(currentMax));
            maxCachePref.setSummary(formatBytes(currentMax));
            maxCachePref.setOnPreferenceChangeListener((preference, newValue) -> {
                long newMax = Long.parseLong((String) newValue);
                UserPreferences.setMaxCacheSize(requireContext(), newMax);
                maxCachePref.setSummary(formatBytes(newMax));
                CacheManager.getInstance(requireContext()).setMaxCacheSize(newMax);
                return true;
            });
        }

        Preference clearCachePref = findPreference("clear_cache");
        if (clearCachePref != null) {
            clearCachePref.setOnPreferenceClickListener(preference -> {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("清除缓存")
                        .setMessage("确定要清除所有已缓存的章节吗？")
                        .setPositiveButton("确定", (dialog, which) ->
                                CacheManager.getInstance(requireContext()).clearAllCache(() -> {
                                    if (cacheSizePref != null) {
                                        cacheSizePref.setSummary(formatBytes(0));
                                    }
                                    Toast.makeText(requireContext(), "缓存已清除", Toast.LENGTH_SHORT).show();
                                }))
                        .setNegativeButton("取消", null)
                        .show();
                return true;
            });
        }
    }
    
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if ("theme_follow_system".equals(key)) {
            boolean followSystem = sharedPreferences.getBoolean(key, true);
            if (followSystem) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            } else {
                boolean manualDarkMode = sharedPreferences.getBoolean("manual_dark_mode", false);
                if (manualDarkMode) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                } else {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                }
            }
        } else if ("auto_update".equals(key)) {
            boolean autoUpdate = sharedPreferences.getBoolean(key, true);
            UserPreferences.setAutoUpdate(requireContext(), autoUpdate);
        } else if ("show_reader_progress".equals(key)) {
            boolean show = sharedPreferences.getBoolean(key, false);
            UserPreferences.setShowReaderProgress(requireContext(), show);
        }
    }
}
