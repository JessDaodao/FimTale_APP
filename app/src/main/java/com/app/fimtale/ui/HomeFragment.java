package com.app.fimtale.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ViewFlipper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.CompositePageTransformer;
import androidx.viewpager2.widget.MarginPageTransformer;
import androidx.viewpager2.widget.ViewPager2;

import com.app.fimtale.MainActivity;
import com.app.fimtale.R;
import com.app.fimtale.adapter.BannerAdapter;
import com.app.fimtale.adapter.TopicAdapter;
import com.app.fimtale.model.RecommendedTopic;
import com.app.fimtale.model.Tags;
import com.app.fimtale.model.Topic;
import com.app.fimtale.model.TopicViewItem;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

import com.app.fimtale.network.RetrofitClient;
import com.app.fimtale.utils.UserPreferences;
import com.app.fimtale.utils.DialogHelper;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeFragment extends Fragment {

    private SwipeRefreshLayout swipeRefreshLayout;
    private NestedScrollView scrollView;
    private LinearLayout contentLayout;
    private LinearLayout emptyStateLayout;
    private LinearLayout quickAccessLayout;
    private LinearLayout btnGallery;
    private LinearLayout btnPosts;
    private LinearLayout btnTags;
    private Button btnLogin;
    private TextView tvWhyHow;
    private TabLayout tabLayout;
    private ViewPager2 bannerViewPager;
    private RecyclerView recyclerViewHot, recyclerViewNew;
    private ViewFlipper viewFlipper;
    private ProgressBar progressBar;
    private TextView errorTextView;
    private Button viewMoreButton;
    private BannerAdapter bannerAdapter;
    private TopicAdapter adapterHot, adapterNew;
    private List<RecommendedTopic> bannerList = new ArrayList<>();
    private List<TopicViewItem> topicListHot = new ArrayList<>();
    private List<TopicViewItem> topicListNew = new ArrayList<>();
    private Timer bannerTimer;
    private Handler bannerHandler = new Handler(Looper.getMainLooper());
    private View rootView;
    private long editorVersion = com.app.fimtale.editor.EditorChanges.version();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (rootView == null) {
            rootView = inflater.inflate(R.layout.fragment_home, container, false);
        } else {
            ViewGroup parent = (ViewGroup) rootView.getParent();
            if (parent != null) {
                parent.removeView(rootView);
            }
        }
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        requireActivity().addMenuProvider(new androidx.core.view.MenuProvider() {
            @Override public void onCreateMenu(@NonNull android.view.Menu menu, @NonNull android.view.MenuInflater inflater) {
                inflater.inflate(R.menu.home_menu, menu);
            }
            @Override public boolean onMenuItemSelected(@NonNull android.view.MenuItem item) {
                if (item.getItemId() != R.id.action_publish) return false;
                startActivity(new Intent(requireContext(), com.app.fimtale.DraftsActivity.class)); return true;
            }
        }, getViewLifecycleOwner(), androidx.lifecycle.Lifecycle.State.RESUMED);

        if (scrollView != null) {
            return;
        }

        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        scrollView = view.findViewById(R.id.scrollView);
        contentLayout = view.findViewById(R.id.contentLayout);
        bannerViewPager = view.findViewById(R.id.bannerViewPager);
        quickAccessLayout = view.findViewById(R.id.quickAccessLayout);
        btnGallery = view.findViewById(R.id.btnGallery);
        btnPosts = view.findViewById(R.id.btnPosts);
        btnTags = view.findViewById(R.id.btnTags);
        recyclerViewHot = view.findViewById(R.id.recyclerViewHot);
        recyclerViewNew = view.findViewById(R.id.recyclerViewNew);
        viewFlipper = view.findViewById(R.id.viewFlipper);
        progressBar = view.findViewById(R.id.progressBar);
        errorTextView = view.findViewById(R.id.errorTextView);
        tabLayout = view.findViewById(R.id.tabLayout);
        viewMoreButton = view.findViewById(R.id.viewMoreButton);
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout);
        btnLogin = view.findViewById(R.id.btnLogin);
        tvWhyHow = view.findViewById(R.id.tvWhyHow);

        setupBannerViewPager();
        setupRecyclerView();
        setupTabLayout();
        setupSwipeRefresh();
        setupEmptyState();
        setupQuickAccess();

        loadContent();
    }

    private void setupQuickAccess() {
        btnGallery.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), com.app.fimtale.TagArticlesActivity.class);
            intent.putExtra(com.app.fimtale.TagArticlesActivity.EXTRA_WORK_TYPE, 2);
            startActivity(intent);
        });

        btnPosts.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), com.app.fimtale.TagArticlesActivity.class);
            intent.putExtra(com.app.fimtale.TagArticlesActivity.EXTRA_WORK_TYPE, 3);
            startActivity(intent);
        });

        btnTags.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), com.app.fimtale.TagListActivity.class);
            startActivity(intent);
        });
    }

    private void setupEmptyState() {
        btnLogin.setOnClickListener(v -> {
            DialogHelper.openLogin(requireContext());
        });
        tvWhyHow.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), com.app.fimtale.HelpActivity.class);
            startActivity(intent);
        });
    }

    private void loadContent() {
        emptyStateLayout.setVisibility(View.GONE);
        swipeRefreshLayout.setVisibility(View.VISIBLE);
        fetchHomePageData();
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setColorSchemeResources(R.color.md_theme_light_primary);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                ValueAnimator blurAnimator = ValueAnimator.ofFloat(0f, 50f);
                blurAnimator.setDuration(300);
                blurAnimator.addUpdateListener(animation -> {
                    float val = (float) animation.getAnimatedValue();
                    if (val > 0) {
                        scrollView.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(val, val, android.graphics.Shader.TileMode.CLAMP));
                    }
                });
                blurAnimator.start();
            }
            
            scrollView.animate()
                    .scaleX(0.9f)
                    .scaleY(0.9f)
                    .setDuration(300)
                    .start();

            fetchHomePageData(true);
        });
    }

    private void setupBannerViewPager() {
        bannerAdapter = new BannerAdapter(bannerList, topic -> {
            Intent intent = new Intent(getContext(), com.app.fimtale.TopicDetailActivity.class);
            intent.putExtra(com.app.fimtale.TopicDetailActivity.EXTRA_TOPIC_ID, topic.getId());
            startActivity(intent);
        });
        bannerViewPager.setAdapter(bannerAdapter);
        bannerViewPager.setClipToPadding(false);
        bannerViewPager.setClipChildren(false);
        bannerViewPager.setOffscreenPageLimit(3);
        CompositePageTransformer compositeTransformer = new CompositePageTransformer();
        compositeTransformer.addTransformer(new MarginPageTransformer(getResources().getDimensionPixelOffset(R.dimen.page_margin)));
        compositeTransformer.addTransformer((page, position) -> {
            View imageView = page.findViewById(R.id.bannerImageView);
            if (imageView != null) {
                int width = imageView.getWidth();
                imageView.setScaleX(1.4f);
                imageView.setScaleY(1.4f);
                imageView.setTranslationX(-position * width * 0.2f);
            }
        });
        bannerViewPager.setPageTransformer(compositeTransformer);
    }

    private void setupTabLayout() {
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int newPosition = tab.getPosition();
                int currentPosition = viewFlipper.getDisplayedChild();

                if (newPosition == currentPosition) return;

                if (newPosition > currentPosition) {
                    viewFlipper.setInAnimation(getContext(), R.anim.slide_in_right);
                    viewFlipper.setOutAnimation(getContext(), R.anim.slide_out_left);
                } else {
                    viewFlipper.setInAnimation(getContext(), R.anim.slide_in_left);
                    viewFlipper.setOutAnimation(getContext(), R.anim.slide_out_right);
                }

                viewFlipper.setDisplayedChild(newPosition);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupRecyclerView() {
        recyclerViewHot.setLayoutManager(new LinearLayoutManager(getContext()));
        adapterHot = new TopicAdapter(topicListHot);
        recyclerViewHot.setAdapter(adapterHot);

        recyclerViewNew.setLayoutManager(new LinearLayoutManager(getContext()));
        adapterNew = new TopicAdapter(topicListNew);
        recyclerViewNew.setAdapter(adapterNew);

        viewMoreButton.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                MainActivity mainActivity = (MainActivity) getActivity();
                BottomNavigationView bottomNav = mainActivity.findViewById(R.id.bottom_navigation);
                bottomNav.setSelectedItemId(R.id.nav_article);
            }
        });
    }

    private void fetchHomePageData() {
        fetchHomePageData(true);
    }

    private int homeRequest;
    private void fetchHomePageData(boolean animate) {
        final int request = ++homeRequest;
        progressBar.setVisibility(View.VISIBLE);
        errorTextView.setVisibility(View.GONE);
        contentLayout.setVisibility(View.VISIBLE);
        scrollView.setVisibility(View.VISIBLE);
        scrollView.setAlpha(1f);
        quickAccessLayout.setVisibility(View.VISIBLE);
        viewFlipper.setVisibility(View.VISIBLE);
        viewMoreButton.setVisibility(View.VISIBLE);
        RetrofitClient.getInstance().getFeed(1).enqueue(new Callback<com.app.fimtale.model.TopicListResponse>() {
            @Override public void onResponse(Call<com.app.fimtale.model.TopicListResponse> call, Response<com.app.fimtale.model.TopicListResponse> response) {
                if (!isAdded() || request != homeRequest) return;
                progressBar.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);
                if (response.isSuccessful() && response.body() != null) {
                    topicListHot.clear();
                    for (com.app.fimtale.model.Topic topic : response.body().getTopicArray()) topicListHot.add(new TopicViewItem(topic));
                    adapterHot.notifyDataSetChanged();
                } else { errorTextView.setText("推荐作品加载失败，下拉重试"); errorTextView.setVisibility(View.VISIBLE); }
            }
            @Override public void onFailure(Call<com.app.fimtale.model.TopicListResponse> call, Throwable t) {
                if (!isAdded() || request != homeRequest) return;
                progressBar.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);
                errorTextView.setText("加载失败，下拉重试"); errorTextView.setVisibility(View.VISIBLE);
            }
        });
        RetrofitClient.getInstance().getTopicList(1, null, com.app.fimtale.network.SearchQuery.rank("last_chapter_at"))
                .enqueue(new Callback<com.app.fimtale.model.TopicListResponse>() {
            @Override public void onResponse(Call<com.app.fimtale.model.TopicListResponse> call, Response<com.app.fimtale.model.TopicListResponse> response) {
                if (!isAdded() || request != homeRequest) return;
                if (response.isSuccessful() && response.body() != null) {
                    topicListNew.clear();
                    for (com.app.fimtale.model.Topic topic : response.body().getTopicArray()) topicListNew.add(new TopicViewItem(topic));
                    adapterNew.notifyDataSetChanged();
                }
            }
            @Override public void onFailure(Call<com.app.fimtale.model.TopicListResponse> call, Throwable t) {}
        });
        RetrofitClient.getInstance().getCuratedWorks(1).enqueue(new Callback<com.app.fimtale.model.CuratedResponse>() {
            @Override public void onResponse(Call<com.app.fimtale.model.CuratedResponse> call, Response<com.app.fimtale.model.CuratedResponse> response) {
                if (!isAdded() || request != homeRequest) return;
                stopBannerAutoScroll(); bannerList.clear();
                if (response.isSuccessful() && response.body() != null && response.body().items != null) bannerList.addAll(response.body().items);
                bannerAdapter.notifyDataSetChanged();
                bannerViewPager.setVisibility(bannerList.isEmpty() ? View.GONE : View.VISIBLE);
                if (!bannerList.isEmpty()) { bannerViewPager.setCurrentItem(0, false); startBannerAutoScroll(); }
            }
            @Override public void onFailure(Call<com.app.fimtale.model.CuratedResponse> call, Throwable t) {}
        });
    }

    private void showError() {
        progressBar.setVisibility(View.GONE);
        scrollView.setVisibility(View.INVISIBLE);
        errorTextView.setVisibility(View.VISIBLE);
        errorTextView.setText("加载失败，请尝试下拉刷新");
        errorTextView.setOnClickListener(v -> fetchHomePageData(true));
    }

    private void startBannerAutoScroll() {
        stopBannerAutoScroll();
        bannerTimer = new Timer();
        bannerTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                bannerHandler.post(() -> {
                    if (bannerViewPager != null && bannerAdapter != null) {
                        int currentItem = bannerViewPager.getCurrentItem();
                        int totalItems = bannerAdapter.getItemCount();
                        if (totalItems > 1) {
                            int nextItem = (currentItem + 1) % totalItems;
                            try {
                                View child = bannerViewPager.getChildAt(0);
                                if (child instanceof RecyclerView) {
                                    RecyclerView rv = (RecyclerView) child;
                                    final boolean isWrapAround = (nextItem == 0);
                                    RecyclerView.SmoothScroller smoothScroller = new LinearSmoothScroller(getContext()) {
                                        @Override
                                        protected int getHorizontalSnapPreference() {
                                            return SNAP_TO_START;
                                        }

                                        @Override
                                        protected float calculateSpeedPerPixel(DisplayMetrics displayMetrics) {
                                            return isWrapAround ? 0.1f : 0.25f; 
                                        }
                                    };
                                    smoothScroller.setTargetPosition(nextItem);
                                    rv.getLayoutManager().startSmoothScroll(smoothScroller);
                                } else {
                                    bannerViewPager.setCurrentItem(nextItem, true);
                                }
                            } catch (Exception e) {
                                bannerViewPager.setCurrentItem(nextItem, true);
                            }
                        }
                    }
                });
            }
        }, 8000, 8000);
    }

    private void stopBannerAutoScroll() {
        if (bannerTimer != null) {
            bannerTimer.cancel();
            bannerTimer = null;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        stopBannerAutoScroll();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (editorVersion != com.app.fimtale.editor.EditorChanges.version()) {
            editorVersion = com.app.fimtale.editor.EditorChanges.version(); loadContent();
        }
        if (emptyStateLayout != null && emptyStateLayout.getVisibility() == View.VISIBLE ) {
            loadContent();
        }
        if (bannerAdapter != null && bannerAdapter.getItemCount() > 0) {
            startBannerAutoScroll();
        }
    }
}
