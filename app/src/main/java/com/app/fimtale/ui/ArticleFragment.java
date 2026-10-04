package com.app.fimtale.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.TextView;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ConcatAdapter;
import com.app.fimtale.adapter.LoadingCardAdapter;

import com.app.fimtale.R;
import com.app.fimtale.adapter.SearchHistoryAdapter;
import com.app.fimtale.adapter.TopicAdapter;
import com.app.fimtale.model.Topic;
import com.app.fimtale.model.TopicListResponse;
import com.app.fimtale.model.TopicViewItem;
import com.app.fimtale.network.RetrofitClient;
import com.app.fimtale.utils.UserPreferences;
import com.app.fimtale.utils.DialogHelper;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ArticleFragment extends Fragment {

    private TabLayout tabLayout;
    private SwipeRefreshLayout swipeRefreshLayout;
    private FrameLayout contentContainer;
    private ShimmerSkeletonView loadingSkeleton;
    private LoadingCardAdapter loadingFooter;
    private LinearLayout emptyStateLayout;
    private Button btnLogin;
    private TextView tvWhyHow;
    private TextView tvNoResults;
    private android.widget.PopupWindow historyPopupWindow;
    private SearchHistoryAdapter historyAdapter;
    
    private RecyclerView recyclerView;
    private TopicAdapter adapter;
    private List<TopicViewItem> dataList = new ArrayList<>();
    private int currentPage = 1;
    private int totalPages = 1;
    private boolean isLoading = false;
    private Call<TopicListResponse> topicsCall;
    private String currentQuery = null;
    private String currentSortBy = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_article, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tabLayout = view.findViewById(R.id.tabs);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        contentContainer = view.findViewById(R.id.content_container);
        loadingSkeleton = view.findViewById(R.id.loadingSkeleton);
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout);
        btnLogin = view.findViewById(R.id.btnLogin);
        tvWhyHow = view.findViewById(R.id.tvWhyHow);
        tvNoResults = view.findViewById(R.id.tvNoResults);

        tabLayout.addTab(tabLayout.newTab().setText("全部"));
        tabLayout.setVisibility(View.GONE);

        setupRecyclerView();
        setupSwipeRefresh();
        setupEmptyState();

        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.article_menu, menu);
                
                MenuItem searchItem = menu.findItem(R.id.action_search);
                androidx.appcompat.widget.SearchView searchView = (androidx.appcompat.widget.SearchView) searchItem.getActionView();
                
                searchView.setQueryHint("搜索文章...");
                
                if (currentQuery != null && !currentQuery.isEmpty()) {
                    searchItem.expandActionView();
                    searchView.setQuery(currentQuery, false);
                    searchView.clearFocus();
                }
                
                searchView.setOnQueryTextListener(new androidx.appcompat.widget.SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        currentQuery = query;
                        currentPage = 1;
                        
                        UserPreferences.saveSearchHistory(getContext(), query);
                        
                        loadTopics();
                        searchView.clearFocus();
                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        if (newText.isEmpty()) {
                            showSearchHistoryPopup(searchView, searchView);
                        } else {
                            if (historyPopupWindow != null) {
                                historyPopupWindow.dismiss();
                            }
                        }
                        return true;
                    }
                });
                
                searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                    @Override
                    public boolean onMenuItemActionExpand(MenuItem item) {
                        searchView.post(searchView::clearFocus);
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            showSearchHistoryPopup(searchView, searchView);
                        }, 100);
                        return true;
                    }

                    @Override
                    public boolean onMenuItemActionCollapse(MenuItem item) {
                        if (currentQuery != null) {
                            currentQuery = null;
                            currentPage = 1;
                            
                            loadTopics();
                        }
                        return true;
                    }
                });
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                if (menuItem.getItemId() == R.id.action_filter) {
                    showFilterDialog();
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);

        loadContent();
    }

    private void showSearchHistoryPopup(View anchorView, androidx.appcompat.widget.SearchView searchView) {
        List<String> history = UserPreferences.getSearchHistory(getContext());
        if (history.isEmpty()) return;

        if (historyPopupWindow != null && historyPopupWindow.isShowing()) {
            historyAdapter.updateData(history);
            return;
        }

        View popupView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_search_history, null);
        RecyclerView historyRecyclerView = popupView.findViewById(R.id.historyRecyclerView);
        TextView tvClearHistory = popupView.findViewById(R.id.tvClearHistory);

        historyAdapter = new SearchHistoryAdapter(history, new SearchHistoryAdapter.OnHistoryClickListener() {
            @Override
            public void onHistoryClick(String query) {
                searchView.setQuery(query, true);
                historyPopupWindow.dismiss();
            }

            @Override
            public void onDeleteClick(String query) {
                UserPreferences.removeSearchHistoryItem(getContext(), query);
                List<String> updatedHistory = UserPreferences.getSearchHistory(getContext());
                if (updatedHistory.isEmpty()) {
                    historyPopupWindow.dismiss();
                } else {
                    historyAdapter.updateData(updatedHistory);
                }
            }
        });

        historyRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        historyRecyclerView.setAdapter(historyAdapter);

        tvClearHistory.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("确认清除")
                    .setMessage("是否清除所有搜索历史？")
                    .setPositiveButton("清除", (dialog, which) -> {
                        UserPreferences.clearSearchHistory(getContext());
                        historyPopupWindow.dismiss();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        historyPopupWindow = new android.widget.PopupWindow(popupView, 
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        
        historyPopupWindow.setElevation(10f);
        historyPopupWindow.setOutsideTouchable(true);
        historyPopupWindow.showAsDropDown(anchorView);
    }

    private void setupEmptyState() {
        btnLogin.setOnClickListener(v -> {
            DialogHelper.openLogin(requireContext());
        });
        tvWhyHow.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(getContext(), com.app.fimtale.HelpActivity.class);
            startActivity(intent);
        });
    }

    private void loadContent() {
        emptyStateLayout.setVisibility(View.GONE);
        swipeRefreshLayout.setVisibility(View.VISIBLE);
        swipeRefreshLayout.setEnabled(true);
        currentPage = 1;
        loadTopics();
    }

    private void setupRecyclerView() {
        recyclerView = new RecyclerView(getContext());
        recyclerView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.MATCH_PARENT));
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        int padding = (int)(8 * getResources().getDisplayMetrics().density);
        recyclerView.setPadding(padding, 0, padding, 0);
        recyclerView.setClipToPadding(false);
        recyclerView.setVisibility(View.GONE);
        
        adapter = new TopicAdapter(dataList);
        loadingFooter = new LoadingCardAdapter();
        recyclerView.setAdapter(new ConcatAdapter(adapter, loadingFooter));
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
        contentContainer.addView(recyclerView, 0);
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnChildScrollUpCallback((parent, child) -> recyclerView.canScrollVertically(-1));
        swipeRefreshLayout.setOnRefreshListener(() -> {
            swipeRefreshLayout.setRefreshing(false);
            currentPage = 1;
            loadTopics();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (emptyStateLayout != null && emptyStateLayout.getVisibility() == View.VISIBLE ) {
            loadContent();
        }
    }

    private void showFilterDialog() {
        final String[] options = {"默认排序", "发表时间", "更新时间", "最后评论", "字数排序", "评论数排序", "阅读数排序", "总体评分"};
        final String[] values = {"", "created_at", "last_chapter_at", "commented_at", "count_character", "count_comment", "count_view", "wilson_score"};
        
        int checkedItem = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(currentSortBy)) {
                checkedItem = i;
                break;
            }
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("选择排序方式")
                .setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
                    currentSortBy = values[which];
                    dialog.dismiss();
                    currentPage = 1;
                    loadTopics();
                })
                .show();
    }

    private void loadTopics() {
        if (isLoading) {
            if (currentPage != 1) return;
            if (topicsCall != null) topicsCall.cancel();
        }
        isLoading = true;
        final int requestedPage = currentPage;
        tvNoResults.setVisibility(View.GONE);
        swipeRefreshLayout.setRefreshing(false);
        loadingSkeleton.setVisibility(requestedPage == 1 ? View.VISIBLE : View.GONE);
        loadingFooter.setLoading(requestedPage > 1);
        recyclerView.setVisibility(requestedPage == 1 ? View.INVISIBLE : View.VISIBLE);

        topicsCall = RetrofitClient.getInstance().getTopicList(requestedPage,
                com.app.fimtale.network.SearchQuery.keywords(currentQuery), com.app.fimtale.network.SearchQuery.rank(currentSortBy));
        topicsCall.enqueue(new Callback<TopicListResponse>() {
            @Override public void onResponse(Call<TopicListResponse> call, Response<TopicListResponse> response) {
                if (!isAdded() || getView() == null || call.isCanceled() || call != topicsCall) return;
                if (response.isSuccessful() && response.body() != null) {
                    TopicListResponse data = response.body();
                    totalPages = data.getTotalPage();
                    if (requestedPage == 1) dataList.clear();
                    int start = dataList.size();
                    List<Topic> topics = data.getTopicArray();
                    if (topics != null) for (Topic topic : topics) dataList.add(new TopicViewItem(topic));
                    if (requestedPage == 1) {
                        adapter.notifyDataSetChanged(); recyclerView.scrollToPosition(0);
                    } else adapter.notifyItemRangeInserted(start, dataList.size() - start);
                    finishLoading();
                    tvNoResults.setText("未找到搜索结果");
                    tvNoResults.setVisibility(dataList.isEmpty() ? View.VISIBLE : View.GONE);
                } else loadFailed(requestedPage, com.app.fimtale.network.ApiErrors.message(response));
            }
            @Override public void onFailure(Call<TopicListResponse> call, Throwable t) {
                if (!isAdded() || getView() == null || call.isCanceled() || call != topicsCall) return;
                loadFailed(requestedPage, "加载失败，请重试");
            }
        });
    }
    private void finishLoading() {
        isLoading = false;
        loadingSkeleton.setVisibility(View.GONE); loadingFooter.setLoading(false);
        recyclerView.setVisibility(View.VISIBLE); swipeRefreshLayout.setRefreshing(false);
    }
    private void loadFailed(int requestedPage, String message) {
        currentPage = Math.max(1, requestedPage - 1);
        finishLoading();
        if (dataList.isEmpty()) {
            tvNoResults.setText("加载失败，点击重试"); tvNoResults.setVisibility(View.VISIBLE);
            tvNoResults.setOnClickListener(v -> { currentPage = 1; loadTopics(); });
        }
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }
    @Override public void onDestroyView() {
        if (topicsCall != null) { topicsCall.cancel(); topicsCall = null; }
        if (historyPopupWindow != null) { historyPopupWindow.dismiss(); historyPopupWindow = null; }
        isLoading = false;
        super.onDestroyView();
    }
}
