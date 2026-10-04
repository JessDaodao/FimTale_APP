package com.app.fimtale;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.fimtale.adapter.LoadingCardAdapter;
import com.app.fimtale.adapter.SearchHistoryAdapter;
import com.app.fimtale.adapter.TopicAdapter;
import com.app.fimtale.model.Topic;
import com.app.fimtale.model.TopicListResponse;
import com.app.fimtale.model.TopicViewItem;
import com.app.fimtale.network.ApiErrors;
import com.app.fimtale.network.RetrofitClient;
import com.app.fimtale.network.SearchQuery;
import com.app.fimtale.ui.ShimmerSkeletonView;
import com.app.fimtale.ui.SkeletonRefreshLayout;
import com.app.fimtale.utils.UserPreferences;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Full-page work search with its own query, history and result state. */
public class SearchActivity extends AppCompatActivity {
    public static final String EXTRA_QUERY = "query";

    private MaterialToolbar toolbar;
    private TextInputEditText searchInput;
    private MaterialButton searchButton;
    private SkeletonRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private RecyclerView historyRecyclerView;
    private View historyPanel;
    private MaterialButton clearHistoryButton;
    private ShimmerSkeletonView loadingSkeleton;
    private TextView loadingStatus;
    private LoadingCardAdapter loadingFooter;
    private TopicAdapter topicAdapter;
    private SearchHistoryAdapter historyAdapter;
    private final List<TopicViewItem> topicItems = new ArrayList<>();

