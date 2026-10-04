package com.app.fimtale;

import android.animation.ObjectAnimator;
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
import com.app.fimtale.adapter.LoadingCardAdapter;
import com.app.fimtale.ui.ShimmerSkeletonView;
import com.app.fimtale.adapter.TopicAdapter;
import com.app.fimtale.model.FavoritesResponse;
import com.app.fimtale.model.Topic;
import com.app.fimtale.model.TopicViewItem;
import com.app.fimtale.network.RetrofitClient;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FavoritesActivity extends AppCompatActivity {

    private TopicAdapter adapter;
    private RecyclerView recyclerView;
    private ShimmerSkeletonView loadingSkeleton;
    private LoadingCardAdapter loadingFooter;
    private TextView loadingStatus;
    private Call<FavoritesResponse> activeCall;
    private MaterialCardView toolbarContainer;
    private boolean isToolbarElevated = false;
    private ObjectAnimator elevationAnimator;
    private List<TopicViewItem> topics = new ArrayList<>();
    private int currentPage = 1;
    private int totalPages = 1;
    private boolean isLoading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorites);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        toolbarContainer = findViewById(R.id.toolbarContainer);
        loadingSkeleton = findViewById(R.id.loadingSkeleton);
        loadingSkeleton.setSkeletonLayout(ShimmerSkeletonView.Layout.ARTICLES);
        loadingFooter = new LoadingCardAdapter();
        loadingStatus = findViewById(R.id.loadingStatus);
        loadingStatus.setOnClickListener(v -> loadFavorites(1));

        recyclerView = findViewById(R.id.recyclerView);
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
                                if (!isFinishing() && !isDestroyed() && !isLoading && currentPage < totalPages) loadFavorites(currentPage + 1);
                            });
                        }
                    }
                }
            }
        });

        adapter = new TopicAdapter(topics);
        recyclerView.setAdapter(new ConcatAdapter(adapter, loadingFooter));
        recyclerView.setItemAnimator(null);

        loadFavorites(1);
    }

    private void loadFavorites(int page) {
        if (isLoading) return;
        isLoading = true;
        loadingStatus.setVisibility(View.GONE);
        loadingSkeleton.setVisibility(page == 1 ? View.VISIBLE : View.GONE);
        loadingFooter.setLoading(page > 1);
        recyclerView.setVisibility(page == 1 ? View.INVISIBLE : View.VISIBLE);
        activeCall = RetrofitClient.getInstance().getFavorites(page);
        activeCall.enqueue(new Callback<FavoritesResponse>() {
            @Override public void onResponse(Call<FavoritesResponse> call, Response<FavoritesResponse> response) {
                if (isFinishing() || isDestroyed() || call.isCanceled() || call != activeCall) return;
                if (response.isSuccessful() && response.body() != null) {
                    FavoritesResponse data = response.body();
                    if (page == 1) topics.clear();
                    currentPage = page;
                    totalPages = data.getTotalPage();
                    int start = topics.size();
                    if (data.getTopicArray() != null) {
                        for (Topic topic : data.getTopicArray()) topics.add(new TopicViewItem(topic));
                    }
                    if (page == 1) adapter.notifyDataSetChanged();
                    else adapter.notifyItemRangeInserted(start, topics.size() - start);
                    finishLoading();
                    if (page == 1) recyclerView.scrollToPosition(0);
                    if (adapter.getItemCount() == 0) {
                        loadingStatus.setText("暂无收藏，点击刷新");
                        loadingStatus.setVisibility(View.VISIBLE);
                    }
                } else showLoadError(com.app.fimtale.network.ApiErrors.message(response));
            }
            @Override public void onFailure(Call<FavoritesResponse> call, Throwable t) {
                if (isFinishing() || isDestroyed() || call.isCanceled() || call != activeCall) return;
                showLoadError("加载失败，请重试");
            }
        });
    }

    private void finishLoading() {
        isLoading = false;
        loadingSkeleton.setVisibility(View.GONE);
        loadingFooter.setLoading(false);
        recyclerView.setVisibility(View.VISIBLE);
    }

    private void showLoadError(String message) {
        finishLoading();
        if (adapter.getItemCount() == 0) {
            loadingStatus.setText("加载失败，点击重试");
            loadingStatus.setVisibility(View.VISIBLE);
        } else Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override protected void onDestroy() {
        if (activeCall != null) { activeCall.cancel(); activeCall = null; }
        if (elevationAnimator != null) elevationAnimator.cancel();
        super.onDestroy();
    }
}
