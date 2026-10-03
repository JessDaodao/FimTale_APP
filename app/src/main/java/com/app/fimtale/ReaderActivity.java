package com.app.fimtale;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.graphics.Insets;
import android.graphics.Color;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.TransitionManager;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import android.widget.ImageView;

import com.app.fimtale.adapter.CommentAdapter;
import com.app.fimtale.adapter.TopicAdapter;
import com.app.fimtale.db.CacheManager;
import com.app.fimtale.db.CachedChapter;
import com.app.fimtale.model.ChapterMenuItem;
import com.app.fimtale.model.Comment;
import com.app.fimtale.model.Topic;
import com.app.fimtale.model.TopicDetailResponse;
import com.app.fimtale.model.TopicInfo;
import com.app.fimtale.model.TopicListResponse;
import com.app.fimtale.model.TopicViewItem;
import com.app.fimtale.network.RetrofitClient;
import com.app.fimtale.utils.UserPreferences;
import android.widget.ProgressBar;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.image.glide.GlideImagesPlugin;
import okhttp3.ResponseBody;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.slider.Slider;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ReaderActivity extends AppCompatActivity {

    public static final String EXTRA_WORK_ID = "work_id";
    public static final String EXTRA_CHAPTER_ID = "chapter_id";
    public static final String EXTRA_INITIAL_PROGRESS = "initial_progress";

    private ViewPager2 viewPager;
    private RecyclerView recyclerView;
    private View menuOverlay;
    private View dimLayer;
    private MaterialToolbar topToolbar;
    private LinearLayout bottomSheetContainer;
    private View bottomMenu;
    private View btnChapterList;
    private View btnSettings;
    private View guideOverlay;
    
    private View readerHeader;
    private View readerFooter;
    private TextView tvChapterTitle;
    private TextView tvChapterProgress;
    private TextView tvBatteryLevel;
    private android.widget.TextClock tcSystemTime;
    private View viewBatteryLevel;
    private ImageView ivCharging;
    private ImageView ivBatteryFrame;
    private ProgressBar scrollProgressBar;

    private BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int batteryPct = (int) (level * 100 / (float) scale);
            if (tvBatteryLevel != null) {
                tvBatteryLevel.setText(batteryPct + "%");
            }
            if (viewBatteryLevel != null && viewBatteryLevel.getBackground() != null) {
                viewBatteryLevel.getBackground().setLevel(batteryPct * 100);
            }

            if (ivCharging != null) {
                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL;
                ivCharging.setVisibility(isCharging ? View.VISIBLE : View.GONE);
            }
        }
    };

    private LinearLayout settingsPanel;
    private LinearLayout chapterListPanel;
    private RecyclerView rvChapterList;
    private Slider sliderFontSize;
    private Slider sliderLineSpacing;
    private Slider sliderBrightness;
    private TabLayout tabPageMode;
    private TabLayout tabThemeMode;

    private boolean isMenuVisible = false;
    private List<ReaderPage> pages = new ArrayList<>();
    private List<ReaderPage> verticalPages = new ArrayList<>();
    private List<Integer> chapterStartPageIndices = new ArrayList<>();
    private List<Integer> chapterVerticalIndices = new ArrayList<>();
    private List<Integer> pageStartOffsets = new ArrayList<>();
    private List<Integer> paragraphStartOffsets = new ArrayList<>();
    private ReaderAdapter adapter;
    private ReaderAdapter recyclerAdapter;
    private int currentTopicId;
    private int currentPostId = -1;
    private int initialTopicId;
    private int rootTopicId = -1;
    private double initialProgress = -1d;
    private double currentProgress = 0d;
    private Handler progressSaveHandler = new Handler(Looper.getMainLooper());
    private static final long PROGRESS_SAVE_INTERVAL = 30 * 1000;

    private Runnable progressSaveRunnable = new Runnable() {
        @Override
        public void run() {
            saveReadingProgress();
            progressSaveHandler.postDelayed(this, PROGRESS_SAVE_INTERVAL);
        }
    };
    private boolean initialProgressApplied = false;
    private int lastWidth = 0;
    private int lastHeight = 0;
    
    private float currentFontSize = 20f;
    private SharedPreferences prefs;
    private Insets lastSystemBars = null;
    private Markwon markwon;

    private String fullChapterContent = "加载中...";
    private String chapterTitle = "";
    private List<ChapterMenuItem> chapterList = new ArrayList<>();
    private List<ChapterMenuItem> filteredChapterList = new ArrayList<>();
    private ChapterListAdapter chapterListAdapter;
    
    private boolean isLoadingChapter = false;
    private boolean canTriggerChapterChange = false;
    private GestureDetector gestureDetector;

    private List<ContentSegment> parsedSegments = new ArrayList<>();

    private static class ContentSegment {
        int type;
        String content;
        ContentSegment(int type, String content) {
            this.type = type;
            this.content = content;
        }
    }

    private static class ReaderPage {
        static final int TYPE_TEXT = 0;
        static final int TYPE_COMMENT = 1;
        static final int TYPE_LOADING = 2;
        static final int TYPE_NEXT_CHAPTER_TRIGGER = 3;
        static final int TYPE_PREV_CHAPTER_TRIGGER = 4;
        static final int TYPE_IMAGE = 5;
        
        int type;
        String content;
        int chapterId;
        
        ReaderPage(int type, String content, int chapterId) {
            this.type = type;
            this.content = content;
            this.chapterId = chapterId;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reader);

        currentTopicId = getIntent().getIntExtra(EXTRA_CHAPTER_ID, 0);
        rootTopicId = getIntent().getIntExtra(EXTRA_WORK_ID, 0);
        if (rootTopicId <= 0 || currentTopicId < 0) {
            finish();
            return;
        }
        initialTopicId = currentTopicId;
        initialProgress = getIntent().getDoubleExtra(EXTRA_INITIAL_PROGRESS, -1d);
        if (initialProgress >= 0) initialProgress = clamp01(initialProgress);

        viewPager = findViewById(R.id.viewPager);
        recyclerView = findViewById(R.id.recyclerView);
        menuOverlay = findViewById(R.id.menuOverlay);
        dimLayer = findViewById(R.id.dimLayer);
        topToolbar = findViewById(R.id.topToolbar);
        bottomSheetContainer = findViewById(R.id.bottomSheetContainer);
        bottomMenu = findViewById(R.id.bottomMenu);
        btnChapterList = findViewById(R.id.btnChapterList);
        btnSettings = findViewById(R.id.btnSettings);
        guideOverlay = findViewById(R.id.guideOverlay);

        settingsPanel = findViewById(R.id.settingsPanel);
        chapterListPanel = findViewById(R.id.chapterListPanel);
        rvChapterList = findViewById(R.id.rvChapterList);
        
        readerHeader = findViewById(R.id.readerHeader);
        readerFooter = findViewById(R.id.readerFooter);
        tvChapterTitle = findViewById(R.id.tvChapterTitle);
        tvChapterProgress = findViewById(R.id.tvChapterProgress);
        tvBatteryLevel = findViewById(R.id.tvBatteryLevel);
        tcSystemTime = findViewById(R.id.tcSystemTime);
        viewBatteryLevel = findViewById(R.id.viewBatteryLevel);
        ivCharging = findViewById(R.id.ivCharging);
        
        View batteryLayout = findViewById(R.id.batteryLayout);
        if (batteryLayout instanceof ViewGroup) {
            ViewGroup batteryContainer = (ViewGroup) batteryLayout;
            for (int i = 0; i < batteryContainer.getChildCount(); i++) {
                View child = batteryContainer.getChildAt(i);
                if (child instanceof FrameLayout) {
                    FrameLayout frame = (FrameLayout) child;
                    if (frame.getChildAt(0) instanceof ImageView) {
                        ivBatteryFrame = (ImageView) frame.getChildAt(0);
                    }
                }
            }
        }
        
        scrollProgressBar = findViewById(R.id.scrollProgressBar);
        
        sliderFontSize = findViewById(R.id.sliderFontSize);
        sliderLineSpacing = findViewById(R.id.sliderLineSpacing);
        sliderBrightness = findViewById(R.id.sliderBrightness);
        tabPageMode = findViewById(R.id.tabPageMode);
        tabThemeMode = findViewById(R.id.tabThemeMode);

        markwon = Markwon.builder(this)
                .usePlugin(HtmlPlugin.create())
                .usePlugin(TablePlugin.create(this))
                .usePlugin(GlideImagesPlugin.create(this))
                .build();

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        ViewCompat.setOnApplyWindowInsetsListener(menuOverlay, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            if (systemBars.top > 0 || systemBars.bottom > 0) {
                lastSystemBars = systemBars;
            }
            
            Insets insets = (lastSystemBars != null) ? lastSystemBars : systemBars;
            
            ViewGroup.MarginLayoutParams topParams = (ViewGroup.MarginLayoutParams) topToolbar.getLayoutParams();
            topParams.topMargin = insets.top + (int)(4 * getResources().getDisplayMetrics().density);
            topToolbar.setLayoutParams(topParams);
            
            ViewGroup.MarginLayoutParams bottomParams = (ViewGroup.MarginLayoutParams) bottomSheetContainer.getLayoutParams();
            bottomParams.bottomMargin = insets.bottom + (int)(16 * getResources().getDisplayMetrics().density);
            bottomSheetContainer.setLayoutParams(bottomParams);
            
            int headerTopPadding = insets.top + (int)(12 * getResources().getDisplayMetrics().density);
            readerHeader.setPadding(
                readerHeader.getPaddingLeft(),
                headerTopPadding,
                readerHeader.getPaddingRight(),
                readerHeader.getPaddingBottom()
            );

            int footerBottomPadding = insets.bottom + (int)(8 * getResources().getDisplayMetrics().density);
            readerFooter.setPadding(
                readerFooter.getPaddingLeft(),
                readerFooter.getPaddingTop(),
                readerFooter.getPaddingRight(),
                footerBottomPadding
            );
            
            int bodyTopPadding = insets.top + (int)(50 * getResources().getDisplayMetrics().density);
            int bodyBottomPadding = insets.bottom + (int)(30 * getResources().getDisplayMetrics().density);
            viewPager.setPadding(0, bodyTopPadding, 0, bodyBottomPadding);
            recyclerView.setPadding(0, bodyTopPadding, 0, bodyBottomPadding);
            
            return windowInsets;
        });

        topToolbar.setNavigationOnClickListener(v -> finish());
        topToolbar.inflateMenu(R.menu.menu_reader);
        topToolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_open_site) {
                com.app.fimtale.utils.DialogHelper.openSite(this, "/work/" + rootTopicId
                        + (currentTopicId > 0 ? "/chapter/" + currentTopicId : ""));
                return true;
            }
            if (item.getItemId() == R.id.action_info) {
                if (rootTopicId != -1) {
                    Intent intent = new Intent(this, TopicDetailActivity.class);
                    intent.putExtra(TopicDetailActivity.EXTRA_TOPIC_ID, rootTopicId);
                    startActivity(intent);
                } else {
                    Toast.makeText(this, "获取失败", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            return false;
        });
        
        dimLayer.setOnClickListener(v -> hideMenu());
        
        btnChapterList.setOnClickListener(v -> toggleChapterList());

        btnSettings.setOnClickListener(v -> toggleSettingsPanel());

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        currentFontSize = prefs.getFloat("reader_font_size", 20f);
        boolean isVertical = prefs.getBoolean("reader_is_vertical", false);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerAdapter = new ReaderAdapter(verticalPages, true);
        recyclerView.setAdapter(recyclerAdapter);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    canTriggerChapterChange = true;
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (isMenuVisible && dy != 0) {
                    hideMenu();
                }
                
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager == null || verticalPages.isEmpty()) return;

                int firstPos = layoutManager.findFirstVisibleItemPosition();
                int lastPos = layoutManager.findLastVisibleItemPosition();
                
                if (firstPos != RecyclerView.NO_POSITION) {
                    float percent = calculateContentBasedPercent(layoutManager, firstPos, lastPos);
                    
                    if (percent > 100) percent = 100;
                    if (percent < 0) percent = 0;
                    currentProgress = (double) percent / 100.0;
                    updateHeader(chapterTitle, String.format("%.1f%%", percent));
                    if (scrollProgressBar != null) {
                        scrollProgressBar.setProgress((int) (percent * 10));
                    }
                }
            }
        });

        adapter = new ReaderAdapter(pages, false);
        viewPager.setAdapter(adapter);

        updateFontSize(currentFontSize);
        sliderFontSize.setValue(currentFontSize);

        float currentLineSpacing = UserPreferences.getLineSpacing(this);
        sliderLineSpacing.setValue(currentLineSpacing);
        sliderLineSpacing.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                UserPreferences.setLineSpacing(this, value);
                calculatePages();
                if (recyclerView.getVisibility() == View.VISIBLE) {
                    prepareVerticalContent();
                    recyclerAdapter.updateData(verticalPages);
                }
            }
        });
        
        sliderBrightness.setLabelFormatter(value -> (int)(value * 100) + "%");
        
        android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
        float currentBrightness = lp.screenBrightness;
        if (currentBrightness < 0) {
            try {
                int systemBrightness = android.provider.Settings.System.getInt(getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS);
                currentBrightness = systemBrightness / 255f;
            } catch (Exception e) {
                currentBrightness = 0.5f;
            }
        }
        
        if (currentBrightness < 0.01f) currentBrightness = 0.01f;
        if (currentBrightness > 1.0f) currentBrightness = 1.0f;
        
        sliderBrightness.setValue(currentBrightness);
        
        sliderBrightness.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                android.view.WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
                layoutParams.screenBrightness = value;
                getWindow().setAttributes(layoutParams);
            }
        });

        if (isVertical) {
            TabLayout.Tab tab = tabPageMode.getTabAt(1);
            if (tab != null) tab.select();
        } else {
            TabLayout.Tab tab = tabPageMode.getTabAt(0);
            if (tab != null) tab.select();
        }

        int currentTheme = UserPreferences.getReaderTheme(this);
        TabLayout.Tab themeTab = tabThemeMode.getTabAt(currentTheme);
        if (themeTab != null) themeTab.select();
        applyReaderTheme(currentTheme);

        tabThemeMode.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int theme = tab.getPosition();
                UserPreferences.setReaderTheme(ReaderActivity.this, theme);
                applyReaderTheme(theme);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        sliderFontSize.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                currentFontSize = value;
                updateFontSize(currentFontSize);
                saveFontSize();
            }
        });

        tabPageMode.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    updatePageMode(false);
                    prefs.edit().putBoolean("reader_is_vertical", false).apply();
                } else {
                    updatePageMode(true);
                    prefs.edit().putBoolean("reader_is_vertical", true).apply();
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        viewPager.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                viewPager.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                calculatePages();
                
                if (isVertical) {
                    updatePageMode(true);
                } else {
                    updatePageMode(false);
                }

                topToolbar.setTranslationY(-topToolbar.getBottom());
                bottomSheetContainer.setTranslationY(menuOverlay.getHeight() - bottomSheetContainer.getTop());
                
                loadWorkNavigation();
            }
        });

        hideSystemUI();
        
        setupChapterList();
        
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                updateCurrentChapterFromPage(position);
                
                if (adapter != null && position < adapter.getItemCount()) {
                    int type = adapter.getItemViewType(position);
                    if (type == ReaderPage.TYPE_NEXT_CHAPTER_TRIGGER) {
                        int nextId = getNextChapterId();
                        if (nextId != -1) {
                            jumpToChapter(nextId);
                        }
                    } else if (type == ReaderPage.TYPE_PREV_CHAPTER_TRIGGER) {
                        int prevId = getPrevChapterId();
                        if (prevId != -1) {
                            jumpToChapter(prevId, true);
                        }
                    }
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                super.onPageScrollStateChanged(state);
                if (state == ViewPager2.SCROLL_STATE_DRAGGING && isMenuVisible) {
                    hideMenu();
                }
            }
        });

        checkAndShowGuide();
        
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryReceiver, ifilter);
        
        progressSaveHandler.postDelayed(progressSaveRunnable, PROGRESS_SAVE_INTERVAL);

        initGestureDetector();
    }

    private void initGestureDetector() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                return handleSingleTap(e);
            }
            
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }
        });
    }

    private boolean handleSingleTap(MotionEvent e) {
        if (isMenuVisible) {
            hideMenu();
            return true;
        }

        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        float x = e.getRawX();
        float y = e.getRawY();

        boolean isVertical = prefs.getBoolean("reader_is_vertical", false);

        if (isVertical) {
            if (y < height * 0.3) {
                if (recyclerView != null) {
                    recyclerView.smoothScrollBy(0, -height / 2);
                }
            } else if (y > height * 0.7) {
                if (recyclerView != null) {
                    recyclerView.smoothScrollBy(0, height / 2);
                }
            } else {
                toggleMenu();
            }
        } else {
            if (x < width * 0.3) {
                if (viewPager != null) {
                    int current = viewPager.getCurrentItem();
                    if (current > 0) {
                        viewPager.setCurrentItem(current - 1, true);
                    }
                }
            } else if (x > width * 0.7) {
                if (viewPager != null) {
                    int current = viewPager.getCurrentItem();
                    if (adapter != null && current < adapter.getItemCount() - 1) {
                        viewPager.setCurrentItem(current + 1, true);
                    }
                }
            } else {
                toggleMenu();
            }
        }
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (isMenuVisible) {
            return super.dispatchKeyEvent(event);
        }

        int action = event.getAction();
        int keyCode = event.getKeyCode();

        switch (keyCode) {
            case android.view.KeyEvent.KEYCODE_VOLUME_UP:
                if (action == android.view.KeyEvent.ACTION_DOWN) {
                    boolean isVertical = prefs.getBoolean("reader_is_vertical", false);
                    if (isVertical) {
                        if (recyclerView != null) {
                            int scrollAmount = recyclerView.getHeight() / 2;
                            recyclerView.smoothScrollBy(0, -scrollAmount);
                        }
                    } else {
                        if (viewPager != null) {
                            int currentItem = viewPager.getCurrentItem();
                            if (currentItem > 0) {
                                viewPager.setCurrentItem(currentItem - 1, true);
                            }
                        }
                    }
                }
                return true;
            case android.view.KeyEvent.KEYCODE_VOLUME_DOWN:
                if (action == android.view.KeyEvent.ACTION_DOWN) {
                    boolean isVertical = prefs.getBoolean("reader_is_vertical", false);
                    if (isVertical) {
                        if (recyclerView != null) {
                            int scrollAmount = recyclerView.getHeight() / 2;
                            recyclerView.smoothScrollBy(0, scrollAmount);
                        }
                    } else {
                        if (viewPager != null) {
                            int currentItem = viewPager.getCurrentItem();
                            if (adapter != null && currentItem < adapter.getItemCount() - 1) {
                                viewPager.setCurrentItem(currentItem + 1, true);
                            }
                        }
                    }
                }
                return true;
        }
        
        return super.dispatchKeyEvent(event);
    }
    
    private void fetchChapterContent(int topicId) {
        fetchChapterContent(topicId, false);
    }
    
    private TopicDetailResponse workData;
    private boolean contentReady;
    private void loadWorkNavigation() {
        RetrofitClient.getInstance().getWork(rootTopicId).enqueue(new Callback<TopicDetailResponse>() {
            @Override public void onResponse(Call<TopicDetailResponse> call, Response<TopicDetailResponse> response) {
                if (isFinishing() || isDestroyed()) return;
                if (response.isSuccessful() && response.body() != null && response.body().getTopicInfo() != null) {
                    applyNavigation(response.body());
                    CacheManager.getInstance(ReaderActivity.this).cacheChapterMenu(rootTopicId, workData);
                    fetchChapterContent(currentTopicId);
                } else { showReadError("无法加载作品目录，请返回后重试"); }
            }
            @Override public void onFailure(Call<TopicDetailResponse> call, Throwable t) {
                CacheManager.getInstance(ReaderActivity.this).getChapterMenu(rootTopicId, data -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (data != null) { applyNavigation(data); fetchChapterContent(currentTopicId); }
                    else showReadError("网络错误，且没有离线目录");
                });
            }
        });
    }
    private void applyNavigation(TopicDetailResponse data) {
        workData = data;
        chapterList = data.getMenu();
        filteredChapterList.clear(); filteredChapterList.addAll(chapterList);
        if (chapterListAdapter != null) chapterListAdapter.updateData(filteredChapterList);
    }
    private int cacheKey(int chapterId) { return chapterId == 0 ? -rootTopicId : chapterId; }
    private void fetchChapterContent(int topicId, boolean scrollToEnd) {
        if (isLoadingChapter) return;
        isLoadingChapter = true;
        contentReady = false;
        canTriggerChapterChange = false;
        fullChapterContent = "加载中...";
        if (viewPager.getVisibility() == View.VISIBLE) calculatePages(); else prepareVerticalContent();
        if (topicId == 0 && workData != null) {
            TopicInfo work = workData.getTopicInfo();
            displayChapter(0, work.getTitle(), work.getContent(), scrollToEnd);
            return;
        }
        CacheManager.getInstance(this).getChapter(cacheKey(topicId), cached -> {
            if (isFinishing() || isDestroyed()) return;
            if (cached != null && cached.rootTopicId == rootTopicId) {
                applyCachedChapter(cached, scrollToEnd);
                preloadNextChapter();
            } else fetchFromNetwork(topicId, scrollToEnd);
        });
    }
    private void showReadError(String message) {
        contentReady = false;
        isLoadingChapter = false;
        fullChapterContent = message;
        parsedSegments.clear(); parsedSegments.add(new ContentSegment(ReaderPage.TYPE_TEXT, message));
        prepareVerticalContent(); calculatePages();
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
    private void displayChapter(int id, String title, String content, boolean scrollToEnd) {
        CachedChapter cached = new CachedChapter();
        cached.topicId = cacheKey(id); cached.rootTopicId = rootTopicId; cached.postId = id;
        cached.title = title; cached.content = content;
        CacheManager.getInstance(this).cacheChapter(cached.topicId, rootTopicId, id, title, content, null);
        applyCachedChapter(cached, scrollToEnd);
        preloadNextChapter();
    }
    private void applyCachedChapter(CachedChapter cached, boolean scrollToEnd) {
        contentReady = true;
        isLoadingChapter = false;
        chapterTitle = cached.title == null ? "" : cached.title;
        currentPostId = cached.postId;
        currentTopicId = cached.postId;
        currentProgress = 0;
        topToolbar.setTitle(chapterTitle); tvChapterTitle.setText(chapterTitle);
        String content = com.app.fimtale.utils.BbCode.toMarkdown(cached.content);
        fullChapterContent = content.isEmpty() ? "无内容" : content;
        parseContent(fullChapterContent);
        prepareVerticalContent(); calculatePages();
        positionReader(currentTopicId, scrollToEnd);
        if (initialProgress < 0 && currentTopicId == initialTopicId && UserPreferences.isLoggedIn(this)) {
            final int loadedChapter = currentTopicId;
            RetrofitClient.getInstance().saveReadingProgress(new com.app.fimtale.model.ReadProgress(rootTopicId, loadedChapter, 0))
                    .enqueue(new Callback<com.app.fimtale.model.ReadProgress>() {
                @Override public void onResponse(Call<com.app.fimtale.model.ReadProgress> call, Response<com.app.fimtale.model.ReadProgress> response) {
                    if (isFinishing() || isDestroyed() || currentTopicId != loadedChapter) return;
                    if (response.isSuccessful() && response.body() != null && response.body().progress > 0) {
                        final double progress = response.body().progress;
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(ReaderActivity.this)
                                .setMessage("继续上次的阅读进度？").setPositiveButton("继续", (d, w) -> {
                                    if (currentTopicId != loadedChapter) return;
                                    initialProgress = progress; initialProgressApplied = false;
                                    positionReader(loadedChapter, false);
                                }).setNegativeButton("从头阅读", null).show();
                    }
                }
                @Override public void onFailure(Call<com.app.fimtale.model.ReadProgress> call, Throwable t) {}
            });
        }
    }

    private void positionReader(int topicId, boolean scrollToEnd) {
        if (viewPager.getVisibility() == View.VISIBLE) {
            int initialPage = 0;
            if (!pages.isEmpty() && pages.get(0).type == ReaderPage.TYPE_PREV_CHAPTER_TRIGGER) {
                initialPage = 1;
            }

            if (!scrollToEnd && shouldApplyInitialProgress(topicId)) {
                initialPage = computeTargetPagedIndex(initialProgress);
                initialProgressApplied = true;
            } else if (scrollToEnd) {
                initialPage = pages.size() - 1;
                if (initialPage > 0 && pages.get(initialPage).type == ReaderPage.TYPE_NEXT_CHAPTER_TRIGGER) {
                    initialPage--;
                }
            }

            viewPager.setCurrentItem(initialPage, false);
            updateCurrentChapterFromPage(initialPage);
        } else {
            int pos;
            if (!scrollToEnd && shouldApplyInitialProgress(topicId)) {
                pos = computeTargetVerticalIndex(initialProgress);
                initialProgressApplied = true;
            } else if (scrollToEnd) {
                pos = verticalPages.size() - 1;
                if (pos > 0 && verticalPages.get(pos).type == ReaderPage.TYPE_NEXT_CHAPTER_TRIGGER) {
                    pos--;
                }
            } else {
                pos = 0;
                if (!verticalPages.isEmpty() && verticalPages.get(0).type == ReaderPage.TYPE_PREV_CHAPTER_TRIGGER) {
                    pos = 1;
                }
            }

            int finalPos = pos;
            recyclerView.post(() -> {
                RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
                if (lm instanceof LinearLayoutManager) {
                    ((LinearLayoutManager) lm).scrollToPositionWithOffset(finalPos, 0);
                } else {
                    recyclerView.scrollToPosition(finalPos);
                }
                updateCurrentChapterFromParagraph(finalPos);
            });
        }
    }

    private void fetchFromNetwork(int chapterId, boolean scrollToEnd) {
        RetrofitClient.getInstance().getChapter(chapterId).enqueue(new Callback<com.app.fimtale.model.ChapterResponse>() {
            @Override public void onResponse(Call<com.app.fimtale.model.ChapterResponse> call, Response<com.app.fimtale.model.ChapterResponse> response) {
                if (isFinishing() || isDestroyed()) return;
                com.app.fimtale.model.ChapterResponse data = response.body();
                if (response.isSuccessful() && data != null && data.chapter != null && data.chapter.workId == rootTopicId) {
                    displayChapter(data.chapter.id, data.chapter.title, data.chapter.content, scrollToEnd);
                } else showReadError("章节加载失败，请从目录重试");
            }
            @Override public void onFailure(Call<com.app.fimtale.model.ChapterResponse> call, Throwable t) {
                if (!isFinishing() && !isDestroyed()) showReadError("网络错误，请从目录重试");
            }
        });
    }
    private void preloadNextChapter() {
        int nextId = getNextChapterId();
        if (nextId <= 0) return;
        final int workId = rootTopicId;
        CacheManager cache = CacheManager.getInstance(this);
        cache.getChapter(nextId, cached -> {
            if (cached != null || isFinishing() || isDestroyed()) return;
            RetrofitClient.getInstance().getChapter(nextId).enqueue(new Callback<com.app.fimtale.model.ChapterResponse>() {
                @Override public void onResponse(Call<com.app.fimtale.model.ChapterResponse> call, Response<com.app.fimtale.model.ChapterResponse> response) {
                    com.app.fimtale.model.ChapterResponse data = response.body();
                    if (response.isSuccessful() && data != null && data.chapter != null && data.chapter.workId == workId) {
                        cache.cacheChapter(data.chapter.id, workId, data.chapter.id, data.chapter.title, data.chapter.content, null);
                    }
                }
                @Override public void onFailure(Call<com.app.fimtale.model.ChapterResponse> call, Throwable t) {}
            });
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveReadingProgress();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        progressSaveHandler.removeCallbacks(progressSaveRunnable);
        unregisterReceiver(batteryReceiver);
    }

    private void checkAndShowGuide() {
        boolean hasShownGuide = prefs.getBoolean("has_shown_reader_guide", false);
        if (!hasShownGuide) {
            guideOverlay.setAlpha(1f);
            guideOverlay.setVisibility(View.VISIBLE);
            guideOverlay.setOnClickListener(v -> {
                guideOverlay.animate()
                        .alpha(0f)
                        .setDuration(500)
                        .withEndAction(() -> guideOverlay.setVisibility(View.GONE))
                        .start();
                prefs.edit().putBoolean("has_shown_reader_guide", true).apply();
            });
        }
    }
    
    private void setupChapterList() {
        rvChapterList.setLayoutManager(new LinearLayoutManager(this));
        chapterListAdapter = new ChapterListAdapter(false);
        rvChapterList.setAdapter(chapterListAdapter);
    }

    private void jumpToChapter(int chapterId) {
        jumpToChapter(chapterId, false);
    }
    
    private void jumpToChapter(int chapterId, boolean scrollToEnd) {
        if (isLoadingChapter) return;
        saveReadingProgress();
        if (chapterId == 0 && currentTopicId != 0) {
            Intent intent = new Intent(this, TopicDetailActivity.class);
            intent.putExtra(TopicDetailActivity.EXTRA_TOPIC_ID, rootTopicId);
            startActivity(intent); finish(); return;
        }
        fetchChapterContent(chapterId, scrollToEnd);
        hideMenu();
    }

    private void updateCurrentChapterFromPage(int pageIndex) {
        if (pageIndex < 0 || pageIndex >= pages.size()) return;
        
        ReaderPage page = pages.get(pageIndex);
        if (page.type == ReaderPage.TYPE_PREV_CHAPTER_TRIGGER || page.type == ReaderPage.TYPE_NEXT_CHAPTER_TRIGGER) {
            updateHeader(chapterTitle, "");
            return;
        }

        int startOffset = 0;
        int endOffset = 0;
        
        if (!pages.isEmpty() && pages.get(0).type == ReaderPage.TYPE_PREV_CHAPTER_TRIGGER) {
            startOffset = 1;
        }
        
        if (!pages.isEmpty() && pages.get(pages.size() - 1).type == ReaderPage.TYPE_NEXT_CHAPTER_TRIGGER) {
            endOffset = 1;
        }
        
        int realTotal = pages.size() - startOffset - endOffset;
        int realIndex = pageIndex - startOffset + 1;
        
        if (realIndex < 1) realIndex = 1;
        if (realIndex > realTotal) realIndex = realTotal;
        
        if (realTotal > 1) {
            currentProgress = (double) (realIndex - 1) / (realTotal - 1);
        } else if (realTotal == 1) {
            currentProgress = 1.0;
        } else {
            currentProgress = 0d;
        }

        updateHeader(chapterTitle, realIndex + "/" + realTotal);
    }

    private void updateCurrentChapterFromParagraph(int paragraphIndex) {
         if (verticalPages.isEmpty()) return;
         int size = verticalPages.size();
         float percent;
         if (size > 1) {
             percent = (float) paragraphIndex * 100 / (size - 1);
             currentProgress = (double) paragraphIndex / (size - 1);
         } else {
             percent = 100f;
             currentProgress = 1.0;
         }
         updateHeader(chapterTitle, String.format("%.1f%%", percent));
         if (scrollProgressBar != null) {
             scrollProgressBar.setProgress((int) (percent * 10));
         }
    }
    
    private void updateHeader(String title, String progressText) {
        tvChapterTitle.setText(title);
        tvChapterProgress.setText(progressText);
    }

    private void saveReadingProgress() {
        if (!contentReady || currentPostId < 0 || rootTopicId <= 0 || isLoadingChapter || !UserPreferences.isLoggedIn(this)) return;
        RetrofitClient.getInstance().saveReadingProgress(new com.app.fimtale.model.ReadProgress(rootTopicId, currentPostId, currentProgress))
                .enqueue(new Callback<com.app.fimtale.model.ReadProgress>() {
            @Override public void onResponse(Call<com.app.fimtale.model.ReadProgress> call, Response<com.app.fimtale.model.ReadProgress> response) {}
            @Override public void onFailure(Call<com.app.fimtale.model.ReadProgress> call, Throwable t) {}
        });
    }

    private void updateFontSize(float size) {
        calculatePages();
        if (recyclerView.getVisibility() == View.VISIBLE) {
            prepareVerticalContent();
            recyclerAdapter.updateData(verticalPages);
        }
    }

    private void saveFontSize() {
        prefs.edit().putFloat("reader_font_size", currentFontSize).apply();
    }

    private void parseContent(String content) {
        parsedSegments.clear();
        Pattern imgPattern = Pattern.compile("<img[^>]+src\\s*=\\s*['\"]([^'\"]+)['\"][^>]*>|!\\[.*?\\]\\((.*?)\\)");
        Matcher matcher = imgPattern.matcher(content);
        int lastEnd = 0;
        
        while (matcher.find()) {
            String textPart = content.substring(lastEnd, matcher.start());
            if (!textPart.trim().isEmpty()) {
                parsedSegments.add(new ContentSegment(ReaderPage.TYPE_TEXT, textPart));
            }
            
            String imgSrc = matcher.group(1);
            if (imgSrc == null) {
                imgSrc = matcher.group(2);
            }
            
            if (imgSrc != null && !imgSrc.isEmpty()) {
                parsedSegments.add(new ContentSegment(ReaderPage.TYPE_IMAGE, imgSrc));
            }
            
            lastEnd = matcher.end();
        }
        
        String tail = content.substring(lastEnd);
        if (!tail.trim().isEmpty()) {
            parsedSegments.add(new ContentSegment(ReaderPage.TYPE_TEXT, tail));
        }
        
        if (parsedSegments.isEmpty() && !content.isEmpty()) {
            String finalContent = content;
            parsedSegments.add(new ContentSegment(ReaderPage.TYPE_TEXT, finalContent));
        }
    }

    private void prepareVerticalContent() {
        verticalPages.clear();
        paragraphStartOffsets.clear();
        chapterVerticalIndices.clear();
        
        if (fullChapterContent.equals("加载中...")) {
            verticalPages.add(new ReaderPage(ReaderPage.TYPE_LOADING, null, currentTopicId));
            if (recyclerAdapter != null) {
                recyclerAdapter.notifyDataSetChanged();
            }
            return;
        }

        if (getPrevChapterId() != -1) {
            verticalPages.add(new ReaderPage(ReaderPage.TYPE_PREV_CHAPTER_TRIGGER, null, -1));
        }

        int currentOffset = 0;
        
        chapterVerticalIndices.add(verticalPages.size());
        
        float lineSpacing = UserPreferences.getLineSpacing(this);
        String paragraphSpacing = "\n";

        verticalPages.add(new ReaderPage(ReaderPage.TYPE_TEXT, chapterTitle + paragraphSpacing, currentTopicId));
        paragraphStartOffsets.add(currentOffset);
        currentOffset += chapterTitle.length() + paragraphSpacing.length();

        for (ContentSegment segment : parsedSegments) {
            if (segment.type == ReaderPage.TYPE_TEXT) {
                String[] paragraphs = segment.content.split("\n");
                for (String paragraph : paragraphs) {
                    if (!paragraph.trim().isEmpty()) {
                        verticalPages.add(new ReaderPage(ReaderPage.TYPE_TEXT, "\u3000\u3000" + paragraph.trim(), currentTopicId));
                        paragraphStartOffsets.add(currentOffset);
                        currentOffset += paragraph.length() + 1;
                    }
                }
            } else if (segment.type == ReaderPage.TYPE_IMAGE) {
                verticalPages.add(new ReaderPage(ReaderPage.TYPE_IMAGE, segment.content, currentTopicId));
                paragraphStartOffsets.add(currentOffset);
                currentOffset += 1;
            }
        }
        
        verticalPages.add(new ReaderPage(ReaderPage.TYPE_COMMENT, null, currentTopicId));
        paragraphStartOffsets.add(currentOffset);
        
        if (getNextChapterId() != -1) {
            verticalPages.add(new ReaderPage(ReaderPage.TYPE_NEXT_CHAPTER_TRIGGER, null, -1));
        }
        
        if (recyclerAdapter != null) {
            recyclerAdapter.notifyDataSetChanged();
        }
    }

    private void updatePageMode(boolean isVertical) {
        if (isVertical) {
            viewPager.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            if (scrollProgressBar != null) {
                if (UserPreferences.isShowReaderProgress(this)) {
                    scrollProgressBar.setVisibility(View.VISIBLE);
                } else {
                    scrollProgressBar.setVisibility(View.GONE);
                }
            }
            
            applyReaderTheme(UserPreferences.getReaderTheme(this));
            
            prepareVerticalContent();
            recyclerAdapter.updateData(verticalPages);
            
            recyclerView.post(() -> {
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager != null) {
                    int position = layoutManager.findFirstVisibleItemPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        updateCurrentChapterFromParagraph(position);
                    } else {
                        updateCurrentChapterFromParagraph(0);
                    }
                }
            });

        } else {
            recyclerView.setVisibility(View.GONE);
            viewPager.setVisibility(View.VISIBLE);
            if (scrollProgressBar != null) {
                scrollProgressBar.setVisibility(View.GONE);
            }
            
            applyReaderTheme(UserPreferences.getReaderTheme(this));
            
            calculatePages();
            adapter.updateData(pages);
            
            viewPager.post(() -> {
                int currentItem = viewPager.getCurrentItem();
                updateCurrentChapterFromPage(currentItem);
            });
        }
    }
    
    private void applyReaderTheme(int theme) {
        int bgColor;
        int textColor;
        boolean isDark;
        
        switch (theme) {
            case 1: // 纸黄
                bgColor = Color.parseColor("#E4E2CC");
                textColor = Color.BLACK;
                isDark = false;
                break;
            case 2: // 抹茶
                bgColor = Color.parseColor("#E1EED5");
                textColor = Color.BLACK;
                isDark = false;
                break;
            case 3: // 海蓝
                bgColor = Color.parseColor("#D5E3EF");
                textColor = Color.BLACK;
                isDark = false;
                break;
            default: // 默认
                bgColor = getThemeColor(android.R.attr.colorBackground);
                textColor = getThemeColor(com.google.android.material.R.attr.colorOnBackground);
                int nightModeFlags = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
                isDark = (nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES);
                break;
        }

        findViewById(android.R.id.content).setBackgroundColor(bgColor);
        viewPager.setBackgroundColor(bgColor);
        recyclerView.setBackgroundColor(bgColor);
        
        boolean isVertical = recyclerView.getVisibility() == View.VISIBLE;
        if (theme != 0 || isVertical) {
            readerHeader.setBackgroundColor(bgColor);
            readerFooter.setBackgroundColor(bgColor);
        } else {
            readerHeader.setBackground(null);
            readerFooter.setBackground(null);
        }

        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            boolean useLightBar = !isDark;
            if (theme >= 1 && theme <= 3) useLightBar = true;
            
            controller.setAppearanceLightStatusBars(useLightBar);
            controller.setAppearanceLightNavigationBars(useLightBar);
        }

        if (tvChapterTitle != null) tvChapterTitle.setTextColor(textColor);
        if (tvChapterProgress != null) tvChapterProgress.setTextColor(textColor);
        if (tvBatteryLevel != null) tvBatteryLevel.setTextColor(textColor);
        if (tcSystemTime != null) tcSystemTime.setTextColor(textColor);
        
        if (ivCharging != null) {
            ivCharging.setColorFilter(textColor);
        }
        if (ivBatteryFrame != null) {
            ivBatteryFrame.setColorFilter(textColor);
        }
        if (viewBatteryLevel != null) {
            viewBatteryLevel.setBackgroundTintList(android.content.res.ColorStateList.valueOf(textColor));
        }
        
        if (adapter != null) adapter.notifyDataSetChanged();
        if (recyclerAdapter != null) recyclerAdapter.notifyDataSetChanged();
    }

    private int getThemeColor(int attr) {
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(attr, typedValue, true);
        return typedValue.data;
    }

    private void calculatePages() {
        int width = viewPager.getWidth();
        int height = viewPager.getHeight();

        if (fullChapterContent.equals("加载中...")) {
            pages.clear();
            pages.add(new ReaderPage(ReaderPage.TYPE_LOADING, null, currentTopicId));
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
            return;
        }

        if (width > 0 && height > 0) {
            lastWidth = width;
            lastHeight = height;
        } else if (lastWidth > 0 && lastHeight > 0) {
            width = lastWidth;
            height = lastHeight;
        } else {
            width = getResources().getDisplayMetrics().widthPixels;
            height = getResources().getDisplayMetrics().heightPixels;
            lastWidth = width;
            lastHeight = height;
        }
        
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        int contentWidth = width - padding * 2;
        int contentHeight = height - viewPager.getPaddingTop() - viewPager.getPaddingBottom() - padding * 2;

        if (contentWidth <= 0 || contentHeight <= 0) return;

        TextPaint paint = new TextPaint();
        paint.setTextSize(currentFontSize * getResources().getDisplayMetrics().scaledDensity);
        paint.setAntiAlias(true);
        paint.setColor(getResources().getColor(android.R.color.primary_text_dark, getTheme()));

        pages.clear();
        chapterStartPageIndices.clear();
        pageStartOffsets.clear();

        if (getPrevChapterId() != -1) {
            pages.add(new ReaderPage(ReaderPage.TYPE_PREV_CHAPTER_TRIGGER, null, -1));
            pageStartOffsets.add(0);
        }
        
        int globalOffset = 0;

        chapterStartPageIndices.add(pages.size());
        
        List<ContentSegment> allSegments = new ArrayList<>();
        if (!parsedSegments.isEmpty() && parsedSegments.get(0).type == ReaderPage.TYPE_TEXT) {
             String combinedContent = chapterTitle + "\n\n" + parsedSegments.get(0).content;
             allSegments.add(new ContentSegment(ReaderPage.TYPE_TEXT, combinedContent));
             allSegments.addAll(parsedSegments.subList(1, parsedSegments.size()));
        } else {
             allSegments.add(new ContentSegment(ReaderPage.TYPE_TEXT, chapterTitle + "\n\n"));
             allSegments.addAll(parsedSegments);
        }

        float lineSpacingMultiplier = UserPreferences.getLineSpacing(this);

        for (ContentSegment segment : allSegments) {
            if (segment.type == ReaderPage.TYPE_TEXT) {
                String formattedContent = segment.content.replaceAll("(?m)^(?=.)", "\u3000\u3000");
                
                StaticLayout layout = StaticLayout.Builder.obtain(formattedContent, 0, formattedContent.length(), paint, contentWidth)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(0f, lineSpacingMultiplier)
                        .setIncludePad(false)
                        .build();

                int startLine = 0;
                while (startLine < layout.getLineCount()) {
                    int lineTop = layout.getLineTop(startLine);
                    int endLine = layout.getLineForVertical(lineTop + contentHeight);
                    
                    if (layout.getLineBottom(endLine) > lineTop + contentHeight) {
                        endLine--;
                    }
                    
                    if (endLine < startLine) endLine = startLine;
                    
                    int startOffset = layout.getLineStart(startLine);
                    int endOffset = layout.getLineEnd(endLine);
                    
                    if (endOffset > startOffset) {
                        String pageContent = formattedContent.substring(startOffset, endOffset);
                        boolean isLastPage = (endLine >= layout.getLineCount() - 1);
                        
                        if (!isLastPage || !pageContent.trim().isEmpty()) {
                            pages.add(new ReaderPage(ReaderPage.TYPE_TEXT, pageContent, currentTopicId));
                            pageStartOffsets.add(globalOffset + startOffset);
                        }
                    }
                    
                    startLine = endLine + 1;
                }
                globalOffset += formattedContent.length();
            } else if (segment.type == ReaderPage.TYPE_IMAGE) {
                pages.add(new ReaderPage(ReaderPage.TYPE_IMAGE, segment.content, currentTopicId));
                pageStartOffsets.add(globalOffset);
                globalOffset += 1;
            }
        }
        
        pages.add(new ReaderPage(ReaderPage.TYPE_COMMENT, null, currentTopicId));
        pageStartOffsets.add(globalOffset);
        
        if (getNextChapterId() != -1) {
            pages.add(new ReaderPage(ReaderPage.TYPE_NEXT_CHAPTER_TRIGGER, null, -1));
            pageStartOffsets.add(globalOffset);
        }
        
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void toggleMenu() {
        if (isMenuVisible) {
            hideMenu();
        } else {
            showMenu();
        }
    }

    private void animateButtonColor(TextView tv, int fromColor, int toColor) {
        ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), fromColor, toColor);
        colorAnimation.setDuration(250);
        colorAnimation.addUpdateListener(animator -> {
            int color = (int) animator.getAnimatedValue();
            tv.setTextColor(color);
            tv.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(color));
        });
        colorAnimation.start();
    }

    private void updateBottomMenuButtons() {
        int primaryColor = getThemeColor(com.google.android.material.R.attr.colorPrimary);
        int normalColor = getThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant);

        if (btnChapterList instanceof TextView) {
            TextView tv = (TextView) btnChapterList;
            int targetColor = (chapterListPanel.getVisibility() == View.VISIBLE) ? primaryColor : normalColor;
            int currentColor = tv.getCurrentTextColor();
            if (currentColor != targetColor) {
                animateButtonColor(tv, currentColor, targetColor);
            }
        }

        if (btnSettings instanceof TextView) {
            TextView tv = (TextView) btnSettings;
            int targetColor = (settingsPanel.getVisibility() == View.VISIBLE) ? primaryColor : normalColor;
            int currentColor = tv.getCurrentTextColor();
            if (currentColor != targetColor) {
                animateButtonColor(tv, currentColor, targetColor);
            }
        }
    }

    private void toggleSettingsPanel() {
        if (chapterListPanel.getVisibility() == View.VISIBLE) {
            chapterListPanel.setVisibility(View.GONE);
        }

        if (settingsPanel.getVisibility() == View.VISIBLE) {
            TransitionManager.beginDelayedTransition(bottomSheetContainer);
            settingsPanel.setVisibility(View.GONE);
        } else {
            TransitionManager.beginDelayedTransition(bottomSheetContainer);
            settingsPanel.setVisibility(View.VISIBLE);
        }
        updateBottomMenuButtons();
    }

    private void toggleChapterList() {
        if (settingsPanel.getVisibility() == View.VISIBLE) {
            settingsPanel.setVisibility(View.GONE);
        }

        if (chapterListPanel.getVisibility() == View.VISIBLE) {
            TransitionManager.beginDelayedTransition(bottomSheetContainer);
            chapterListPanel.setVisibility(View.GONE);
        } else {
            TransitionManager.beginDelayedTransition(bottomSheetContainer);
            chapterListPanel.setVisibility(View.VISIBLE);
            if (!filteredChapterList.isEmpty()) {
                int currentIndex = -1;
                for (int i = 0; i < filteredChapterList.size(); i++) {
                    if (filteredChapterList.get(i).getId() == currentTopicId) {
                        currentIndex = i;
                        break;
                    }
                }
                if (currentIndex != -1) {
                    int finalIndex = currentIndex;
                    rvChapterList.post(() -> {
                        LinearLayoutManager layoutManager = (LinearLayoutManager) rvChapterList.getLayoutManager();
                        if (layoutManager != null) {
                            layoutManager.scrollToPositionWithOffset(finalIndex, 0);
                        }
                    });
                }
            }
        }
        updateBottomMenuButtons();
    }

    private void showMenu() {
        menuOverlay.setVisibility(View.VISIBLE);
        dimLayer.setClickable(true);
        dimLayer.animate().alpha(1f).setDuration(300).start();
        
        showSystemUI();
        
        topToolbar.animate().translationY(0).setDuration(300).start();
        bottomSheetContainer.animate().translationY(0).setDuration(300).start();
        
        isMenuVisible = true;
    }

    private void hideMenu() {
        dimLayer.setClickable(false);
        dimLayer.animate().alpha(0f).setDuration(300).start();
        
        topToolbar.animate().translationY(-topToolbar.getBottom()).setDuration(300).start();
        
        bottomSheetContainer.animate().translationY(menuOverlay.getHeight() - bottomSheetContainer.getTop()).setDuration(300)
                .withEndAction(() -> {
                    settingsPanel.setVisibility(View.GONE);
                    chapterListPanel.setVisibility(View.GONE);
                    updateBottomMenuButtons();
                    hideSystemUI();
                }).start();
        
        isMenuVisible = false;
    }

    private void hideSystemUI() {
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    private void showSystemUI() {
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.show(WindowInsetsCompat.Type.systemBars());
        controller.setAppearanceLightStatusBars(false);
    }

    private class ReaderAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private List<ReaderPage> data;
        private boolean isVerticalMode;

        ReaderAdapter(List<ReaderPage> data, boolean isVerticalMode) {
            this.data = data;
            this.isVerticalMode = isVerticalMode;
        }
        
        @Override
        public int getItemViewType(int position) {
            return data.get(position).type;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == ReaderPage.TYPE_LOADING || viewType == ReaderPage.TYPE_NEXT_CHAPTER_TRIGGER || viewType == ReaderPage.TYPE_PREV_CHAPTER_TRIGGER) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_reader_loading, parent, false);
                return new LoadingViewHolder(view);
            } else if (viewType == ReaderPage.TYPE_COMMENT) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_reader_comment_page, parent, false);
                
                if (isVerticalMode) {
                    ViewGroup.LayoutParams params = view.getLayoutParams();
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    view.setLayoutParams(params);
                    
                    RecyclerView rv = view.findViewById(R.id.rvRecommendedTopics);
                    LinearLayout.LayoutParams rvParams = (LinearLayout.LayoutParams) rv.getLayoutParams();
                    rvParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    rvParams.weight = 0;
                    rv.setLayoutParams(rvParams);
                }
                
                return new CommentViewHolder(view);
            } else if (viewType == ReaderPage.TYPE_IMAGE) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_reader_image, parent, false);
                
                if (isVerticalMode) {
                    ViewGroup.LayoutParams params = view.getLayoutParams();
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    view.setLayoutParams(params);
                } else {
                    ViewGroup.LayoutParams params = view.getLayoutParams();
                    params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                    view.setLayoutParams(params);
                }
                
                return new ImageViewHolder(view);
            } else {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_reader_page, parent, false);
                
                if (isVerticalMode) {
                    ViewGroup.LayoutParams params = view.getLayoutParams();
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    view.setLayoutParams(params);
                    
                    TextView textView = view.findViewById(R.id.pageContentTextView);
                    ViewGroup.LayoutParams textParams = textView.getLayoutParams();
                    textParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    textView.setLayoutParams(textParams);
                    
                    int horizontalPadding = (int) (24 * parent.getContext().getResources().getDisplayMetrics().density);
                    float lineSpacing = UserPreferences.getLineSpacing(parent.getContext());
                    int bottomPadding = (int) (16 * lineSpacing * parent.getContext().getResources().getDisplayMetrics().density);
                    textView.setPadding(horizontalPadding, 0, horizontalPadding, bottomPadding);
                }
                
                return new TextViewHolder(view);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ReaderPage page = data.get(position);
            
            View.OnTouchListener touchListener = (v, event) -> {
                if (gestureDetector != null) {
                    return gestureDetector.onTouchEvent(event);
                }
                return false;
            };
            
            if (holder instanceof CommentViewHolder) {
                CommentViewHolder commentHolder = (CommentViewHolder) holder;
                commentHolder.bind(page.chapterId);
            } else if (holder instanceof TextViewHolder) {
                TextViewHolder textHolder = (TextViewHolder) holder;
                textHolder.textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentFontSize);
                float lineSpacing = UserPreferences.getLineSpacing(ReaderActivity.this);
                textHolder.textView.setLineSpacing(0, lineSpacing);
                
                int theme = UserPreferences.getReaderTheme(ReaderActivity.this);
                if (theme >= 1 && theme <= 3) {
                    textHolder.textView.setTextColor(Color.BLACK);
                } else {
                    textHolder.textView.setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnBackground));
                }

                int spacingPx = (int) (16 * lineSpacing * holder.itemView.getContext().getResources().getDisplayMetrics().density);
                
                if (isVerticalMode) {
                    int horizontalPadding = (int) (24 * holder.itemView.getContext().getResources().getDisplayMetrics().density);
                    textHolder.textView.setPadding(horizontalPadding, 0, horizontalPadding, spacingPx);
                } else {
                    ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) textHolder.textView.getLayoutParams();
                    lp.bottomMargin = spacingPx;
                    textHolder.textView.setLayoutParams(lp);
                }

                if (markwon != null) {
                    markwon.setMarkdown(textHolder.textView, page.content);
                } else {
                    textHolder.textView.setText(page.content);
                }
                textHolder.textView.setOnTouchListener(touchListener);
                textHolder.itemView.setOnTouchListener(touchListener);
            } else if (holder instanceof ImageViewHolder) {
                ImageViewHolder imageHolder = (ImageViewHolder) holder;
                
                int cornerRadius = (int) (12 * holder.itemView.getContext().getResources().getDisplayMetrics().density);
                
                try {
                    Glide.with(imageHolder.imageView.getContext())
                            .load(page.content)
                            .apply(RequestOptions.bitmapTransform(new RoundedCorners(cornerRadius)))
                            .placeholder(R.drawable.placeholder_image)
                            .error(android.R.drawable.ic_menu_report_image)
                            .into(imageHolder.imageView);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                imageHolder.itemView.setOnTouchListener(touchListener);
            } else if (holder instanceof LoadingViewHolder) {
                LoadingViewHolder loadingHolder = (LoadingViewHolder) holder;

                int theme = UserPreferences.getReaderTheme(ReaderActivity.this);
                int textColor;
                if (theme >= 1 && theme <= 3) {
                    textColor = Color.BLACK;
                } else {
                    textColor = getThemeColor(com.google.android.material.R.attr.colorOnBackground);
                }
                loadingHolder.tvLoading.setTextColor(textColor);
                if (loadingHolder.pbLoading != null) {
                    loadingHolder.pbLoading.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(textColor));
                }
                
                if (page.type == ReaderPage.TYPE_LOADING) {
                    loadingHolder.tvLoading.setText("加载中...");
                    if (loadingHolder.pbLoading != null) loadingHolder.pbLoading.setVisibility(View.VISIBLE);
                    loadingHolder.itemView.setOnClickListener(null);
                } else if (page.type == ReaderPage.TYPE_NEXT_CHAPTER_TRIGGER) {
                    if (loadingHolder.pbLoading != null) loadingHolder.pbLoading.setVisibility(View.GONE);
                    if (isVerticalMode) {
                        loadingHolder.tvLoading.setText("点击跳转下一章");
                        TypedValue typedValue = new TypedValue();
                        holder.itemView.getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true);
                        loadingHolder.itemView.setBackgroundResource(typedValue.resourceId);
                        
                        ViewGroup.LayoutParams params = loadingHolder.itemView.getLayoutParams();
                        if (params != null) {
                            params.height = (int) (60 * holder.itemView.getContext().getResources().getDisplayMetrics().density);
                            loadingHolder.itemView.setLayoutParams(params);
                        }
                        
                        loadingHolder.itemView.setOnClickListener(v -> {
                            if (!isLoadingChapter) {
                                int nextId = getNextChapterId();
                                if (nextId != -1) {
                                    jumpToChapter(nextId);
                                } else {
                                    Toast.makeText(ReaderActivity.this, "没有下一章了", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    } else {
                        loadingHolder.tvLoading.setText("加载中...");
                        loadingHolder.itemView.setOnClickListener(null);
                        loadingHolder.itemView.setBackground(null);
                    }
                } else if (page.type == ReaderPage.TYPE_PREV_CHAPTER_TRIGGER) {
                    if (loadingHolder.pbLoading != null) loadingHolder.pbLoading.setVisibility(View.GONE);
                    if (isVerticalMode) {
                        loadingHolder.tvLoading.setText("点击跳转上一章");
                        TypedValue typedValue = new TypedValue();
                        holder.itemView.getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true);
                        loadingHolder.itemView.setBackgroundResource(typedValue.resourceId);

                        ViewGroup.LayoutParams params = loadingHolder.itemView.getLayoutParams();
                        if (params != null) {
                            params.height = (int) (60 * holder.itemView.getContext().getResources().getDisplayMetrics().density);
                            loadingHolder.itemView.setLayoutParams(params);
                        }

                        loadingHolder.itemView.setOnClickListener(v -> {
                            if (!isLoadingChapter) {
                                int prevId = getPrevChapterId();
                                if (prevId != -1) {
                                    jumpToChapter(prevId, true);
                                } else {
                                    Toast.makeText(ReaderActivity.this, "没有上一章了", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    } else {
                        loadingHolder.tvLoading.setText("加载中...");
                        loadingHolder.itemView.setOnClickListener(null);
                        loadingHolder.itemView.setBackground(null);
                    }
                }
            }
        }

        @Override
        public int getItemCount() {
            return data.size();
        }
        
        public void updateData(List<ReaderPage> newData) {
            this.data = newData;
            notifyDataSetChanged();
        }

        class TextViewHolder extends RecyclerView.ViewHolder {
            TextView textView;

            TextViewHolder(View itemView) {
                super(itemView);
                textView = itemView.findViewById(R.id.pageContentTextView);
            }
        }

        class ImageViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView;

            ImageViewHolder(View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.ivReaderImage);
            }
        }

        class LoadingViewHolder extends RecyclerView.ViewHolder {
            TextView tvLoading;
            ProgressBar pbLoading;
            LoadingViewHolder(View itemView) {
                super(itemView);
                tvLoading = itemView.findViewById(R.id.tvLoading);
                pbLoading = itemView.findViewById(R.id.pbLoading);
            }
        }
        
        class CommentViewHolder extends RecyclerView.ViewHolder {
            TextView tvChapterTitle;
            RecyclerView rvRecommendedTopics;
            TextView tvContinueRead;
            ChapterListAdapter nextChaptersAdapter;
            
            CommentViewHolder(View itemView) {
                super(itemView);
                tvChapterTitle = itemView.findViewById(R.id.tvChapterTitle);
                rvRecommendedTopics = itemView.findViewById(R.id.rvRecommendedTopics);
                tvContinueRead = itemView.findViewById(R.id.tvContinueRead);
                
                rvRecommendedTopics.setLayoutManager(new LinearLayoutManager(itemView.getContext()) {
                    @Override
                    public boolean canScrollVertically() {
                        return !isVerticalMode;
                    }
                });
                
                nextChaptersAdapter = new ChapterListAdapter(true);
                rvRecommendedTopics.setAdapter(nextChaptersAdapter);
            }
            
            void bind(int chapterId) {
                // 根据模式调整可见性和样式
                int theme = UserPreferences.getReaderTheme(ReaderActivity.this);
                int textColor;
                if (theme >= 1 && theme <= 3) {
                    textColor = Color.BLACK;
                } else {
                    textColor = getThemeColor(com.google.android.material.R.attr.colorOnSurface);
                }

                if (isVerticalMode) {
                    if (tvChapterTitle != null) tvChapterTitle.setVisibility(View.GONE);
                    if (rvRecommendedTopics != null) rvRecommendedTopics.setVisibility(View.GONE);
                } else {
                    if (tvChapterTitle != null) {
                        tvChapterTitle.setVisibility(View.VISIBLE);
                        tvChapterTitle.setText("章节列表");
                        tvChapterTitle.setTextColor(textColor);
                    }
                    if (rvRecommendedTopics != null) rvRecommendedTopics.setVisibility(View.VISIBLE);
                }

                int nextChapterId = getNextChapterId();
                tvContinueRead.setVisibility(View.VISIBLE);
                tvContinueRead.setTextColor(textColor);
                
                if (nextChoices().size() > 1) {
                    tvContinueRead.setText("选择剧情分支");
                    tvContinueRead.setOnClickListener(v -> showBranchChoices());
                } else if (nextChapterId != -1) {
                    if (isVerticalMode) {
                        tvContinueRead.setVisibility(View.GONE);
                    } else {
                        tvContinueRead.setText("左滑进入下一章");
                        tvContinueRead.setOnClickListener(v -> jumpToChapter(nextChapterId));
                        tvContinueRead.setVisibility(View.VISIBLE);
                    }
                } else {
                    tvContinueRead.setText("当前为最后一章");
                    tvContinueRead.setOnClickListener(null);
                    tvContinueRead.setVisibility(View.VISIBLE);
                }
                
                if (!isVerticalMode) {
                    ViewGroup.LayoutParams params = tvContinueRead.getLayoutParams();
                    if (params != null) {
                        params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                        tvContinueRead.setLayoutParams(params);
                    }
                    tvContinueRead.setGravity(android.view.Gravity.NO_GRAVITY);
                    tvContinueRead.setPadding(0, 0, 0, 0);
                    tvContinueRead.setTextSize(14);
                }
                
                if (!isVerticalMode) {
                    nextChaptersAdapter.updateData(filteredChapterList);
                    
                    int currentIndex = -1;
                    for (int i = 0; i < filteredChapterList.size(); i++) {
                        if (filteredChapterList.get(i).getId() == currentTopicId) {
                            currentIndex = i;
                            break;
                        }
                    }
                    
                    if (currentIndex != -1) {
                        rvRecommendedTopics.scrollToPosition(currentIndex);
                    }
                }
            }
        }
    }
    
    private static double clamp01(double v) {
        if (v < 0d) return 0d;
        if (v > 1d) return 1d;
        return v;
    }
    
    private boolean shouldApplyInitialProgress(int topicId) {
        return !initialProgressApplied && initialProgress >= 0d && topicId == initialTopicId;
    }
    
    private int computeTargetPagedIndex(double progress01) {
        if (pages == null || pages.isEmpty()) return 0;
        int startOffset = (!pages.isEmpty() && pages.get(0).type == ReaderPage.TYPE_PREV_CHAPTER_TRIGGER) ? 1 : 0;
        int endOffset = (!pages.isEmpty() && pages.get(pages.size() - 1).type == ReaderPage.TYPE_NEXT_CHAPTER_TRIGGER) ? 1 : 0;
        int realTotal = pages.size() - startOffset - endOffset;
        if (realTotal <= 0) return startOffset;
        
        int realIndex = (int) Math.floor(clamp01(progress01) * realTotal);
        if (realIndex >= realTotal) realIndex = realTotal - 1;
        if (realIndex < 0) realIndex = 0;
        return startOffset + realIndex;
    }
    
    private int computeTargetVerticalIndex(double progress01) {
        if (verticalPages == null || verticalPages.isEmpty()) return 0;
        int startOffset = (!verticalPages.isEmpty() && verticalPages.get(0).type == ReaderPage.TYPE_PREV_CHAPTER_TRIGGER) ? 1 : 0;
        int endOffset = (!verticalPages.isEmpty() && verticalPages.get(verticalPages.size() - 1).type == ReaderPage.TYPE_NEXT_CHAPTER_TRIGGER) ? 1 : 0;
        int realTotal = verticalPages.size() - startOffset - endOffset;
        if (realTotal <= 0) return startOffset;
        
        int realIndex = (int) Math.floor(clamp01(progress01) * realTotal);
        if (realIndex >= realTotal) realIndex = realTotal - 1;
        if (realIndex < 0) realIndex = 0;
        return startOffset + realIndex;
    }
    
    private List<TopicDetailResponse.ChapterEdge> nextChoices() {
        return com.app.fimtale.model.ChapterNavigation.choices(workData, currentTopicId);
    }
    private int getNextChapterId() {
        List<TopicDetailResponse.ChapterEdge> choices = nextChoices();
        return choices.size() == 1 ? (choices.get(0).to == null ? 0 : choices.get(0).to) : -1;
    }
    private int getPrevChapterId() {
        return com.app.fimtale.model.ChapterNavigation.previous(workData, currentTopicId);
    }
    private void showBranchChoices() {
        List<TopicDetailResponse.ChapterEdge> choices = nextChoices();
        String[] labels = new String[choices.size()];
        for (int i = 0; i < choices.size(); i++) {
            TopicDetailResponse.ChapterEdge edge = choices.get(i);
            labels[i] = edge.label == null || edge.label.isEmpty() ? "继续阅读" : edge.label;
        }
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("选择剧情分支")
                .setItems(labels, (dialog, which) -> jumpToChapter(choices.get(which).to == null ? 0 : choices.get(which).to)).show();
    }

    private long[] cachedWeights;
    private long cachedTotalWeight = 0;
    private float lastPercentValue = -1f;

    private void ensureWeightsCalculated() {
        if (cachedWeights != null && cachedWeights.length == verticalPages.size()) return;
        
        cachedWeights = new long[verticalPages.size()];
        cachedTotalWeight = 0;
        for (int i = 0; i < verticalPages.size(); i++) {
            ReaderPage page = verticalPages.get(i);
            long weight;
            switch (page.type) {
                case ReaderPage.TYPE_TEXT:
                    weight = page.content != null ? page.content.length() : 10;
                    break;
                case ReaderPage.TYPE_IMAGE:
                    weight = 400;
                    break;
                case ReaderPage.TYPE_COMMENT:
                    weight = 800;
                    break;
                default:
                    weight = 100;
                    break;
            }
            cachedWeights[i] = weight;
            cachedTotalWeight += weight;
        }
    }

    private float calculateContentBasedPercent(LinearLayoutManager layoutManager, int firstPos, int lastPos) {
        if (verticalPages.isEmpty()) return 0f;
        ensureWeightsCalculated();
        if (cachedTotalWeight == 0) return 0f;

        double currentWeight = 0;
        for (int i = 0; i < firstPos; i++) {
            currentWeight += cachedWeights[i];
        }

        View firstView = layoutManager.findViewByPosition(firstPos);
        if (firstView != null) {
            float itemHeight = firstView.getHeight();
            float itemTop = firstView.getTop();
            float scrolledRate = itemHeight > 0 ? -itemTop / itemHeight : 0;
            currentWeight += cachedWeights[firstPos] * scrolledRate;
        }
        
        int offset = recyclerView.computeVerticalScrollOffset();
        int range = recyclerView.computeVerticalScrollRange();
        int extent = recyclerView.computeVerticalScrollExtent();
        
        float percent;
        if (range > extent) {
            percent = (float) offset * 100 / (range - extent);
        } else {
            percent = 100f;
        }

        if (lastPercentValue >= 0) {
            percent = lastPercentValue + 0.3f * (percent - lastPercentValue);
        }
        lastPercentValue = percent;
        
        return percent;
    }

    private class ChapterListAdapter extends RecyclerView.Adapter<ChapterListAdapter.ViewHolder> {
        private List<ChapterMenuItem> displayList = new ArrayList<>();
        private boolean isReaderContent;

        public ChapterListAdapter() {
            this.isReaderContent = false;
        }

        public ChapterListAdapter(boolean isReaderContent) {
            this.isReaderContent = isReaderContent;
        }

        public void updateData(List<ChapterMenuItem> newData) {
            displayList.clear();
            if (newData != null) {
                displayList.addAll(newData);
            }
            notifyDataSetChanged();
        }
        
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView view = new TextView(parent.getContext());
            view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            view.setMinHeight((int) (56 * parent.getContext().getResources().getDisplayMetrics().density));
            view.setGravity(android.view.Gravity.CENTER_VERTICAL);
            
            int paddingHorizontal = (int) (16 * parent.getContext().getResources().getDisplayMetrics().density);
            int paddingVertical = (int) (12 * parent.getContext().getResources().getDisplayMetrics().density);
            view.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical);
            
            view.setTextSize(16);
            
            TypedValue typedValue = new TypedValue();
            parent.getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true);
            view.setBackgroundResource(typedValue.resourceId);
            
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ChapterMenuItem item = displayList.get(position);
            holder.textView.setText(item.getTitle());
            
            TypedValue typedValue = new TypedValue();
            if (item.getId() == currentTopicId) {
                getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true);
                holder.textView.setTextColor(typedValue.data);
            } else {
                if (isReaderContent) {
                    int theme = UserPreferences.getReaderTheme(ReaderActivity.this);
                    if (theme >= 1 && theme <= 3) {
                        holder.textView.setTextColor(Color.BLACK);
                    } else {
                        getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnBackground, typedValue, true);
                        holder.textView.setTextColor(typedValue.data);
                    }
                } else {
                    getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true);
                    holder.textView.setTextColor(typedValue.data);
                }
            }
            
            holder.itemView.setOnClickListener(v -> {
                jumpToChapter(item.getId());
                hideMenu();
            });
        }

        @Override
        public int getItemCount() {
            return displayList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView textView;
            ViewHolder(TextView itemView) {
                super(itemView);
                textView = itemView;
            }
        }
    }
}
