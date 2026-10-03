package com.app.fimtale;

import android.animation.ObjectAnimator;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.fimtale.adapter.TopicAdapter;
import com.app.fimtale.model.TopicListResponse;
import com.app.fimtale.model.TagInfo;
import com.app.fimtale.model.Topic;
import com.app.fimtale.model.TopicViewItem;
import com.app.fimtale.network.RetrofitClient;
import com.app.fimtale.utils.UserPreferences;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TagArticlesActivity extends AppCompatActivity {
    public static final String EXTRA_WORK_TYPE = "work_type";
    private int workType;

    public static final String EXTRA_TAG_NAME = "tag_name";

    private AppBarLayout appBarLayout;
    private CollapsingToolbarLayout collapsingToolbarLayout;
    private MaterialToolbar toolbar;
    private MaterialCardView toolbarContainer;
    private RecyclerView recyclerView;
    private CircularProgressIndicator progressBar;
    private TopicAdapter topicAdapter;
    private List<TopicViewItem> topicViewItemList = new ArrayList<>();
    
    private String tagName;
    private int currentPage = 1;
    private int totalPages = 1;
    private boolean isLoading = false;
    private String currentSortBy = "";
    private TagInfo tagInfo;
    private MenuItem tagInfoMenuItem;

    private boolean isToolbarElevated = false;
    private ObjectAnimator elevationAnimator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tag_articles);

        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.colorBackground, typedValue, true);
        getWindow().setStatusBarColor(typedValue.data);

        tagName = getIntent().getStringExtra(EXTRA_TAG_NAME);
        workType = getIntent().getIntExtra(EXTRA_WORK_TYPE, 0);
        if (workType != 0) tagName = workType == 2 ? "图集" : "帖子";
        if (tagName == null) {
            finish();
            return;
        }

        setupViews();
        fetchTagTopics();
    }

    private void setupViews() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        appBarLayout = findViewById(R.id.app_bar);
        collapsingToolbarLayout = findViewById(R.id.toolbar_layout);
        toolbarContainer = findViewById(R.id.toolbarContainer);
        
        toolbar.setTitle("# " + tagName);

        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        topicAdapter = new TopicAdapter(topicViewItemList);
        recyclerView.setAdapter(topicAdapter);

        float targetElevation = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics());
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                boolean shouldElevate = recyclerView.computeVerticalScrollOffset() > 0;
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
                            currentPage++;
                            fetchTagTopics();
                        }
                    }
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_tag_articles, menu);
        tagInfoMenuItem = menu.findItem(R.id.action_tag_info);
        updateTagInfoMenuItemVisibility();
        return true;
    }

    private void updateTagInfoMenuItemVisibility() {
        if (tagInfoMenuItem != null) {
            tagInfoMenuItem.setVisible(tagInfo != null && tagInfo.getIntro() != null && !tagInfo.getIntro().isEmpty());
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_tag_info) {
            showTagInfoDialog();
            return true;
        } else if (item.getItemId() == R.id.action_filter) {
            showFilterDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
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

        new MaterialAlertDialogBuilder(this)
                .setTitle("选择排序方式")
                .setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
                    currentSortBy = values[which];
                    dialog.dismiss();
                    currentPage = 1;
                    fetchTagTopics();
                })
                .show();
    }

    private void showTagInfoDialog() {
        if (tagInfo == null || tagInfo.getIntro() == null || tagInfo.getIntro().isEmpty()) {
            Toast.makeText(this, "暂无详情介绍", Toast.LENGTH_SHORT).show();
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(tagInfo.getName())
                .setMessage(android.text.Html.fromHtml(tagInfo.getIntro(), android.text.Html.FROM_HTML_MODE_COMPACT))
                .setPositiveButton("确定", null)
                .show();
    }

    private void fetchTagTopics() {
        if (currentPage == 1 && workType == 0 && tagInfo == null) {
            RetrofitClient.getInstance().getTag(tagName).enqueue(new Callback<TagInfo>() {
                @Override public void onResponse(Call<TagInfo> call, Response<TagInfo> response) {
                    if (isFinishing() || isDestroyed()) return;
                    if (response.isSuccessful()) { tagInfo = response.body(); updateTagInfoMenuItemVisibility(); }
                }
                @Override public void onFailure(Call<TagInfo> call, Throwable t) {}
            });
        }
        if (isLoading) return;
        isLoading = true;

        if (currentPage == 1) {
            progressBar.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.INVISIBLE);
            appBarLayout.setVisibility(View.INVISIBLE);
        }

        (workType == 0
                ? RetrofitClient.getInstance().getTagTopics(tagName, currentPage, com.app.fimtale.network.SearchQuery.rank(currentSortBy))
                : RetrofitClient.getInstance().getTopicList(currentPage, com.app.fimtale.network.SearchQuery.type(workType), com.app.fimtale.network.SearchQuery.rank(currentSortBy))).enqueue(new Callback<TopicListResponse>() {
            @Override
            public void onResponse(@NonNull Call<TopicListResponse> call, @NonNull Response<TopicListResponse> response) {
                isLoading = false;
                progressBar.setVisibility(View.GONE);
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
                        recyclerView.setVisibility(View.VISIBLE);
                        appBarLayout.setVisibility(View.VISIBLE);
                        
                        recyclerView.setAlpha(0f);
                        appBarLayout.setAlpha(0f);
                        
                        recyclerView.animate().alpha(1f).setDuration(300).start();
                        appBarLayout.animate().alpha(1f).setDuration(300).start();
                    }
                    

                } else {
                    if (currentPage > 1) currentPage--;
                    Toast.makeText(TagArticlesActivity.this, "加载失败", Toast.LENGTH_SHORT).show();
                    recyclerView.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onFailure(@NonNull Call<TopicListResponse> call, @NonNull Throwable t) {
                isLoading = false;
                if (currentPage > 1) currentPage--;
                progressBar.setVisibility(View.GONE);
                Toast.makeText(TagArticlesActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                recyclerView.setVisibility(View.VISIBLE);
                appBarLayout.setVisibility(View.VISIBLE);
            }
        });
    }
}
