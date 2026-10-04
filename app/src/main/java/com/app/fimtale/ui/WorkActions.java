package com.app.fimtale.ui;

import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import com.app.fimtale.R;
import com.app.fimtale.model.TopicDetailResponse;
import com.app.fimtale.model.WorkInteractions;
import com.app.fimtale.network.ApiErrors;
import com.app.fimtale.network.RetrofitClient;
import com.app.fimtale.utils.UserPreferences;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Owns authenticated actions; always re-reads server state after a mutation. */
public final class WorkActions {
    private final AppCompatActivity activity;
    private final int workId;
    private final Runnable login;
    private final Consumer<TopicDetailResponse> updated;
    private final MaterialButton like, favorite;
    private final TextView status;
    private TopicDetailResponse data;
    private String stateToken;
    private boolean busy, known, closed;
    private Call<?> activeCall;
    private AlertDialog foldersDialog;

    public WorkActions(AppCompatActivity activity, int workId, Runnable login, Consumer<TopicDetailResponse> updated) {
        this.activity = activity; this.workId = workId; this.login = login; this.updated = updated;
        stateToken = UserPreferences.getToken(activity);
        like = activity.findViewById(R.id.likeWorkButton);
        favorite = activity.findViewById(R.id.favoriteWorkButton);
        status = activity.findViewById(R.id.workActionStatus);
        like.setOnClickListener(v -> vote());
        favorite.setOnClickListener(v -> chooseFavorites());
        status.setOnClickListener(v -> { if (!busy) refresh(); });
    }

    public void bind(TopicDetailResponse data, String token) {
        if (closed || busy || !token.equals(UserPreferences.getToken(activity))) return;
        this.data = data; stateToken = token;
        known = data.viewer != null || token.isEmpty();
        render();
    }

    public void onResume() {
        String token = UserPreferences.getToken(activity);
        if (token.equals(stateToken)) return;
        if (activeCall != null) activeCall.cancel();
        activeCall = null;
        if (foldersDialog != null) foldersDialog.dismiss();
        stateToken = token; known = false; busy = false;
        refresh();
    }

    private boolean valid(Call<?> call, String token) {
        if (closed || activity.isFinishing() || activity.isDestroyed() || call.isCanceled() || call != activeCall) return false;
        if (!token.equals(UserPreferences.getToken(activity))) {
            onResume();
            Toast.makeText(activity, "登录状态已变化，请重试", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private boolean authenticated() {
        if (!UserPreferences.isLoggedIn(activity)) { login.run(); return false; }
        if (!stateToken.equals(UserPreferences.getToken(activity))) { onResume(); return false; }
        return !busy && known && data != null;
    }

    private void render() {
        boolean liked = known && data != null && data.viewer != null && data.viewer.isLiked();
        boolean faved = known && data != null && data.viewer != null && !data.viewer.folderIds().isEmpty();
        int likes = data == null ? 0 : data.getTopicInfo().getLikeCount();
        int favorites = data == null ? 0 : data.getTopicInfo().getFavoriteCount();
        like.setText(String.valueOf(likes));
        favorite.setText(String.valueOf(favorites));
        boolean enabled = !busy && data != null && (known || !UserPreferences.isLoggedIn(activity));
        like.setEnabled(enabled); favorite.setEnabled(enabled);
        like.setChecked(liked); favorite.setChecked(faved);
        like.setContentDescription((liked ? "取消点赞，" : "点赞，") + likes);
        favorite.setContentDescription((faved ? "管理收藏夹，已收藏，" : "收藏到收藏夹，") + favorites);
        // Mutations can take a moment, but the action row should remain quiet while
        // the server state is being reconciled. Only expose the retry affordance
        // when the initial interaction state could not be loaded.
        status.setVisibility(!busy && !known ? View.VISIBLE : View.GONE);
        status.setText(!busy && !known ? "互动状态加载失败，点击重试" : "");
    }

    private void vote() {
        if (!authenticated()) return;
        busy = true; render();
        String token = stateToken;
        Call<Void> call = RetrofitClient.getInstance().voteWork(token, new WorkInteractions.Vote(workId));
        activeCall = call;
        call.enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> response) {
                if (!valid(call, token)) return;
                if (!response.isSuccessful()) Toast.makeText(activity, ApiErrors.message(response), Toast.LENGTH_LONG).show();
                refresh();
            }
            @Override public void onFailure(Call<Void> call, Throwable error) {
                if (!valid(call, token)) return;
                // Vote toggles. Never repeat a POST whose response might have been lost.
                Toast.makeText(activity, "未收到点赞结果，正在核对状态", Toast.LENGTH_LONG).show();
                refresh();
            }
        });
    }

