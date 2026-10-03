package com.app.fimtale;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.app.fimtale.adapter.HistoryAdapter;
import com.app.fimtale.model.HistoryResponse;
import com.app.fimtale.model.TopicDetailResponse;
import com.app.fimtale.model.TopicInfo;
import com.app.fimtale.network.RetrofitClient;
import com.app.fimtale.utils.UserPreferences;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HistoryActivity extends AppCompatActivity {

    private HistoryAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private MaterialCardView toolbarContainer;
    private android.view.View loadingOverlay;
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
        loadingOverlay = findViewById(R.id.loadingOverlay);

        swipeRefresh = findViewById(R.id.swipeRefresh);
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
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
                            loadHistory(currentPage + 1);
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
        recyclerView.setAdapter(adapter);

        swipeRefresh.setEnabled(false);

        loadHistory(1);
    }

    private void loadHistory(int page) {
        if (isLoading) return;
        isLoading = true;
        swipeRefresh.setRefreshing(true);

        RetrofitClient.getInstance().getHistory(page).enqueue(new Callback<HistoryResponse>() {
            @Override
            public void onResponse(Call<HistoryResponse> call, Response<HistoryResponse> response) {
                isLoading = false;
                swipeRefresh.setRefreshing(false);
                hideLoadingOverlay();
                if (response.isSuccessful() && response.body() != null) {
                    currentPage = page;
                    totalPages = response.body().getTotalPage();

                    if (page == 1) {
                        adapter.setHistoryTopics(response.body().getHistoryTopics());
                        RecyclerView recyclerView = findViewById(R.id.recyclerView);
                        recyclerView.scrollToPosition(0);
                    } else {
                        adapter.addHistoryTopics(response.body().getHistoryTopics());
                    }
                } else {
                    Toast.makeText(HistoryActivity.this, com.app.fimtale.network.ApiErrors.message(response), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<HistoryResponse> call, Throwable t) {
                isLoading = false;
                swipeRefresh.setRefreshing(false);
                hideLoadingOverlay();
                Toast.makeText(HistoryActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void hideLoadingOverlay() {
        if (loadingOverlay != null && loadingOverlay.getVisibility() == android.view.View.VISIBLE) {
            loadingOverlay.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction(() -> loadingOverlay.setVisibility(android.view.View.GONE))
                    .start();
        }
    }
}
