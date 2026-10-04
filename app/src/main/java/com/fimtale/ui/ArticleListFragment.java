package com.fimtale.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ConcatAdapter;
import com.fimtale.adapter.LoadingCardAdapter;

import com.fimtale.R;
import com.fimtale.adapter.TopicAdapter;
import com.fimtale.model.Topic;
import com.fimtale.model.TopicListResponse;
import com.fimtale.model.TopicViewItem;
import com.fimtale.network.RetrofitClient;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ArticleListFragment extends Fragment {

    private static final String ARG_CATEGORY = "category";
    private String category;
    private TopicAdapter topicAdapter;
    private List<TopicViewItem> topicViewItemList = new ArrayList<>();
    private int currentPage = 1;
    private int totalPages = 1;
    private boolean isLoading = false;
    private RecyclerView recyclerView;
    private ShimmerSkeletonView loadingSkeleton;
    private LoadingCardAdapter loadingFooter;
    private Call<TopicListResponse> topicsCall;
    private TextView errorTextView;

    public static ArticleListFragment newInstance(String category) {
        ArticleListFragment fragment = new ArticleListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CATEGORY, category);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            category = getArguments().getString(ARG_CATEGORY);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_article_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (view instanceof RecyclerView) {
            recyclerView = (RecyclerView) view;
        } else {
            recyclerView = view.findViewById(R.id.recycler_view);
        }
        loadingSkeleton = view.findViewById(R.id.loadingSkeleton);
        errorTextView = view.findViewById(R.id.errorTextView);
        errorTextView.setOnClickListener(v -> { currentPage = 1; loadTopics(); });

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        topicAdapter = new TopicAdapter(topicViewItemList);
        loadingFooter = new LoadingCardAdapter();
        recyclerView.setAdapter(new ConcatAdapter(topicAdapter, loadingFooter));
        recyclerView.setItemAnimator(null);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
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
                                if (getView() != null && !isLoading && currentPage < totalPages) {
                                    currentPage++; loadTopics();
                                }
                            });
                        }
                    }
                }
            }
        });

        currentPage = 1;
        loadTopics();
    }

    private void loadTopics() {
        if (isLoading) return;
        isLoading = true;
        final int requestedPage = currentPage;
        errorTextView.setVisibility(View.GONE);
        loadingSkeleton.setVisibility(requestedPage == 1 ? View.VISIBLE : View.GONE);
        loadingFooter.setLoading(requestedPage > 1);
        recyclerView.setVisibility(requestedPage == 1 ? View.INVISIBLE : View.VISIBLE);
        topicsCall = RetrofitClient.getInstance().getTopicList(requestedPage, null, null);
        topicsCall.enqueue(new Callback<TopicListResponse>() {
            @Override public void onResponse(Call<TopicListResponse> call, Response<TopicListResponse> response) {
                if (!isAdded() || getView() == null || call.isCanceled() || call != topicsCall) return;
                if (response.isSuccessful() && response.body() != null) {
                    TopicListResponse data = response.body();
                    totalPages = data.getTotalPage();
                    if (requestedPage == 1) topicViewItemList.clear();
                    int start = topicViewItemList.size();
                    if (data.getTopicArray() != null) for (Topic topic : data.getTopicArray()) topicViewItemList.add(new TopicViewItem(topic));
                    if (requestedPage == 1) topicAdapter.notifyDataSetChanged();
                    else topicAdapter.notifyItemRangeInserted(start, topicViewItemList.size() - start);
                    finishLoading();
                    if (topicViewItemList.isEmpty()) {
                        errorTextView.setText("暂无文章，点击刷新"); errorTextView.setVisibility(View.VISIBLE);
                    }
                } else showError(requestedPage);
            }
            @Override public void onFailure(Call<TopicListResponse> call, Throwable t) {
                if (!isAdded() || getView() == null || call.isCanceled() || call != topicsCall) return;
                showError(requestedPage);
            }
        });
    }
    private void finishLoading() {
        isLoading = false;
        loadingSkeleton.setVisibility(View.GONE); loadingFooter.setLoading(false);
        recyclerView.setVisibility(View.VISIBLE);
    }
    private void showError(int requestedPage) {
        currentPage = Math.max(1, requestedPage - 1); finishLoading();
        if (topicViewItemList.isEmpty()) {
            errorTextView.setText("加载失败，点击重试"); errorTextView.setVisibility(View.VISIBLE);
        } else android.widget.Toast.makeText(getContext(), "加载失败，请重试", android.widget.Toast.LENGTH_SHORT).show();
    }
    @Override public void onDestroyView() {
        if (topicsCall != null) { topicsCall.cancel(); topicsCall = null; }
        isLoading = false;
        super.onDestroyView();
    }
}
