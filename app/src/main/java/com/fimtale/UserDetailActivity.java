package com.fimtale;

import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.text.TextUtils;
import android.graphics.drawable.Drawable;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import com.fimtale.ui.ShimmerSkeletonView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.TypedValue;
import android.content.res.ColorStateList;

import com.fimtale.adapter.TopicAdapter;
import com.fimtale.model.Topic;
import com.fimtale.model.TopicListResponse;
import com.fimtale.model.TopicViewItem;
import com.fimtale.model.UserDetailResponse;
import com.fimtale.network.RetrofitClient;
import com.fimtale.utils.UserPreferences;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.Map;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UserDetailActivity extends AppCompatActivity {

    public static final String EXTRA_USERNAME = "extra_username";

    private MaterialCardView imageContainer;
    private ImageView ivBackground;
    private ImageView ivAvatar;
    private TextView tvUsername;
    private TextView tvUserRole;
    private TextView tvLastSeen;
    private TextView tvIntro;
    private TextView tvFollowing;
    private TextView tvFollowers;
    private TextView tvTopics;
    private ChipGroup chipGroupBadges;
    private TextView tvMedalsTitle;
    private ChipGroup chipGroupMedals;
    private ShimmerSkeletonView loadingSkeleton, topicsSkeleton;
    private TextView loadError, topicsStatus;
    private Call<UserDetailResponse> profileCall;
    private Call<com.fimtale.model.UserWorksResponse> topicsCall;
    private CollapsingToolbarLayout collapsingToolbar;
    private MaterialCardView toolbarContainer;
    private Toolbar toolbar;
    private TextView tvToolbarTitle;
    private NestedScrollView scrollView;
    private boolean isToolbarElevated = false;
    private boolean isTitleVisible = false;
    private ObjectAnimator elevationAnimator;

    private RecyclerView rvUserTopics;
    private TopicAdapter topicAdapter;
    private java.util.List<TopicViewItem> topicList = new java.util.ArrayList<>();
    private TextView tvUserTopicsTitle;
    private int currentPage = 1;
    private int totalPages = 1;
    private boolean isLoading = false;
    private String currentUsername;
    private long editorVersion = com.fimtale.editor.EditorChanges.version();

    @Override protected void onResume() {
        super.onResume();
        if (editorVersion != com.fimtale.editor.EditorChanges.version()) {
            editorVersion = com.fimtale.editor.EditorChanges.version();
            loadUserTopics(currentUsername, 1);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_detail);

        String username = getIntent().getStringExtra(EXTRA_USERNAME);
        if (TextUtils.isEmpty(username)) {
            finish();
            return;
        }
        this.currentUsername = username;

        initView();
        loadData(username);
        loadUserTopics(username, 1);
    }

    private void initView() {
        toolbar = findViewById(R.id.toolbar);
        tvToolbarTitle = findViewById(R.id.tvToolbarTitle);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
        }

        collapsingToolbar = findViewById(R.id.collapsingToolbar);
        toolbarContainer = findViewById(R.id.toolbarContainer);
        scrollView = findViewById(R.id.scrollView);
        imageContainer = findViewById(R.id.imageContainer);
        ivBackground = findViewById(R.id.ivBackground);
        ivAvatar = findViewById(R.id.ivAvatar);
        tvUsername = findViewById(R.id.tvUsername);
        tvUserRole = findViewById(R.id.tvUserRole);
        tvLastSeen = findViewById(R.id.tvLastSeen);
        tvIntro = findViewById(R.id.tvIntro);
        tvFollowing = findViewById(R.id.tvFollowing);
        tvFollowers = findViewById(R.id.tvFollowers);
        tvTopics = findViewById(R.id.tvTopics);
        chipGroupBadges = findViewById(R.id.chipGroupBadges);
        tvMedalsTitle = findViewById(R.id.tvMedalsTitle);
        chipGroupMedals = findViewById(R.id.chipGroupMedals);
        loadingSkeleton = findViewById(R.id.profileLoadingSkeleton);
        loadingSkeleton.setSkeletonLayout(ShimmerSkeletonView.Layout.USER_DETAIL);
        topicsSkeleton = findViewById(R.id.userTopicsSkeleton);
        topicsSkeleton.setSkeletonLayout(ShimmerSkeletonView.Layout.CARD);
        loadError = findViewById(R.id.profileLoadError);
        loadError.setOnClickListener(v -> loadData(currentUsername));
        topicsStatus = findViewById(R.id.userTopicsStatus);
        rvUserTopics = findViewById(R.id.rvUserTopics);
        tvUserTopicsTitle = findViewById(R.id.tvUserTopicsTitle);

        rvUserTopics.setLayoutManager(new LinearLayoutManager(this));
        topicAdapter = new TopicAdapter(topicList);
        rvUserTopics.setAdapter(topicAdapter);
        rvUserTopics.setItemAnimator(null);

        float targetElevation = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics());
        float titleThreshold = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 240, getResources().getDisplayMetrics());
        
        scrollView.setOnScrollChangeListener(new NestedScrollView.OnScrollChangeListener() {
            @Override
            public void onScrollChange(NestedScrollView v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                boolean shouldElevate = v.canScrollVertically(-1);

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

                if (scrollY > titleThreshold) {
                    if (!isTitleVisible) {
                        isTitleVisible = true;
                        tvToolbarTitle.setText(currentUsername);
                        tvToolbarTitle.animate().alpha(1.0f).setDuration(200).start();
                    }
                } else {
                    if (isTitleVisible) {
                        isTitleVisible = false;
                        tvToolbarTitle.animate().alpha(0.0f).setDuration(200).start();
                    }
                }

                if (scrollY > oldScrollY && !v.canScrollVertically(1)) {
                    if (!isLoading && currentPage < totalPages) {
                        loadUserTopics(currentUsername, currentPage + 1);
                    }
                }
            }
        });
    }

    private void loadData(String username) {
        if (profileCall != null) profileCall.cancel();
        loadingSkeleton.setVisibility(View.VISIBLE);
        loadError.setVisibility(View.GONE);
        scrollView.setVisibility(View.INVISIBLE);
        profileCall = RetrofitClient.getInstance().getUserDetail(username);
        profileCall.enqueue(new Callback<UserDetailResponse>() {
            @Override
            public void onResponse(Call<UserDetailResponse> call, Response<UserDetailResponse> response) {
                if (isFinishing() || isDestroyed() || call.isCanceled() || call != profileCall) return;
                if (response.isSuccessful() && response.body() != null) {
                    UserDetailResponse data = response.body();
                    if (data.getId() > 0) {
                        bindData(data);
                        loadingSkeleton.setVisibility(View.GONE);
                        scrollView.setVisibility(View.VISIBLE);
                    } else {
                        showProfileLoadError();
                    }
                } else {
                    showProfileLoadError();
                }
            }

            @Override
            public void onFailure(Call<UserDetailResponse> call, Throwable t) {
                if (isFinishing() || isDestroyed() || call.isCanceled() || call != profileCall) return;
                showProfileLoadError();
            }
        });
    }

    private void showProfileLoadError() {
        loadingSkeleton.setVisibility(View.GONE);
        loadError.setVisibility(View.VISIBLE);
    }

    private void bindData(UserDetailResponse data) {
        UserDetailResponse info = data;

        tvUsername.setText(info.getUserName());
        tvUserRole.setText("LV." + info.getLevel());

        if (!TextUtils.isEmpty(info.getLastSeen())) {
            try {
                long timestamp = java.time.OffsetDateTime.parse(info.getLastSeen()).toInstant().toEpochMilli();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                tvLastSeen.setText("最后活动: " + sdf.format(new Date(timestamp)));
            } catch (Exception e) {
                tvLastSeen.setVisibility(View.GONE);
            }
        } else {
             tvLastSeen.setVisibility(View.GONE);
        }

        tvIntro.setText(info.getUserIntro());
        tvFollowing.setText(String.valueOf(info.getFollowing()));
        tvFollowers.setText(String.valueOf(info.getFollowers()));
        tvTopics.setText(String.valueOf(info.getTopics()));

        if (TextUtils.isEmpty(info.getBackground())) {
            imageContainer.setVisibility(View.GONE);
        } else {
            imageContainer.setVisibility(View.VISIBLE);
            Glide.with(this)
                 .load(info.getBackground())
                 .listener(new RequestListener<Drawable>() {
                     @Override
                     public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                         imageContainer.setVisibility(View.GONE);
                         return false;
                     }

                     @Override
                     public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                         return false;
                     }
                 })
                 .into(ivBackground);
        }

        String avatarUrl = info.getAvatar();
        Glide.with(this)
             .load(avatarUrl)
             .placeholder(R.drawable.ic_person)
             .into(ivAvatar);

        chipGroupBadges.removeAllViews();
        if (info.badges != null) {
            int bgColor = resolveThemeColor(com.google.android.material.R.attr.colorPrimaryContainer);
            int textColor = resolveThemeColor(com.google.android.material.R.attr.colorOnPrimaryContainer);
            for (UserDetailResponse.Badge badge : info.badges) {
                addChip(chipGroupBadges, badge.name, bgColor, textColor);
            }
        }

        chipGroupMedals.removeAllViews();
        if (info.medals != null && !info.medals.isEmpty()) {
            tvMedalsTitle.setVisibility(View.VISIBLE);
            for (UserDetailResponse.Medal medal : info.medals) {
                addMedalChip(chipGroupMedals, medal);
            }
        } else {
            tvMedalsTitle.setVisibility(View.GONE);
        }
    }

    private void addMedalChip(ChipGroup group, UserDetailResponse.Medal medal) {
        String medalName = medal.name;
        Chip chip = new Chip(this);
        chip.setText(""); 
        chip.setChipBackgroundColor(ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
        chip.setEnsureMinTouchTargetSize(false);
        chip.setChipMinHeight(0);
        chip.setChipStartPadding(0);
        chip.setChipEndPadding(0);
        chip.setTextStartPadding(0);
        chip.setTextEndPadding(0);
        chip.setCloseIconVisible(false);
        chip.setChipStrokeWidth(0);

        String medalUrl = com.fimtale.network.SiteUrls.media(medal.image);
        int iconSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 44, getResources().getDisplayMetrics());
        chip.setChipIconSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40, getResources().getDisplayMetrics()));

        Glide.with(this)
                .load(medalUrl)
                .override(iconSize, iconSize)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        chip.setText(medalName);
                        chip.setChipBackgroundColor(ColorStateList.valueOf(resolveThemeColor(com.google.android.material.R.attr.colorSecondaryContainer)));
                        chip.setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSecondaryContainer));
                        float padding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics());
                        chip.setChipStartPadding(padding);
                        chip.setChipEndPadding(padding);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        chip.setChipIcon(resource);
                        chip.setChipIconVisible(true);
                        return false;
                    }
                })
                .into(new com.bumptech.glide.request.target.CustomTarget<Drawable>() {
                    @Override
                    public void onResourceReady(@NonNull Drawable resource, @Nullable com.bumptech.glide.request.transition.Transition<? super Drawable> transition) {
                        chip.setChipIcon(resource);
                        chip.setChipIconVisible(true);
                    }
                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {}
                });

        group.addView(chip);
    }

    private void addChip(ChipGroup group, String text, int bgColor, int textColor) {
        Chip chip = new Chip(this);
        chip.setText(text);
        chip.setChipBackgroundColor(ColorStateList.valueOf(bgColor));
        chip.setTextColor(textColor);
        chip.setEnsureMinTouchTargetSize(false);
        chip.setElevation(0);
        chip.setChipMinHeight(0);
        float padding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics());
        chip.setChipStartPadding(padding);
        chip.setChipEndPadding(padding);
        chip.setTextStartPadding(padding);
        chip.setTextEndPadding(padding);
        chip.setCloseIconVisible(false);
        chip.setChipStrokeWidth(0);
        group.addView(chip);
    }

    private void loadUserTopics(String username, int page) {
        if (username == null) return;
        if (isLoading) {
            if (page != 1) return;
            if (topicsCall != null) topicsCall.cancel();
        }
        isLoading = true;
        topicsStatus.setVisibility(View.GONE);
        tvUserTopicsTitle.setVisibility(View.VISIBLE);
        topicsSkeleton.setVisibility(View.VISIBLE);
        topicsCall = RetrofitClient.getInstance().getUserTopics(username, "work", page);
        topicsCall.enqueue(new Callback<com.fimtale.model.UserWorksResponse>() {
            @Override public void onResponse(Call<com.fimtale.model.UserWorksResponse> call, Response<com.fimtale.model.UserWorksResponse> response) {
                if (isFinishing() || isDestroyed() || call.isCanceled() || call != topicsCall) return;
                if (response.isSuccessful() && response.body() != null && response.body().content != null) {
                    TopicListResponse data = response.body().content;
                    if (page == 1) topicList.clear();
                    currentPage = page; totalPages = data.getTotalPage();
                    int start = topicList.size();
                    if (data.getTopicArray() != null) for (Topic topic : data.getTopicArray()) topicList.add(new TopicViewItem(topic));
                    if (page == 1) topicAdapter.notifyDataSetChanged();
                    else topicAdapter.notifyItemRangeInserted(start, topicList.size() - start);
                    finishTopicsLoading();
                    rvUserTopics.setVisibility(topicList.isEmpty() ? View.GONE : View.VISIBLE);
                    if (topicList.isEmpty()) {
                        topicsStatus.setText("暂无文章"); topicsStatus.setVisibility(View.VISIBLE);
                        topicsStatus.setOnClickListener(null);
                    }
                } else showTopicsLoadError(page);
            }
            @Override public void onFailure(Call<com.fimtale.model.UserWorksResponse> call, Throwable t) {
                if (isFinishing() || isDestroyed() || call.isCanceled() || call != topicsCall) return;
                showTopicsLoadError(page);
            }
        });
    }

    private void finishTopicsLoading() {
        isLoading = false;
        topicsSkeleton.setVisibility(View.GONE);
    }

    private void showTopicsLoadError(int page) {
        finishTopicsLoading();
        topicsStatus.setText("文章加载失败，点击重试"); topicsStatus.setVisibility(View.VISIBLE);
        topicsStatus.setOnClickListener(v -> loadUserTopics(currentUsername, page));
    }

    @Override protected void onDestroy() {
        if (profileCall != null) { profileCall.cancel(); profileCall = null; }
        if (topicsCall != null) { topicsCall.cancel(); topicsCall = null; }
        if (elevationAnimator != null) elevationAnimator.cancel();
        super.onDestroy();
    }

    private int resolveThemeColor(int attrRes) {
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(attrRes, typedValue, true);
        return typedValue.data;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
