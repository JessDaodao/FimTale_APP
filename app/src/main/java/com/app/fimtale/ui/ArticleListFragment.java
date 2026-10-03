package com.app.fimtale.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.fimtale.R;
import com.app.fimtale.adapter.TopicAdapter;
import com.app.fimtale.model.Topic;
import com.app.fimtale.model.TopicListResponse;
import com.app.fimtale.model.TopicViewItem;
import com.app.fimtale.network.RetrofitClient;
import com.app.fimtale.utils.UserPreferences;

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
    private ProgressBar progressBar;
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
        progressBar = view.findViewById(R.id.progressBar);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        topicAdapter = new TopicAdapter(topicViewItemList);
        recyclerView.setAdapter(topicAdapter);

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
                            currentPage++;
                            loadTopics();
                        }
                    }
                }
            }
        });

        loadTopics();
    }

    private void loadTopics() {
        if (isLoading) return;
        isLoading = true;

        if (currentPage == 1) {
            progressBar.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.INVISIBLE);
        }

        RetrofitClient.getInstance().getTopicList(currentPage, null, null).enqueue(new Callback<TopicListResponse>() {
            @Override
            public void onResponse(Call<TopicListResponse> call, Response<TopicListResponse> response) {
                isLoading = false;
                if (!isAdded()) return;

                if (response.isSuccessful() && response.body() != null) {
                    TopicListResponse data = response.body();
                    
                    totalPages = data.getTotalPage();
                    
                    List<Topic> topicList = data.getTopicArray();
                    
                    if (currentPage == 1) {
                        topicViewItemList.clear();
                    }

                    int startInsertPos = topicViewItemList.size();
                    if (topicList != null) {
                        List<TopicViewItem> newItems = topicList.stream().map(TopicViewItem::new).collect(Collectors.toList());
                        topicViewItemList.addAll(newItems);
                        if (currentPage == 1) {
                            topicAdapter.notifyDataSetChanged();
                        } else {
                            topicAdapter.notifyItemRangeInserted(startInsertPos, newItems.size());
                        }
                    }

                    if (currentPage == 1) {
                        progressBar.animate()
                                .alpha(0f)
                                .setDuration(300)
                                .withEndAction(() -> {
                                    progressBar.setVisibility(View.GONE);
                                    progressBar.setAlpha(1f);

                                    recyclerView.setAlpha(0f);
                                    recyclerView.setScaleX(0.9f);
                                    recyclerView.setScaleY(0.9f);
                                    recyclerView.setVisibility(View.VISIBLE);

                                    android.view.animation.PathInterpolator interpolator = new android.view.animation.PathInterpolator(1.00f, 0.00f, 0.28f, 1.00f);

                                    recyclerView.animate()
                                            .alpha(1f)
                                            .scaleX(1f)
                                            .scaleY(1f)
                                            .setInterpolator(interpolator)
                                            .setDuration(500)
                                            .start();
                                })
                                .start();
                    }

                } else {
                    if (currentPage > 1) currentPage--;
                    showError();
                }
            }

            @Override
            public void onFailure(Call<TopicListResponse> call, Throwable t) {
                isLoading = false;
                if (!isAdded()) return;
                if (currentPage > 1) currentPage--;
                showError();
            }
        });
    }

    private void showError() {
        if (currentPage == 1) {
            progressBar.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }
}
