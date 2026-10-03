package com.app.fimtale;

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
import com.app.fimtale.adapter.TagAdapter;
import com.app.fimtale.model.TagInfo;
import com.app.fimtale.model.TagGroup;
import com.app.fimtale.network.FimTaleApiService;
import com.app.fimtale.network.RetrofitClient;
import com.app.fimtale.utils.UserPreferences;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TagListActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TagAdapter adapter;
    private List<TagInfo> tagList = new ArrayList<>();
    private int currentPage = 1;
    private int totalPages = 1;
    private boolean isLoading = false;
    private MaterialCardView toolbarContainer;
    private boolean isToolbarElevated = false;
    private ObjectAnimator elevationAnimator;
    private View loadingOverlay;
    private String keyword = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tag_list);

        loadingOverlay = findViewById(R.id.loadingOverlay);
        loadingOverlay.setVisibility(View.VISIBLE);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        toolbarContainer = findViewById(R.id.toolbarContainer);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TagAdapter(tagList, this);
        recyclerView.setAdapter(adapter);

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
                if (!isLoading && layoutManager != null && layoutManager.findLastVisibleItemPosition() >= tagList.size() - 3) {
                    if (currentPage < totalPages) {
                        currentPage++;
                        loadTags();
                    }
                }
            }
        });

        loadTags();
    }

    private void loadTags() {
        isLoading = true;
        FimTaleApiService apiService = RetrofitClient.getInstance();

        Call<List<TagGroup>> call = apiService.getTags(currentPage, keyword);
        call.enqueue(new Callback<List<TagGroup>>() {
            @Override
            public void onResponse(Call<List<TagGroup>> call, Response<List<TagGroup>> response) {
                isLoading = false;
                if (response.isSuccessful()) {
                    List<TagGroup> data = response.body() == null ? java.util.Collections.emptyList() : response.body();
                    int count = 0;
                    for (TagGroup group : data) {
                        if (group.tags != null) { tagList.addAll(group.tags); count += group.tags.size(); }
                    }
                    totalPages = count >= 20 ? currentPage + 1 : currentPage;
                    adapter.notifyDataSetChanged();
                } else {
                    if (currentPage > 1) currentPage--;
                    Toast.makeText(TagListActivity.this, "加载标签失败", Toast.LENGTH_SHORT).show();
                }
                hideLoadingOverlay();
            }

            @Override
            public void onFailure(Call<List<TagGroup>> call, Throwable t) {
                isLoading = false;
                if (currentPage > 1) currentPage--;
                Toast.makeText(TagListActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                hideLoadingOverlay();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_tag_list, menu);
        return true;
    }

    private void showFilterDialog() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setSingleLine(true); input.setText(keyword); input.setHint("标签名称");
        new MaterialAlertDialogBuilder(this).setTitle("搜索标签").setView(input)
                .setPositiveButton("搜索", (dialog, which) -> {
                    if (isLoading) return;
                    keyword = input.getText().toString().trim(); currentPage = 1;
                    tagList.clear(); adapter.notifyDataSetChanged(); loadTags();
                }).setNegativeButton("取消", null).show();
    }

    private void hideLoadingOverlay() {
        if (loadingOverlay.getVisibility() == View.VISIBLE) {
            loadingOverlay.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction(() -> loadingOverlay.setVisibility(View.GONE))
                    .start();
        }
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
