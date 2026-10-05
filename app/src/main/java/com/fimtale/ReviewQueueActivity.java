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
import com.fimtale.review.ReviewEntry;
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
    private boolean highlighted;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_review_queue); EditorWindowStyle.apply(this);
        model = new ViewModelProvider(this).get(ReviewQueueViewModel.class);
        highlighted = state != null && state.getBoolean("highlighted");
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
        adapter.rebuild();
        toolbar.getMenu().findItem(R.id.action_review_refresh).setEnabled(!model.loading && !model.mutating);
        if (!highlighted && model.loaded) {
            int position = adapter.highlightedPosition();
            if (position >= 0) { ((LinearLayoutManager) list.getLayoutManager()).scrollToPositionWithOffset(position, 0); highlighted = true; }
        }
        if (model.notice != null) { Toast.makeText(this, model.notice, Toast.LENGTH_SHORT).show(); model.notice = null; }
        syncDialog();
    }
    private void syncDialog() {
        FragmentManager fragments = getSupportFragmentManager();
        if (isFinishing() || isDestroyed() || fragments.isStateSaved()) return;
        Fragment existing = fragments.findFragmentByTag(ReviewActionDialog.TAG);
        if (model.selected != null && existing == null && !showPending) {
            showPending = true;
            new ReviewActionDialog().show(fragments.beginTransaction().runOnCommit(() -> { showPending = false; syncDialog(); }), ReviewActionDialog.TAG);
        } else if (model.selected == null && existing instanceof ReviewActionDialog) {
            ((ReviewActionDialog) existing).dismiss();
        }
    }
    @Override protected void onResume() { super.onResume(); model.connect(); }
    @Override protected void onPostResume() { super.onPostResume(); syncDialog(); }
    @Override protected void onSaveInstanceState(@NonNull Bundle out) {
        out.putBoolean("highlighted", highlighted);
        super.onSaveInstanceState(out);
    }
    @Override protected void onDestroy() { if (elevation != null) elevation.cancel(); super.onDestroy(); }
    public boolean isHighlighted(ReviewEntry entry) {
        int review = getIntent().getIntExtra(EXTRA_REVIEW_ID, 0), work = getIntent().getIntExtra(EXTRA_WORK_ID, 0);
        return review > 0 ? entry.id == review : work > 0 && entry.workId == work;
    }
}
