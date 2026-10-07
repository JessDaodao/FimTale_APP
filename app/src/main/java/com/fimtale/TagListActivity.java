package com.fimtale;

import com.fimtale.utils.MdiIcons;

import android.animation.ObjectAnimator;
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
import com.fimtale.adapter.TagAdapter;
import com.fimtale.model.TagInfo;
import com.fimtale.model.TagGroup;
import android.widget.TextView;
import androidx.recyclerview.widget.ConcatAdapter;
import com.fimtale.adapter.LoadingCardAdapter;
import com.fimtale.ui.ShimmerSkeletonView;
import com.fimtale.network.RetrofitClient;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TagListActivity extends AppCompatActivity {
    private com.fimtale.ui.PageErrorView pageError;

    private RecyclerView recyclerView;
    private TagAdapter adapter;
    private List<TagInfo> tagList = new ArrayList<>();
    private int currentPage = 1;
    private int totalPages = 1;
    private boolean isLoading = false;
    private MaterialCardView toolbarContainer;
    private boolean isToolbarElevated = false;
    private ObjectAnimator elevationAnimator;
    private ShimmerSkeletonView loadingSkeleton;
    private LoadingCardAdapter loadingFooter;
    private TextView loadingStatus;
    private Call<List<TagGroup>> tagsCall;
    private String keyword = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tag_list);

        loadingSkeleton = findViewById(R.id.loadingSkeleton);
        loadingSkeleton.setSkeletonLayout(ShimmerSkeletonView.Layout.TAGS);
        loadingFooter = new LoadingCardAdapter(ShimmerSkeletonView.Layout.TAG_ROW);
        loadingStatus = findViewById(R.id.loadingStatus);
        loadingStatus.setOnClickListener(v -> loadTags(1));

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeAsUpIndicator(MdiIcons.drawable(this, "arrow-left"));
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        toolbarContainer = findViewById(R.id.toolbarContainer);

        recyclerView = findViewById(R.id.recyclerView);
        pageError = com.fimtale.ui.PageErrorView.wrap(recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TagAdapter(tagList, this);
        recyclerView.setAdapter(new ConcatAdapter(adapter, loadingFooter));
        recyclerView.setItemAnimator(null);
        com.fimtale.ui.PullToRefresh.attach(recyclerView, () -> loadTags(1), () -> !isLoading);

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

                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (dy > 0 && !isLoading && layoutManager != null && layoutManager.findLastVisibleItemPosition() >= tagList.size() - 3) {
                    if (currentPage < totalPages) {
                        recyclerView.post(() -> {
                            if (!isFinishing() && !isDestroyed() && !isLoading && currentPage < totalPages) loadTags(currentPage + 1);
                        });
                    }
                }
            }
        });

        loadTags(1);
    }

    private void loadTags(int page) {
        pageError.hide();
        if (isLoading && page != 1) return;
        if (tagsCall != null) tagsCall.cancel();
        isLoading = true;
        loadingStatus.setVisibility(View.GONE);
        boolean replacing = page == 1 && !com.fimtale.ui.PullToRefresh.isRefreshing(recyclerView);
        loadingSkeleton.setVisibility(replacing ? View.VISIBLE : View.GONE);
        loadingFooter.setLoading(page > 1);
        recyclerView.setVisibility(replacing ? View.INVISIBLE : View.VISIBLE);
        tagsCall = RetrofitClient.getInstance().getTags(page, keyword);
        tagsCall.enqueue(new Callback<List<TagGroup>>() {
            @Override public void onResponse(Call<List<TagGroup>> call, Response<List<TagGroup>> response) {
                if (isFinishing() || isDestroyed() || call.isCanceled() || call != tagsCall) return;
                if (response.isSuccessful()) {
                    List<TagGroup> data = response.body() == null ? java.util.Collections.emptyList() : response.body();
                    if (page == 1) tagList.clear();
                    int start = tagList.size();
                    for (TagGroup group : data) {
                        if (group != null && group.tags != null) tagList.addAll(group.tags);
                    }
                    currentPage = page;
                    int count = tagList.size() - start;
                    totalPages = count >= 20 ? page + 1 : page;
                    if (page == 1) adapter.notifyDataSetChanged();
                    else adapter.notifyItemRangeInserted(start, count);
                    finishLoading();
                    if (page == 1) recyclerView.scrollToPosition(0);
                    if (tagList.isEmpty()) {
                        loadingStatus.setText("暂无标签，点击刷新");
                        loadingStatus.setVisibility(View.VISIBLE);
                    }
                } else showLoadError(page);
            }
            @Override public void onFailure(Call<List<TagGroup>> call, Throwable t) {
                if (isFinishing() || isDestroyed() || call.isCanceled() || call != tagsCall) return;
                showLoadError(page);
            }
        });
    }

    private void finishLoading() {
        com.fimtale.ui.PullToRefresh.finish(recyclerView);
        isLoading = false;
        loadingSkeleton.setVisibility(View.GONE);
        loadingFooter.setLoading(false);
        recyclerView.setVisibility(View.VISIBLE);
    }

    private void showLoadError(int page) {
        finishLoading();
        loadingStatus.setVisibility(View.GONE);
        pageError.show(null, () -> loadTags(page), adapter.getItemCount() > 0);
    }

    @Override protected void onDestroy() {
        if (tagsCall != null) { tagsCall.cancel(); tagsCall = null; }
        if (elevationAnimator != null) elevationAnimator.cancel();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MdiIcons.inflateMenu(this, getMenuInflater(), R.menu.menu_tag_list, menu);
        return true;
    }

    private void showFilterDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        View content = android.view.LayoutInflater.from(builder.getContext()).inflate(R.layout.dialog_text_input, null);
        com.google.android.material.textfield.TextInputLayout field = content.findViewById(R.id.dialogTextInputLayout);
        field.setHint("标签名称");
        android.widget.EditText input = content.findViewById(R.id.dialogTextInput);
        input.setText(keyword);
        builder.setTitle("搜索标签").setView(content)
                .setPositiveButton("搜索", (dialog, which) -> {
                    keyword = input.getText().toString().trim(); currentPage = 1; totalPages = 1;
                    tagList.clear(); adapter.notifyDataSetChanged(); loadTags(1);
                }).setNegativeButton("取消", null).show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == R.id.action_filter) {
            showFilterDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