    private void refresh() {
        if (closed) return;
        busy = true; known = false; render();
        String token = UserPreferences.getToken(activity);
        stateToken = token;
        Call<TopicDetailResponse> call = RetrofitClient.getInstance().getWorkViewer(token, workId);
        activeCall = call;
        call.enqueue(new Callback<TopicDetailResponse>() {
            @Override public void onResponse(Call<TopicDetailResponse> call, Response<TopicDetailResponse> response) {
                if (!valid(call, token)) return;
                busy = false;
                if (response.isSuccessful() && response.body() != null && response.body().getTopicInfo() != null) {
                    data = response.body(); known = data.viewer != null || token.isEmpty();
                    updated.accept(data);
                }
                render();
            }
            @Override public void onFailure(Call<TopicDetailResponse> call, Throwable error) {
                if (!valid(call, token)) return;
                busy = false; render();
            }
        });
    }

    private void chooseFavorites() {
        if (!authenticated()) return;
        busy = true; render();
        String token = stateToken;
        Call<List<WorkInteractions.Folder>> call = RetrofitClient.getInstance().getFavoriteFolders(token);
        activeCall = call;
        call.enqueue(new Callback<List<WorkInteractions.Folder>>() {
            @Override public void onResponse(Call<List<WorkInteractions.Folder>> call, Response<List<WorkInteractions.Folder>> response) {
                if (!valid(call, token)) return;
                busy = false; render();
                if (!response.isSuccessful()) {
                    Toast.makeText(activity, ApiErrors.message(response), Toast.LENGTH_LONG).show(); return;
                }
                showFolders(response.body(), token);
            }
            @Override public void onFailure(Call<List<WorkInteractions.Folder>> call, Throwable error) {
                if (!valid(call, token)) return;
                busy = false; render();
                Toast.makeText(activity, "收藏夹加载失败，请重试", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showFolders(List<WorkInteractions.Folder> remote, String token) {
        List<WorkInteractions.Folder> folders = new ArrayList<>();
        folders.add(new WorkInteractions.Folder(0, "默认收藏夹"));
        Set<Integer> ids = new LinkedHashSet<>(); ids.add(0);
        if (remote != null) for (WorkInteractions.Folder folder : remote) {
            if (folder != null && folder.id > 0 && ids.add(folder.id)) folders.add(folder);
        }
        Set<Integer> initial = data.viewer == null ? new LinkedHashSet<>() : data.viewer.folderIds();
        // Keep existing memberships even if a folder is absent from the returned list.
        for (int id : initial) if (ids.add(id)) folders.add(new WorkInteractions.Folder(id, "收藏夹 " + id));
        Set<Integer> selected = new LinkedHashSet<>(initial);
        if (selected.isEmpty()) selected.add(0);
        String[] labels = new String[folders.size()]; boolean[] checked = new boolean[folders.size()];
        for (int i = 0; i < folders.size(); i++) {
            labels[i] = folders.get(i).name; checked[i] = selected.contains(folders.get(i).id);
        }
        foldersDialog = new MaterialAlertDialogBuilder(activity).setTitle("收藏到（取消勾选可移除）")
                .setMultiChoiceItems(labels, checked, (dialog, index, value) -> {
                    int id = folders.get(index).id;
                    if (value) selected.add(id); else selected.remove(id);
                })
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    if (!token.equals(UserPreferences.getToken(activity))) { onResume(); return; }
                    List<WorkInteractions.Change> changes = WorkInteractions.changes(initial, selected);
                    if (changes.isEmpty()) return;
                    busy = true; render();
                    saveFavorites(changes, 0, token);
                }).create();
        foldersDialog.show();
    }

    private void saveFavorites(List<WorkInteractions.Change> changes, int index, String token) {
        if (index >= changes.size()) { refresh(); return; }
        if (!token.equals(UserPreferences.getToken(activity))) { onResume(); return; }
        WorkInteractions.Change change = changes.get(index);
        WorkInteractions.FavoriteRequest request = new WorkInteractions.FavoriteRequest(workId, change.folderId);
        Call<Void> call = change.add ? RetrofitClient.getInstance().addFavoriteWork(token, request)
                : RetrofitClient.getInstance().removeFavoriteWork(token, request);
        activeCall = call;
        call.enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> response) {
                if (!valid(call, token)) return;
                if (response.isSuccessful()) saveFavorites(changes, index + 1, token);
                else {
                    Toast.makeText(activity, ApiErrors.message(response), Toast.LENGTH_LONG).show();
                    refresh();
                }
            }
            @Override public void onFailure(Call<Void> call, Throwable error) {
                if (!valid(call, token)) return;
                Toast.makeText(activity, "收藏未全部完成，正在核对状态", Toast.LENGTH_LONG).show();
                refresh();
            }
        });
    }

    public void close() {
        closed = true;
        if (activeCall != null) activeCall.cancel();
        if (foldersDialog != null) foldersDialog.dismiss();
    }
}
