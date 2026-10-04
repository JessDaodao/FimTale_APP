package com.app.fimtale;

import android.content.Intent;
import android.animation.ValueAnimator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.app.fimtale.editor.EditorDraftStore;
import com.app.fimtale.editor.EditorDocument;
import com.app.fimtale.editor.OnlineDraft;
import com.app.fimtale.editor.DraftCodec;
import com.app.fimtale.editor.DraftRemote;
import com.app.fimtale.editor.DraftSync;
import com.app.fimtale.network.RetrofitClient;
import com.app.fimtale.network.SiteUrls;
import com.app.fimtale.utils.UserPreferences;
import com.app.fimtale.ui.ShimmerSkeletonView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Website drafts, merged with pending local recovery copies. */
public class DraftsActivity extends AppCompatActivity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Row> items = new ArrayList<>();
    private static class Row {
        String key, title, preview;
        long revision, savedAt;
        boolean pending, uncertain;
        EditorDraftStore.Entry local;
    }
    private static long time(String value) {
        try { return java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli(); }
        catch (RuntimeException ignored) { return 0; }
    }
    private final DraftAdapter adapter = new DraftAdapter();
    private final SummaryAdapter summaryAdapter = new SummaryAdapter();
    private String summaryText;
    private ValueAnimator headerAnimator;
    private boolean headerRaised;
    private int generation;
    private String listedUser = "";
    private ShimmerSkeletonView loadingSkeleton;
    private boolean loading;
    private final ActivityResultLauncher<Intent> login = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (UserPreferences.isLoggedIn(this)) refresh(); else finish();
            });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_drafts);
        com.app.fimtale.utils.EditorWindowStyle.apply(this);
        MaterialToolbar toolbar = findViewById(R.id.toolbar); toolbar.setTitle("");
        // MaterialToolbar creates its own shape even for a null XML background.
        // Only the separate rounded surface should paint behind the title.
        toolbar.setBackground(null);
        ((TextView) findViewById(R.id.tvToolbarTitle)).setText(R.string.drafts_title);
        toolbar.setNavigationOnClickListener(v -> finish());
        RecyclerView list = findViewById(R.id.draftsList);
        loadingSkeleton = findViewById(R.id.draftsSkeleton);
        loadingSkeleton.setSkeletonLayout(ShimmerSkeletonView.Layout.DRAFTS);
        summaryText = getString(R.string.drafts_local_hint);
        list.setLayoutManager(new LinearLayoutManager(this)); list.setAdapter(new ConcatAdapter(summaryAdapter, adapter));
        list.setItemAnimator(null);
        View titleCard = findViewById(R.id.toolbarContainer);
        // The list scrolls behind the fixed card, including its summary row.
        titleCard.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int top = b + Math.round(16 * getResources().getDisplayMetrics().density);
            if (list.getPaddingTop() != top) list.setPadding(0, top, 0, list.getPaddingBottom());
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) loadingSkeleton.getLayoutParams();
            if (params.topMargin != top) { params.topMargin = top; loadingSkeleton.setLayoutParams(params); }
        });
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView view, int dx, int dy) {
                animateHeader(view.canScrollVertically(-1));
            }
        });
        findViewById(R.id.draftsCreate).setOnClickListener(v -> {
            if (!UserPreferences.isLoggedIn(this)) { login.launch(new Intent(this, LoginActivity.class)); return; }
            startActivity(EditorActivity.workIntent(this, 0));
        });
        findViewById(R.id.draftsEmpty).setOnClickListener(v -> refresh());
        if (state == null && !UserPreferences.isLoggedIn(this)) login.launch(new Intent(this, LoginActivity.class));
    }
    private void setDraftsLoading(boolean loading) {
        this.loading = loading;
        loadingSkeleton.setVisibility(loading ? View.VISIBLE : View.GONE);
        findViewById(R.id.draftsList).setVisibility(loading ? View.INVISIBLE : View.VISIBLE);
        findViewById(R.id.draftsEmpty).setVisibility(!loading && items.isEmpty() ? View.VISIBLE : View.GONE);
        findViewById(R.id.draftsCreate).setEnabled(!loading);
    }
    private void animateHeader(boolean raised) {
        if (headerRaised == raised) return;
        headerRaised = raised;
        if (headerAnimator != null) headerAnimator.cancel();
        View surface = findViewById(R.id.draftsHeaderSurface);
        // Fade an opaque shape and its fixed shadow together. Changing a translucent
        // MaterialCardView fill/elevation exposes shadow and elevation-overlay artifacts.
        headerAnimator = ValueAnimator.ofFloat(surface.getAlpha(), raised ? 1 : 0);
        headerAnimator.setDuration(200);
        headerAnimator.addUpdateListener(animation -> surface.setAlpha((float) animation.getAnimatedValue()));
        headerAnimator.start();
    }
    @Override protected void onResume() { super.onResume(); refresh(); }
    private void refresh() {
        int current = ++generation;
        String userId = UserPreferences.getUserId(this);
        if (!UserPreferences.isLoggedIn(this) || !listedUser.equals(userId)) { items.clear(); adapter.notifyDataSetChanged(); }
        listedUser = userId;
        if (!UserPreferences.isLoggedIn(this) || userId.isEmpty()) { setDraftsLoading(false); return; }
        setDraftsLoading(true);
        File directory = new File(getNoBackupFilesDir(), "editor_drafts");
        String token = UserPreferences.getToken(this);
        io.execute(() -> {
            try {
                DraftRemote remote = new DraftRemote(RetrofitClient.getInstance(), token);
                java.util.LinkedHashMap<String, Row> rows = new java.util.LinkedHashMap<>();
                String warning = null;
                List<EditorDraftStore.Entry> local = EditorDraftStore.list(directory, SiteUrls.API, userId);
                // Import drafts created by earlier app builds, with revision checks protecting
                // any draft already created on the website under the same entity key.
                for (EditorDraftStore.Entry entry : local) {
                    EditorDocument document = entry.read();
                    if (document != null && document.draftKey == null && token.equals(UserPreferences.getToken(this))) {
                        try {
                            DraftSync sync = entry.synchronizer(); EditorDocument pending = sync.load(remote);
                            if (pending != null) sync.save(remote, pending);
                        } catch (Exception ignored) { warning = "部分本机草稿尚未同步，打开草稿可重试"; }
                    }
                }
                boolean offline = false;
                try {
                    for (OnlineDraft.Summary summary : remote.list()) {
                        if (summary.key == null) continue;
                        Row row = new Row(); row.key = summary.key; row.title = summary.title; row.preview = summary.preview;
                        row.revision = summary.revision; row.savedAt = time(summary.updatedAt); rows.put(row.key, row);
                    }
                } catch (Exception e) { offline = true; warning = "无法读取在线草稿，显示本机副本；点击提示可重试"; }
                for (EditorDraftStore.Entry entry : EditorDraftStore.list(directory, SiteUrls.API, userId)) {
                    Row row = rows.get(entry.onlineKey);
                    if (entry.pendingSync || entry.submissionUncertain || (offline && row == null)) {
                        row = new Row(); row.key = entry.onlineKey; row.title = entry.title; row.preview = entry.preview;
                        row.revision = entry.revision; row.savedAt = entry.savedAt;
                        row.pending = entry.pendingSync; row.uncertain = entry.submissionUncertain; rows.put(row.key, row);
                    }
                    if (row != null) row.local = entry;
                }
                List<Row> drafts = new ArrayList<>(rows.values()); drafts.sort(java.util.Comparator.comparingLong((Row row) -> row.savedAt).reversed());
                String notice = warning;
                main.post(() -> {
                    if (isFinishing() || isDestroyed() || current != generation || !token.equals(UserPreferences.getToken(this))) return;
                    items.clear(); items.addAll(drafts); adapter.notifyDataSetChanged();
                    setDraftsLoading(false);
                    TextView empty = findViewById(R.id.draftsEmpty); empty.setText(R.string.drafts_empty);
                    if (notice != null && items.isEmpty()) empty.setText("无法读取在线草稿，点击重试");
                    empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                    summaryText = notice == null ? items.size() + " 篇草稿" : notice;
                    summaryAdapter.notifyItemChanged(0);
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (isFinishing() || isDestroyed() || current != generation || !token.equals(UserPreferences.getToken(this))) return;
                    setDraftsLoading(false);
                    TextView empty = findViewById(R.id.draftsEmpty); empty.setText("无法读取草稿，点击重试");
                    summaryText = "无法读取草稿，点击重试";
                    summaryAdapter.notifyItemChanged(0);
                });
            }
        });
    }
    private boolean accountMatches() {
        if (UserPreferences.isLoggedIn(this) && listedUser.equals(UserPreferences.getUserId(this))) return true;
        refresh(); return false;
    }
    private void launch(int workId, int chapterId, String slot) {
        startActivity(new Intent(this, EditorActivity.class).putExtra(EditorActivity.EXTRA_WORK_ID, workId)
                .putExtra(EditorActivity.EXTRA_CHAPTER_ID, chapterId)
                .putExtra(EditorActivity.EXTRA_DRAFT_ID, slot == null ? "" : slot));
    }
    private void open(Row row) {
        if (loading) return;
        if (!accountMatches()) return;
        if (row.local != null) { launch(row.local.workId, row.local.chapterId, row.local.draftId); return; }
        int current = ++generation;
        setDraftsLoading(true);
        String token = UserPreferences.getToken(this);
        io.execute(() -> {
            try {
                OnlineDraft draft = new DraftRemote(RetrofitClient.getInstance(), token).get(row.key);
                if (draft == null) throw new java.io.IOException("在线草稿已删除，请刷新列表");
                DraftCodec.Target target = DraftCodec.target(draft.key, draft.payload);
                main.post(() -> {
                    if (isFinishing() || isDestroyed() || current != generation || !token.equals(UserPreferences.getToken(this))) return;
                    setDraftsLoading(false); launch(target.workId, target.chapterId, target.slot);
                });
            } catch (Exception e) { main.post(() -> {
                if (isFinishing() || isDestroyed() || current != generation || !token.equals(UserPreferences.getToken(this))) return;
                setDraftsLoading(false); Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            }); }
        });
    }
    private void remove(Row row) {
        if (!accountMatches()) return;
        new MaterialAlertDialogBuilder(this).setTitle("删除在线草稿？")
                .setMessage("删除“" + row.title + "”的草稿后，网站和 App 均不再保留此草稿。已发表内容不受影响。")
                .setNegativeButton("取消", null).setPositiveButton("删除", (d, w) -> {
                    String token = UserPreferences.getToken(this);
                    io.execute(() -> {
                        try {
                            new DraftRemote(RetrofitClient.getInstance(), token).delete(row.key, row.revision);
                            if (row.local != null) row.local.delete();
                            main.post(() -> { if (!isDestroyed()) refresh(); });
                        } catch (Exception e) { main.post(() -> {
                            if (isDestroyed()) return;
                            Toast.makeText(this, "删除失败：" + e.getMessage(), Toast.LENGTH_LONG).show(); refresh();
                        }); }
                    });
                }).show();
    }
    private static class SummaryHolder extends RecyclerView.ViewHolder {
        SummaryHolder(View view) { super(view); }
    }
    private class SummaryAdapter extends RecyclerView.Adapter<SummaryHolder> {
        @NonNull @Override public SummaryHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new SummaryHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_drafts_summary, parent, false));
        }
        @Override public void onBindViewHolder(@NonNull SummaryHolder holder, int position) {
            ((TextView) holder.itemView).setText(summaryText);
            holder.itemView.setOnClickListener(v -> refresh());
        }
        @Override public int getItemCount() { return 1; }
    }
    private class DraftAdapter extends RecyclerView.Adapter<DraftHolder> {
        @NonNull @Override public DraftHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new DraftHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_editor_draft, parent, false));
        }
        @Override public void onBindViewHolder(@NonNull DraftHolder holder, int position) {
            Row entry = items.get(position);
            holder.title.setText(com.app.fimtale.editor.WorkInput.blank(entry.title) ? "未命名草稿" : entry.title);
            holder.preview.setText(com.app.fimtale.editor.WorkInput.blank(entry.preview) ? "尚未填写正文" : entry.preview);
            String kind = entry.key.startsWith("chapter:") ? "章节草稿" : "文章草稿";
            String time = entry.savedAt > 0 ? DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(entry.savedAt)) : "尚未保存";
            holder.time.setText(kind + " · " + time + (entry.uncertain ? " · 提交结果待核对" : entry.pending ? " · 待同步" : ""));
            holder.itemView.setOnClickListener(v -> open(entry));
            holder.more.setOnClickListener(v -> {
                PopupMenu menu = new PopupMenu(DraftsActivity.this, v); menu.getMenu().add("删除草稿");
                menu.setOnMenuItemClickListener(item -> { remove(entry); return true; }); menu.show();
            });
        }
        @Override public int getItemCount() { return items.size(); }
    }
    private static class DraftHolder extends RecyclerView.ViewHolder {
        final TextView title, preview, time; final View more;
        DraftHolder(View view) {
            super(view); title = view.findViewById(R.id.draftTitle); preview = view.findViewById(R.id.draftPreview);
            time = view.findViewById(R.id.draftTime); more = view.findViewById(R.id.draftMore);
        }
    }
    @Override protected void onDestroy() {
        generation++; if (headerAnimator != null) headerAnimator.cancel(); io.shutdown(); super.onDestroy();
    }
}
