package com.fimtale;

import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.view.Menu;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.fimtale.review.ReviewActionDialog;
import com.fimtale.review.ReviewQueueAdapter;
import com.fimtale.review.ReviewQueueViewModel;
import com.fimtale.utils.EditorWindowStyle;
import com.fimtale.utils.MdiIcons;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;

public class ReviewQueueActivity extends AppCompatActivity {
    public static final String EXTRA_WORK_ID = "review_work_id", EXTRA_REVIEW_ID = "review_id";
    private ReviewQueueViewModel model;
    private ReviewQueueAdapter adapter;
    private RecyclerView list;
    private MaterialToolbar toolbar;
    private MaterialCardView header;
    private ObjectAnimator elevation;
    private boolean raised, showPending;
    private String displayedQuery;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_review_queue); EditorWindowStyle.apply(this);
        model = new ViewModelProvider(this).get(ReviewQueueViewModel.class);
        if (!model.initialized) {
            if (state == null) {
                int work = getIntent().getIntExtra(EXTRA_WORK_ID, 0), review = getIntent().getIntExtra(EXTRA_REVIEW_ID, 0);
                model.workFilter = work > 0 ? work : null; model.reviewFilter = review > 0 ? review : null;
                if (model.workFilter != null || model.reviewFilter != null) model.mode = ReviewQueueViewModel.Mode.HISTORY;
            } else {
                try { model.mode = ReviewQueueViewModel.Mode.valueOf(state.getString("mode", "ASSIGNED")); } catch (IllegalArgumentException ignored) {}
                model.page = Math.max(1, state.getInt("page", 1));
                model.workFilter = state.getInt("work", 0) > 0 ? state.getInt("work") : null;
                model.reviewFilter = state.getInt("review", 0) > 0 ? state.getInt("review") : null;
                model.statusFilter = state.getInt("status", 0) > 0 ? state.getInt("status") : null;
            }
            model.initialized = true;
        }
        toolbar = findViewById(R.id.toolbar); toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.getMenu().add(Menu.NONE, R.id.action_review_refresh, Menu.NONE, R.string.review_refresh)
                .setIcon(MdiIcons.drawable(this, "refresh")).setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
        toolbar.setOnMenuItemClickListener(item -> { model.refresh(); return true; });
        header = findViewById(R.id.toolbarContainer);
        list = findViewById(R.id.reviewList); list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ReviewQueueAdapter(this, model); list.setAdapter(adapter); list.setItemAnimator(null);
        header.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int padding = b + Math.round(16 * getResources().getDisplayMetrics().density);
            if (list.getPaddingTop() != padding) list.setPadding(list.getPaddingLeft(), padding, list.getPaddingRight(), list.getPaddingBottom());
        });
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView view, int dx, int dy) {
                boolean next = view.canScrollVertically(-1);
                if (next == raised) return;
                raised = next;
                if (elevation != null) elevation.cancel();
                elevation = ObjectAnimator.ofFloat(header, "cardElevation", header.getCardElevation(),
                        raised ? 4 * getResources().getDisplayMetrics().density : 0);
                elevation.setDuration(200); elevation.start();
            }
        });
        model.changes.observe(this, ignored -> render());
    }
    private void render() {
        if (isFinishing() || isDestroyed()) return;
        if (list.isComputingLayout()) { list.post(this::render); return; }
        adapter.notifyDataSetChanged();
        toolbar.getMenu().findItem(R.id.action_review_refresh).setEnabled(!model.loading && !model.mutating);
        String query = model.mode + ":" + model.statusFilter + ":" + model.workFilter + ":" + model.reviewFilter + ":" + model.page;
        if (!query.equals(displayedQuery)) { displayedQuery = query; list.scrollToPosition(0); }
        if (model.notice != null) { Toast.makeText(this, model.notice, Toast.LENGTH_SHORT).show(); model.notice = null; }
        syncDialog();
    }
    private void syncDialog() {
        FragmentManager fragments = getSupportFragmentManager();
        if (isFinishing() || isDestroyed() || fragments.isStateSaved()) return;
        Fragment existing = fragments.findFragmentByTag(ReviewActionDialog.TAG);
        if (model.action != ReviewQueueViewModel.Action.NONE && existing == null && !showPending) {
            showPending = true;
            new ReviewActionDialog().show(fragments.beginTransaction().runOnCommit(() -> { showPending = false; syncDialog(); }), ReviewActionDialog.TAG);
        } else if (model.action == ReviewQueueViewModel.Action.NONE && existing instanceof ReviewActionDialog) {
            ((ReviewActionDialog) existing).dismiss();
        }
    }
    @Override protected void onResume() { super.onResume(); model.connect(); }
    @Override protected void onPostResume() { super.onPostResume(); syncDialog(); }
    @Override protected void onSaveInstanceState(@NonNull Bundle out) {
        out.putString("mode", model.mode.name()); out.putInt("page", model.page);
        if (model.statusFilter != null) out.putInt("status", model.statusFilter);
        if (model.workFilter != null) out.putInt("work", model.workFilter);
        if (model.reviewFilter != null) out.putInt("review", model.reviewFilter);
        super.onSaveInstanceState(out);
    }
    @Override protected void onDestroy() { if (elevation != null) elevation.cancel(); super.onDestroy(); }
}
