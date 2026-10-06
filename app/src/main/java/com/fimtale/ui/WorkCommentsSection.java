package com.fimtale.ui;

import android.graphics.Rect;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.fimtale.R;
import com.fimtale.adapter.CommentAdapter;
import com.fimtale.model.WorkCommentsResponse;
import com.fimtale.network.ApiErrors;
import com.fimtale.network.RetrofitClient;
import java.util.ArrayList;
import java.util.function.IntConsumer;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Paged comments in the detail page's scroll content, loaded when they come into view. */
public final class WorkCommentsSection {
    private static final int PER_PAGE = 16;
    private final AppCompatActivity activity;
    private final int workId;
    private final IntConsumer countUpdated;
    private final View root, pager;
    private final NestedScrollView scroll;
    private final RecyclerView list;
    private final CommentAdapter adapter;
    private final ShimmerSkeletonView skeleton;
    private final TextView status, pageLabel, title, sort;
    private final View previous, next;
    private final Rect visibleBounds = new Rect();
    private PageErrorView pageError;
    private Call<WorkCommentsResponse> activeCall;
    private int page = 1, totalPages = 1;
    private boolean started, loading, descending, closed;

    public WorkCommentsSection(AppCompatActivity activity, int workId, IntConsumer countUpdated) {
        this.activity = activity; this.workId = workId; this.countUpdated = countUpdated;
        root = activity.findViewById(R.id.workCommentsSection);
        scroll = activity.findViewById(R.id.scrollView);
        list = root.findViewById(R.id.commentsList);
        pageError = PageErrorView.wrap(list);
        list.setLayoutManager(new LinearLayoutManager(activity));
        adapter = new CommentAdapter(new ArrayList<>(), activity);
        list.setAdapter(adapter); list.setItemAnimator(null);
        skeleton = root.findViewById(R.id.commentsSkeleton);
        skeleton.setSkeletonLayout(ShimmerSkeletonView.Layout.COMMENTS);
        skeleton.setVisibility(View.INVISIBLE);
        status = root.findViewById(R.id.commentsStatus);
        title = root.findViewById(R.id.commentsTitle);
        pageLabel = root.findViewById(R.id.commentsPage);
        pager = root.findViewById(R.id.commentsPager);
        previous = root.findViewById(R.id.commentsPrevious);
        next = root.findViewById(R.id.commentsNext);
        sort = root.findViewById(R.id.commentsSort);
        previous.setOnClickListener(v -> changePage(page - 1));
        next.setOnClickListener(v -> changePage(page + 1));
        sort.setOnClickListener(v -> {
            descending = !descending;
            sort.setText(descending ? "从晚到早" : "从早到晚");
            changePage(1);
        });
    }

    public void setCount(int count) { title.setText("评论（" + count + "）"); }

    public void loadIfVisible() {
        if (!started && !closed && root.isShown() && root.getGlobalVisibleRect(visibleBounds)) load(1);
    }

    public void open() {
        if (closed) return;
        if (!started) load(1);
        scrollToComments();
    }

    public void refresh() {
        if (!closed) load(1);
    }

    /** After posting, match the website's newest-first view so the new comment is visible immediately. */
    public void refreshLatest() {
        if (closed) return;
        descending = true;
        sort.setText("从晚到早");
        load(1);
    }

    public int top() { return root.getTop(); }

    private void scrollToComments() {
        scroll.post(() -> { if (!closed) scroll.smoothScrollTo(0, root.getTop()); });
    }

    private void changePage(int requestedPage) {
        if (loading) return;
        load(requestedPage); scrollToComments();
    }

    private boolean valid(Call<?> call) {
        return !closed && !activity.isFinishing() && !activity.isDestroyed() && !call.isCanceled() && call == activeCall;
    }

    private void load(int requestedPage) {
        pageError.hide();
        if (closed || requestedPage < 1) return;
        if (activeCall != null) activeCall.cancel();
        started = true; loading = true; updatePager();
        status.setVisibility(View.GONE);
        list.setVisibility(View.GONE); skeleton.setVisibility(View.VISIBLE);
        activeCall = RetrofitClient.getInstance().getWorkComments(workId, requestedPage, PER_PAGE, "created_at", descending ? "desc" : "asc");
        activeCall.enqueue(new Callback<WorkCommentsResponse>() {
            @Override public void onResponse(Call<WorkCommentsResponse> call, Response<WorkCommentsResponse> response) {
                if (!valid(call)) return;
                if (response.isSuccessful() && response.body() != null) {
                    WorkCommentsResponse result = response.body();
                    totalPages = result.totalPages(PER_PAGE);
                    if (requestedPage > totalPages) { load(totalPages); return; }
                    page = requestedPage;
                    adapter.updateData(result.getItems());
                    loading = false; updatePager();
                    skeleton.setVisibility(View.GONE); list.setVisibility(View.VISIBLE);
                    countUpdated.accept(result.total);
                    if (result.getItems().isEmpty()) {
                        status.setText("暂无评论，点击刷新"); status.setVisibility(View.VISIBLE);
                        status.setOnClickListener(v -> load(1));
                    }
                } else showError(requestedPage, ApiErrors.message(response));
            }
            @Override public void onFailure(Call<WorkCommentsResponse> call, Throwable error) {
                if (valid(call)) showError(requestedPage, "评论加载失败");
            }
        });
    }

    private void showError(int requestedPage, String message) {
        loading = false; updatePager();
        skeleton.setVisibility(View.GONE);
        status.setVisibility(View.GONE); list.setVisibility(View.VISIBLE);
        pageError.show(message, () -> load(requestedPage), adapter.getItemCount() > 0);
    }

    private void updatePager() {
        pager.setVisibility(totalPages > 1 ? View.VISIBLE : View.GONE);
        previous.setEnabled(!loading && page > 1);
        next.setEnabled(!loading && page < totalPages);
        sort.setEnabled(!loading);
        pageLabel.setText(page + " / " + totalPages);
    }

    public void close() {
        closed = true;
        if (activeCall != null) activeCall.cancel();
        list.setAdapter(null);
    }
}
