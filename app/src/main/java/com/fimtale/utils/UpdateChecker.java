package com.fimtale.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import com.fimtale.R;
import com.fimtale.model.UpdateResponse;
import com.fimtale.network.RetrofitClient;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UpdateChecker {

    private static final String UPDATE_URL = "https://ftapp.eqad.fun/update/";
    private static final String UPDATE_HOST = "ftapp.eqad.fun";
    private static final ExecutorService DOWNLOAD_EXECUTOR = Executors.newSingleThreadExecutor();

    public static void checkUpdate(Context context, boolean manual) {
        RetrofitClient.getUpdateService().checkUpdate(UPDATE_URL).enqueue(new Callback<UpdateResponse>() {
            @Override
            public void onResponse(@NonNull Call<UpdateResponse> call, @NonNull Response<UpdateResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    UpdateResponse update = response.body();
                    int currentVersion = getVersionCode(context);
                    if (update.getVersionCode() > currentVersion) {
                        if (update.isForceUpdate() || manual || UserPreferences.isAutoUpdateEnabled(context)) {
                            showUpdateDialog(context, update);
                        }
                    } else if (manual) {
                        Toast.makeText(context, context.getString(R.string.update_latest), Toast.LENGTH_SHORT).show();
                    }
                } else if (manual) {
                    Toast.makeText(context, context.getString(R.string.update_check_failed), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<UpdateResponse> call, @NonNull Throwable t) {
                if (manual) {
                    Toast.makeText(context, context.getString(R.string.error_network), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private static int getVersionCode(Context context) {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return (int) pInfo.getLongVersionCode();
            } else {
                return pInfo.versionCode;
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return 0;
        }
    }

    private static void showUpdateDialog(Context context, UpdateResponse update) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.update_new_version, update.getVersionName()))
                .setMessage(update.getUpdateLog())
                .setCancelable(!update.isForceUpdate())
                .setPositiveButton(context.getString(R.string.update_now), (dialog, which) -> {
                    downloadAndInstallApk(context, update.getDownloadUrl());
                });

        if (!update.isForceUpdate()) {
            builder.setNegativeButton(context.getString(R.string.update_later), null);
        }

        builder.show();
    }

    private static void downloadAndInstallApk(Context context, String downloadUrl) {
        if (!(context instanceof Activity) || !isTrustedDownloadUrl(downloadUrl)) {
            Toast.makeText(context, context.getString(R.string.update_invalid_url), Toast.LENGTH_LONG).show();
            return;
        }
        Activity activity = (Activity) context;
        activity.runOnUiThread(() -> {
            View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_update_progress, null);
            LinearProgressIndicator progressIndicator = dialogView.findViewById(R.id.progress_indicator);
            TextView tvPercent = dialogView.findViewById(R.id.tv_progress_percent);

            AlertDialog progressDialog = new MaterialAlertDialogBuilder(context)
                    .setTitle(context.getString(R.string.update_downloading))
                    .setView(dialogView)
                    .setCancelable(false)
                    .create();
            progressDialog.show();

            DOWNLOAD_EXECUTOR.execute(() -> {
                HttpURLConnection connection = null;
                try {
                    URL url = new URL(downloadUrl);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(15_000);
                    connection.setReadTimeout(30_000);
                    connection.setInstanceFollowRedirects(false);
                    connection.connect();
                    if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
                        throw new java.io.IOException(context.getString(R.string.error_http_status, connection.getResponseCode()));
                    }

                    int fileLength = connection.getContentLength();
                    File externalFilesDir = context.getExternalFilesDir(null);
                    if (externalFilesDir == null) throw new java.io.IOException(context.getString(R.string.drafts_storage_unavailable));
                    File apkFile = new File(externalFilesDir, "update.apk");

                    byte[] buffer = new byte[4096];
                    int len;
                    long total = 0;
                    try (InputStream inputStream = connection.getInputStream();
                         FileOutputStream outputStream = new FileOutputStream(apkFile)) {
                        while ((len = inputStream.read(buffer)) != -1) {
                            total += len;
                            if (fileLength > 0) {
                                int progress = (int) (total * 100 / fileLength);
                                activity.runOnUiThread(() -> {
                                    progressIndicator.setProgress(progress);
                                    tvPercent.setText(context.getString(R.string.common_percent, progress));
                                });
                            }
                            outputStream.write(buffer, 0, len);
                        }
                    }

                    activity.runOnUiThread(() -> {
                        progressDialog.dismiss();
                        installApk(context, apkFile);
                    });
                } catch (Exception e) {
                    activity.runOnUiThread(() -> {
                        progressDialog.dismiss();
                        Toast.makeText(context, context.getString(R.string.update_download_failed, e.getMessage()), Toast.LENGTH_LONG).show();
                    });
                } finally {
                    if (connection != null) connection.disconnect();
                }
            });
        });
    }

    private static boolean isTrustedDownloadUrl(String downloadUrl) {
        if (downloadUrl == null || downloadUrl.isEmpty()) return false;
        try {
            URI uri = URI.create(downloadUrl);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && UPDATE_HOST.equalsIgnoreCase(uri.getHost())
                    && uri.getUserInfo() == null
                    && uri.getQuery() == null
                    && uri.getFragment() == null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static void installApk(Context context, File apkFile) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Uri apkUri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            apkUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", apkFile);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            apkUri = Uri.fromFile(apkFile);
        }
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        context.startActivity(intent);
    }
}
