package com.app.fimtale.ui;

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
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.viewpager2.widget.CompositePageTransformer;
import androidx.viewpager2.widget.MarginPageTransformer;
import androidx.viewpager2.widget.ViewPager2;

import com.app.fimtale.MainActivity;
import com.app.fimtale.R;
import com.app.fimtale.adapter.BannerAdapter;
import com.app.fimtale.adapter.TopicAdapter;
import com.app.fimtale.model.RecommendedTopic;
import com.app.fimtale.model.Topic;
import com.app.fimtale.model.TopicViewItem;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import com.app.fimtale.network.RetrofitClient;
import com.app.fimtale.utils.DialogHelper;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeFragment extends Fragment {

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView homeList;
    private LinearLayout emptyStateLayout;
    private LinearLayout quickAccessLayout;
    private LinearLayout btnGallery;
    private LinearLayout btnPosts;
    private LinearLayout btnTags;
    private Button btnLogin;
    private TextView tvWhyHow;
    private TabLayout tabLayout;
    private ViewPager2 bannerViewPager;
    private ShimmerSkeletonView loadingSkeleton;
    private final List<Call<?>> homeCalls = new ArrayList<>();
    private int pendingHomeRequests;
    private String homeError;
    private TextView errorTextView;
    private Button viewMoreButton;
    private BannerAdapter bannerAdapter;
    private TopicAdapter topicAdapter;
    private final List<TopicViewItem> visibleTopics = new ArrayList<>();
    private int selectedTab;
    private List<RecommendedTopic> bannerList = new ArrayList<>();
    private List<RecommendedTopic> pendingBanners;
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

        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        homeList = view.findViewById(R.id.homeList);
        // The XML installs its LayoutManager before these inflations generate row LayoutParams.
        View header = getLayoutInflater().inflate(R.layout.item_home_header, homeList, false);
        View footer = getLayoutInflater().inflate(R.layout.item_home_footer, homeList, false);
        bannerViewPager = header.findViewById(R.id.bannerViewPager);
        quickAccessLayout = header.findViewById(R.id.quickAccessLayout);
        btnGallery = header.findViewById(R.id.btnGallery);
        btnPosts = header.findViewById(R.id.btnPosts);
        btnTags = header.findViewById(R.id.btnTags);
        loadingSkeleton = view.findViewById(R.id.loadingSkeleton);
        loadingSkeleton.setSkeletonLayout(ShimmerSkeletonView.Layout.HOME);
        errorTextView = view.findViewById(R.id.errorTextView);
        tabLayout = header.findViewById(R.id.tabLayout);
        viewMoreButton = footer.findViewById(R.id.viewMoreButton);
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout);
        btnLogin = view.findViewById(R.id.btnLogin);
        tvWhyHow = view.findViewById(R.id.tvWhyHow);

        setupBannerViewPager();
        setupRecyclerView(header, footer);
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
        swipeRefreshLayout.setOnRefreshListener(() -> {
            swipeRefreshLayout.setRefreshing(false);
            fetchHomePageData();
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
        bannerViewPager.setOffscreenPageLimit(1);
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
        TabLayout.Tab selected = tabLayout.getTabAt(selectedTab);
        if (selected != null) selected.select();
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (selectedTab == tab.getPosition()) return;
                selectedTab = tab.getPosition();
                displaySelectedTopics();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupRecyclerView(View header, View footer) {
        homeList.setHasFixedSize(true);
        topicAdapter = new TopicAdapter(visibleTopics);
        // One viewport-bound list: header and footer scroll with the cards without
        // measuring both entire feeds inside an unbounded NestedScrollView.
        homeList.setAdapter(new ConcatAdapter(new StaticRowAdapter(header), topicAdapter, new StaticRowAdapter(footer)));
        homeList.setItemAnimator(null);
        int cardInset = Math.round(8 * getResources().getDisplayMetrics().density);
        homeList.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override public void getItemOffsets(@NonNull android.graphics.Rect outRect, @NonNull View view,
                                                @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                if (parent.getChildViewHolder(view).getBindingAdapter() == topicAdapter)
                    outRect.set(cardInset, 0, cardInset, 0);
            }
        });

        viewMoreButton.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                MainActivity mainActivity = (MainActivity) getActivity();
                BottomNavigationView bottomNav = mainActivity.findViewById(R.id.bottom_navigation);
                bottomNav.setSelectedItemId(R.id.nav_article);
            }
        });
    }

    private void displaySelectedTopics() {
        visibleTopics.clear();
        visibleTopics.addAll(selectedTab == 0 ? topicListHot : topicListNew);
        topicAdapter.notifyDataSetChanged();
    }

    private static final class StaticRowAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final View row;
        StaticRowAdapter(View row) { this.row = row; }
        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new RecyclerView.ViewHolder(row) {};
        }
        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {}
        @Override public int getItemCount() { return 1; }
    }

    private int homeRequest;
    private void fetchHomePageData() {
        final int request = ++homeRequest;
        for (Call<?> call : homeCalls) call.cancel();
        homeCalls.clear(); pendingHomeRequests = 3; homeError = null; pendingBanners = null;
        stopBannerAutoScroll();
        swipeRefreshLayout.setRefreshing(false);
        loadingSkeleton.setVisibility(View.VISIBLE);
        errorTextView.setVisibility(View.GONE);
        homeList.setVisibility(View.INVISIBLE);
        homeList.scrollToPosition(0);
        quickAccessLayout.setVisibility(View.VISIBLE);
        viewMoreButton.setVisibility(View.VISIBLE);
        Call<com.app.fimtale.model.TopicListResponse> feed = RetrofitClient.getInstance().getFeed(1);
        homeCalls.add(feed);
        feed.enqueue(new Callback<com.app.fimtale.model.TopicListResponse>() {
            @Override public void onResponse(Call<com.app.fimtale.model.TopicListResponse> call, Response<com.app.fimtale.model.TopicListResponse> response) {
                if (!acceptHomeResult(request)) return;
                if (response.isSuccessful() && response.body() != null) {
                    topicListHot.clear();
                    List<Topic> topics = response.body().getTopicArray();
                    if (topics != null) for (Topic topic : topics) topicListHot.add(new TopicViewItem(topic));
                } else homeError = "推荐作品加载失败，点击重试";
                finishHomeRequest();
            }
            @Override public void onFailure(Call<com.app.fimtale.model.TopicListResponse> call, Throwable t) {
                if (!acceptHomeResult(request)) return;
                homeError = "推荐作品加载失败，点击重试"; finishHomeRequest();
            }
        });
        Call<com.app.fimtale.model.TopicListResponse> latest = RetrofitClient.getInstance()
                .getTopicList(1, null, com.app.fimtale.network.SearchQuery.rank("last_chapter_at"));
        homeCalls.add(latest);
        latest.enqueue(new Callback<com.app.fimtale.model.TopicListResponse>() {
            @Override public void onResponse(Call<com.app.fimtale.model.TopicListResponse> call, Response<com.app.fimtale.model.TopicListResponse> response) {
                if (!acceptHomeResult(request)) return;
                if (response.isSuccessful() && response.body() != null) {
                    topicListNew.clear();
                    List<Topic> topics = response.body().getTopicArray();
                    if (topics != null) for (Topic topic : topics) topicListNew.add(new TopicViewItem(topic));
                } else homeError = "最近更新加载失败，点击重试";
                finishHomeRequest();
            }
            @Override public void onFailure(Call<com.app.fimtale.model.TopicListResponse> call, Throwable t) {
                if (!acceptHomeResult(request)) return;
                homeError = "最近更新加载失败，点击重试"; finishHomeRequest();
            }
        });
        Call<com.app.fimtale.model.CuratedResponse> curated = RetrofitClient.getInstance().getCuratedWorks(1);
        homeCalls.add(curated);
        curated.enqueue(new Callback<com.app.fimtale.model.CuratedResponse>() {
            @Override public void onResponse(Call<com.app.fimtale.model.CuratedResponse> call, Response<com.app.fimtale.model.CuratedResponse> response) {
                if (!acceptHomeResult(request)) return;
                if (response.isSuccessful() && response.body() != null) {
                    // Keep the attached adapter's data unchanged until the batch is ready.
                    pendingBanners = response.body().items == null ? new ArrayList<>() : new ArrayList<>(response.body().items);
                } else homeError = "精选作品加载失败，点击重试";
                finishHomeRequest();
            }
            @Override public void onFailure(Call<com.app.fimtale.model.CuratedResponse> call, Throwable t) {
                if (!acceptHomeResult(request)) return;
                homeError = "精选作品加载失败，点击重试"; finishHomeRequest();
            }
        });
    }
    private boolean acceptHomeResult(int request) {
        return isAdded() && getView() != null && request == homeRequest;
    }
    private void finishHomeRequest() {
        if (--pendingHomeRequests != 0) return;
        homeCalls.clear();
        displaySelectedTopics();
        if (pendingBanners != null) {
            bannerList.clear(); bannerList.addAll(pendingBanners); pendingBanners = null;
            bannerAdapter.notifyDataSetChanged();
        }
        loadingSkeleton.setVisibility(View.GONE); homeList.setVisibility(View.VISIBLE);
        bannerViewPager.setVisibility(bannerList.isEmpty() ? View.GONE : View.VISIBLE);
        if (!bannerList.isEmpty()) {
            bannerViewPager.setCurrentItem(0, false);
            if (isResumed()) startBannerAutoScroll();
        }
        if (homeError != null) {
            errorTextView.setText(homeError); errorTextView.setVisibility(View.VISIBLE);
            errorTextView.setOnClickListener(v -> fetchHomePageData());
        }
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
        if (pendingHomeRequests == 0 && bannerAdapter != null && bannerAdapter.getItemCount() > 0) {
            startBannerAutoScroll();
        }
    }
    @Override public void onDestroyView() {
        homeRequest++;
        for (Call<?> call : homeCalls) call.cancel();
        homeCalls.clear(); pendingHomeRequests = 0; pendingBanners = null;
        stopBannerAutoScroll(); bannerHandler.removeCallbacksAndMessages(null);
        if (homeList != null) homeList.setAdapter(null);
        if (bannerViewPager != null) bannerViewPager.setAdapter(null);
        rootView = null; homeList = null;
        super.onDestroyView();
    }

}
