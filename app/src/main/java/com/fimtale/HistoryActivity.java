package com.fimtale;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;
import androidx.recyclerview.widget.ConcatAdapter;
import com.fimtale.adapter.LoadingCardAdapter;
import com.fimtale.ui.ShimmerSkeletonView;
import com.fimtale.adapter.HistoryAdapter;
import com.fimtale.model.HistoryResponse;
import com.fimtale.network.RetrofitClient;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HistoryActivity extends AppCompatActivity {
    private com.fimtale.ui.PageErrorView pageError;

    private HistoryAdapter adapter;
    private RecyclerView recyclerView;
    private ShimmerSkeletonView loadingSkeleton;
    private LoadingCardAdapter loadingFooter;
    private TextView loadingStatus;
    private Call<HistoryResponse> activeCall;
    private MaterialCardView toolbarContainer;
    private boolean isToolbarElevated = false;
    private ObjectAnimator elevationAnimator;
    private int currentPage = 1;
    private int totalPages = 1;
    private boolean isLoading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        toolbarContainer = findViewById(R.id.toolbarContainer);
        loadingSkeleton = findViewById(R.id.loadingSkeleton);
        loadingSkeleton.setSkeletonLayout(ShimmerSkeletonView.Layout.HISTORY);
        loadingFooter = new LoadingCardAdapter(ShimmerSkeletonView.Layout.HISTORY_ROW);
        loadingStatus = findViewById(R.id.loadingStatus);
        loadingStatus.setOnClickListener(v -> loadHistory(1));

        recyclerView = findViewById(R.id.recyclerView);
        pageError = com.fimtale.ui.PageErrorView.wrap(recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        float targetElevation = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics());
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                boolean shouldElevate = recyclerView.canScrollVertically(-1);

                if (shouldElevate != isToolbarElevated) {
                    isToolbarElevated = shouldElevate;

                    if (elevationAnimator != null && elevationAnimator.isRunning()) {
                        elevationAnimator.cancel();
                    }

                    float start = toolbarContainer.getCardElevation();
                    float end = shouldElevate ? targetElevation : 0;

                    elevationAnimator = ObjectAnimator.ofFloat(toolbarContainer, "cardElevation", start, end);
                    elevationAnimator.setDuration(200);
                    elevationAnimator.start();
                }

                if (dy > 0) {
                    LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                    if (layoutManager != null) {
                        int visibleItemCount = layoutManager.getChildCount();
                        int totalItemCount = layoutManager.getItemCount();
                        int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                        if (!isLoading && (visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                                && firstVisibleItemPosition >= 0
                                && currentPage < totalPages) {
                            recyclerView.post(() -> {
                                if (!isFinishing() && !isDestroyed() && !isLoading && currentPage < totalPages) loadHistory(currentPage + 1);
                            });
                        }
                    }
                }
            }
        });

        adapter = new HistoryAdapter();
        adapter.setOnItemClickListener(topic -> {
            Intent intent = new Intent(HistoryActivity.this, ReaderActivity.class);
            intent.putExtra(ReaderActivity.EXTRA_CHAPTER_ID, topic.getChapterId());
            intent.putExtra(ReaderActivity.EXTRA_WORK_ID, topic.getWorkId());
            intent.putExtra(ReaderActivity.EXTRA_INITIAL_PROGRESS, topic.getProgress());
            startActivity(intent);
        });
        recyclerView.setAdapter(new ConcatAdapter(adapter, loadingFooter));
        recyclerView.setItemAnimator(null);
        com.fimtale.ui.PullToRefresh.attach(recyclerView, () -> loadHistory(1), () -> !isLoading);

        loadHistory(1);
    }

    private void loadHistory(int page) {
        pageError.hide();
        if (isLoading) return;
        isLoading = true;
        loadingStatus.setVisibility(View.GONE);
        loadingSkeleton.setVisibility(page == 1 ? View.VISIBLE : View.GONE);
        loadingFooter.setLoading(page > 1);
        recyclerView.setVisibility(page == 1 ? View.INVISIBLE : View.VISIBLE);
        activeCall = RetrofitClient.getInstance().getHistory(page);
        activeCall.enqueue(new Callback<HistoryResponse>() {
            @Override public void onResponse(Call<HistoryResponse> call, Response<HistoryResponse> response) {
                if (isFinishing() || isDestroyed() || call.isCanceled() || call != activeCall) return;
                if (response.isSuccessful() && response.body() != null) {
                    currentPage = page;
                    totalPages = response.body().getTotalPage();
                    java.util.List<HistoryResponse.HistoryTopic> history = response.body().getHistoryTopics();
                    if (history == null) history = new java.util.ArrayList<>();
                    if (page == 1) adapter.setHistoryTopics(new java.util.ArrayList<>(history));
                    else adapter.addHistoryTopics(history);
                    finishLoading();
                    if (page == 1) recyclerView.scrollToPosition(0);
                    if (adapter.getItemCount() == 0) {
                        loadingStatus.setText("暂无历史记录，点击刷新");
                        loadingStatus.setVisibility(View.VISIBLE);
                    }
                } else showLoadError(page, com.fimtale.network.ApiErrors.message(response));
            }
            @Override public void onFailure(Call<HistoryResponse> call, Throwable t) {
                if (isFinishing() || isDestroyed() || call.isCanceled() || call != activeCall) return;
                showLoadError(page, "暂时无法连接服务器，请检查网络后重试。");
            }
        });
    }

    private void finishLoading() {
        isLoading = false;
        loadingSkeleton.setVisibility(View.GONE);
        loadingFooter.setLoading(false);
        recyclerView.setVisibility(View.VISIBLE);
    }

    private void showLoadError(int page, String message) {
        finishLoading();
        loadingStatus.setVisibility(View.GONE);
        pageError.show(message, () -> loadHistory(page), adapter.getItemCount() > 0);
    }

    @Override protected void onDestroy() {
        if (activeCall != null) { activeCall.cancel(); activeCall = null; }
        if (elevationAnimator != null) elevationAnimator.cancel();
        super.onDestroy();
    }
}