    private Call<TopicListResponse> topicsCall;
    private int currentPage = 1;
    private int totalPages = 1;
    private boolean isLoading;
    private String currentSortBy = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.colorBackground, typedValue, true);
        getWindow().setStatusBarColor(typedValue.data);

        setupViews();
        String initialQuery = getIntent().getStringExtra(EXTRA_QUERY);
        if (initialQuery != null && !initialQuery.trim().isEmpty()) {
            searchInput.setText(initialQuery.trim());
            searchInput.setSelection(searchInput.length());
            submitSearch();
        } else {
            showHistory();
        }
    }

    private void setupViews() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        searchInput = findViewById(R.id.searchInput);
        searchButton = findViewById(R.id.searchButton);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        recyclerView = findViewById(R.id.recyclerView);
        historyRecyclerView = findViewById(R.id.historyRecyclerView);
        historyPanel = findViewById(R.id.historyPanel);
        clearHistoryButton = findViewById(R.id.clearHistoryButton);
        loadingSkeleton = findViewById(R.id.loadingSkeleton);
        loadingStatus = findViewById(R.id.loadingStatus);
        loadingSkeleton.setSkeletonLayout(ShimmerSkeletonView.Layout.ARTICLES);

        topicAdapter = new TopicAdapter(topicItems);
        loadingFooter = new LoadingCardAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(new ConcatAdapter(topicAdapter, loadingFooter));
        recyclerView.setItemAnimator(null);

        historyAdapter = new SearchHistoryAdapter(new ArrayList<>(), new SearchHistoryAdapter.OnHistoryClickListener() {
            @Override public void onHistoryClick(String query) {
                searchInput.setText(query);
                searchInput.setSelection(searchInput.length());
                submitSearch();
            }

            @Override public void onDeleteClick(String query) {
                UserPreferences.removeSearchHistoryItem(SearchActivity.this, query);
                showHistory();
            }
        });
        historyRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        historyRecyclerView.setAdapter(historyAdapter);

        searchButton.setOnClickListener(v -> submitSearch());
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                submitSearch();
                return true;
            }
            return false;
        });
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.toString().trim().isEmpty()) showHistory();
                else {
                    historyPanel.setVisibility(View.GONE);
                    loadingStatus.setVisibility(View.GONE);
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        clearHistoryButton.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle("确认清除")
                .setMessage("是否清除所有搜索历史？")
                .setPositiveButton("清除", (dialog, which) -> {
                    UserPreferences.clearSearchHistory(this);
                    showHistory();
                })
                .setNegativeButton("取消", null)
                .show());
        loadingStatus.setOnClickListener(v -> submitSearch());
        swipeRefreshLayout.setOnRefreshListener(() -> {
            swipeRefreshLayout.setRefreshing(false);
            if (!searchInput.getText().toString().trim().isEmpty()) loadTopics(1);
            else showHistory();
        });

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView view, int dx, int dy) {
                if (dy <= 0 || isLoading || currentPage >= totalPages) return;
                LinearLayoutManager manager = (LinearLayoutManager) view.getLayoutManager();
                if (manager != null && manager.findLastVisibleItemPosition() >= manager.getItemCount() - 3) {
                    view.post(() -> {
                        if (!isFinishing() && !isDestroyed() && !isLoading && currentPage < totalPages) {
                            loadTopics(currentPage + 1);
                        }
                    });
                }
            }
        });
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_search, menu);
        return true;
    }

    @Override public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_filter) {
            showFilterDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void submitSearch() {
        String query = searchInput.getText() == null ? "" : searchInput.getText().toString().trim();
        if (query.isEmpty()) {
            searchInput.setError("请输入搜索内容");
            showHistory();
            return;
        }
        searchInput.setError(null);
        UserPreferences.saveSearchHistory(this, query);
        currentPage = 1;
        loadTopics(1);
        searchInput.clearFocus();
    }

    private void showHistory() {
        if (topicsCall != null) { topicsCall.cancel(); topicsCall = null; }
        isLoading = false;
        loadingSkeleton.setVisibility(View.GONE);
        loadingFooter.setLoading(false);
        recyclerView.setVisibility(View.GONE);
        List<String> history = UserPreferences.getSearchHistory(this);
        historyAdapter.updateData(history);
        historyPanel.setVisibility(history.isEmpty() ? View.GONE : View.VISIBLE);
        loadingStatus.setText("输入关键词开始搜索");
        loadingStatus.setVisibility(history.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void loadTopics(int page) {
        String query = searchInput.getText() == null ? "" : searchInput.getText().toString().trim();
        if (query.isEmpty()) { showHistory(); return; }
        if (isLoading && page != 1) return;
        if (topicsCall != null) topicsCall.cancel();
        isLoading = true;
        final int requestedPage = page;
        historyPanel.setVisibility(View.GONE);
        loadingStatus.setVisibility(View.GONE);
        loadingSkeleton.setVisibility(page == 1 ? View.VISIBLE : View.GONE);
        loadingFooter.setLoading(page > 1);
        recyclerView.setVisibility(page == 1 ? View.INVISIBLE : View.VISIBLE);

        topicsCall = RetrofitClient.getInstance().getTopicList(page,
                SearchQuery.keywords(query), SearchQuery.rank(currentSortBy));
        topicsCall.enqueue(new Callback<TopicListResponse>() {
            @Override public void onResponse(Call<TopicListResponse> call, Response<TopicListResponse> response) {
                if (isFinishing() || isDestroyed() || call.isCanceled() || call != topicsCall) return;
                if (response.isSuccessful() && response.body() != null) {
                    TopicListResponse data = response.body();
                    currentPage = requestedPage;
                    totalPages = Math.max(1, data.getTotalPage());
                    if (requestedPage == 1) topicItems.clear();
                    int start = topicItems.size();
                    List<Topic> topics = data.getTopicArray();
                    if (topics != null) for (Topic topic : topics) topicItems.add(new TopicViewItem(topic));
                    if (requestedPage == 1) topicAdapter.notifyDataSetChanged();
                    else topicAdapter.notifyItemRangeInserted(start, topicItems.size() - start);
                    finishLoading();
                    recyclerView.scrollToPosition(requestedPage == 1 ? 0 : start);
                    if (topicItems.isEmpty()) {
                        loadingStatus.setText("未找到搜索结果");
                        loadingStatus.setVisibility(View.VISIBLE);
                    }
                } else showLoadError(ApiErrors.message(response));
            }

            @Override public void onFailure(Call<TopicListResponse> call, Throwable t) {
                if (isFinishing() || isDestroyed() || call.isCanceled() || call != topicsCall) return;
                showLoadError("加载失败，请重试");
            }
        });
    }

    private void finishLoading() {
        isLoading = false;
        loadingSkeleton.setVisibility(View.GONE);
        loadingFooter.setLoading(false);
        recyclerView.setVisibility(View.VISIBLE);
        swipeRefreshLayout.setRefreshing(false);
    }

    private void showLoadError(String message) {
        finishLoading();
        if (topicItems.isEmpty()) {
            loadingStatus.setText("加载失败，点击重试");
            loadingStatus.setVisibility(View.VISIBLE);
        } else Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void showFilterDialog() {
        final String[] options = {"默认排序", "发表时间", "更新时间", "最后评论", "字数排序", "评论数排序", "阅读数排序", "总体评分"};
        final String[] values = {"", "created_at", "last_chapter_at", "commented_at", "count_character", "count_comment", "count_view", "wilson_score"};
        int checkedItem = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(currentSortBy)) { checkedItem = i; break; }
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("选择排序方式")
                .setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
                    currentSortBy = values[which];
                    dialog.dismiss();
                    if (!searchInput.getText().toString().trim().isEmpty()) loadTopics(1);
                }).show();
    }

    @Override protected void onDestroy() {
        if (topicsCall != null) { topicsCall.cancel(); topicsCall = null; }
        super.onDestroy();
    }
}
