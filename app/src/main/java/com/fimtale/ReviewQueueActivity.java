package com.fimtale;

import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.fimtale.review.ReviewActionDialog;
import com.fimtale.review.ReviewEntry;
import com.fimtale.review.ReviewQueueAdapter;
import com.fimtale.review.ReviewQueueViewModel;
import com.fimtale.ui.ShimmerSkeletonView;
import com.fimtale.ui.PullToRefresh;
import com.fimtale.utils.EditorWindowStyle;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class ReviewQueueActivity extends AppCompatActivity {
    public static final String EXTRA_WORK_ID = "review_work_id", EXTRA_REVIEW_ID = "review_id";
    private ReviewQueueViewModel model;
    private final ReviewQueueAdapter[] adapters = new ReviewQueueAdapter[3];
    private final RecyclerView[] lists = new RecyclerView[3];
    private final Parcelable[] scrollStates = new Parcelable[3];
    private ViewPager2 pager;
    private TabLayout tabs;
    private TabLayoutMediator tabsMediator;
    private ShimmerSkeletonView loadingSkeleton;
    private MaterialToolbar toolbar;
    private MaterialCardView header;
    private boolean showPending;
    private boolean highlighted;
    private com.fimtale.ui.PageErrorView pageError;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_review_queue); EditorWindowStyle.apply(this);
        model = new ViewModelProvider(this).get(ReviewQueueViewModel.class);
        highlighted = state != null && state.getBoolean("highlighted");
        toolbar = findViewById(R.id.toolbar); toolbar.setNavigationOnClickListener(v -> finish());
        header = findViewById(R.id.toolbarContainer);
        pager = findViewById(R.id.reviewPager);
        pageError = com.fimtale.ui.PageErrorView.wrap(pager);
        tabs = findViewById(R.id.reviewTabs);
        loadingSkeleton = findViewById(R.id.reviewSkeleton);
        loadingSkeleton.setSkeletonLayout(ShimmerSkeletonView.Layout.DRAFTS);
        for (ReviewQueueViewModel.Section section : ReviewQueueViewModel.Section.values()) {
            int page = section.ordinal();
            adapters[page] = new ReviewQueueAdapter(this, model, section);
            if (state != null) scrollStates[page] = state.getParcelable("review_scroll_" + page);
        }
        setupPages();
        int[] titles = {R.string.review_ready, R.string.review_pending, R.string.review_completed};
        tabsMediator = new TabLayoutMediator(tabs, pager, (tab, position) -> tab.setText(titles[position]));
        tabsMediator.attach();
        if (state != null) pager.setCurrentItem(Math.max(0, Math.min(2, state.getInt("review_page"))), false);
        PullToRefresh.attach(pager, model::refresh, () -> !model.loading && !model.mutating,
                () -> lists[pager.getCurrentItem()] != null && lists[pager.getCurrentItem()].canScrollVertically(-1));
        header.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                findViewById(R.id.reviewContent).setPadding(0,
                        b + Math.round(16 * getResources().getDisplayMetrics().density), 0, 0));
        model.changes.observe(this, ignored -> render());
    }
    private void setupPages() {
        int[] ids = {R.id.reviewList, R.id.reviewPendingList, R.id.reviewCompletedList};
        pager.setOffscreenPageLimit(2);
        pager.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @Override public int getItemCount() { return adapters.length; }
            @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
                RecyclerView list = new RecyclerView(parent.getContext());
                list.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                list.setLayoutManager(new LinearLayoutManager(parent.getContext()));
                list.setItemAnimator(null); list.setClipToPadding(false);
                return new RecyclerView.ViewHolder(list) {};
            }
            @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                RecyclerView list = (RecyclerView) holder.itemView;
                lists[position] = list; list.setId(ids[position]);
                list.setPadding(0, Math.round(12 * getResources().getDisplayMetrics().density),
                        0, Math.round(12 * getResources().getDisplayMetrics().density));
                list.setAdapter(adapters[position]);
                if (scrollStates[position] != null) {
                    list.getLayoutManager().onRestoreInstanceState(scrollStates[position]); scrollStates[position] = null;
                }
                list.post(ReviewQueueActivity.this::revealHighlightedEntry);
            }
        });
    }
    private void render() {
        if (isFinishing() || isDestroyed()) return;
        for (RecyclerView list : lists) if (list != null && list.isComputingLayout()) { pager.post(this::render); return; }
        for (ReviewQueueAdapter adapter : adapters) adapter.rebuild();
        loadingSkeleton.setVisibility(model.loading ? View.VISIBLE : View.GONE);
        pager.setVisibility(model.loading ? View.INVISIBLE : View.VISIBLE);
        if (!model.loading && !model.needsLogin && !model.error.isEmpty())
            pageError.show(model.error, model::refresh, !model.reviews.isEmpty());
        else pageError.hide();
        revealHighlightedEntry();
        if (model.notice != null) { Toast.makeText(this, model.notice, Toast.LENGTH_SHORT).show(); model.notice = null; }
        syncDialog();
    }
    private void revealHighlightedEntry() {
        if (highlighted || !model.loaded || model.loading || isDestroyed()) return;
        for (int page = 0; page < adapters.length; page++) {
            int position = adapters[page].highlightedPosition();
            if (position < 0) continue;
            pager.setCurrentItem(page, false);
            if (lists[page] != null) {
                ((LinearLayoutManager) lists[page].getLayoutManager()).scrollToPositionWithOffset(position, 0);
                highlighted = true;
            }
            return;
        }
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
        out.putInt("review_page", pager.getCurrentItem());
        for (int page = 0; page < lists.length; page++) out.putParcelable("review_scroll_" + page,
                lists[page] == null ? scrollStates[page] : lists[page].getLayoutManager().onSaveInstanceState());
        super.onSaveInstanceState(out);
    }
    @Override protected void onDestroy() {
        if (tabsMediator != null) tabsMediator.detach();
        super.onDestroy();
    }
    public boolean isHighlighted(ReviewEntry entry) {
        int review = getIntent().getIntExtra(EXTRA_REVIEW_ID, 0), work = getIntent().getIntExtra(EXTRA_WORK_ID, 0);
        return review > 0 ? entry.id == review : work > 0 && entry.workId == work;
    }
}
