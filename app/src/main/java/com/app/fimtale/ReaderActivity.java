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
import android.graphics.Typeface;
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
import com.app.fimtale.model.ChapterMenuItem;
import com.app.fimtale.model.Comment;
import com.app.fimtale.model.Topic;
import com.app.fimtale.model.TopicDetailResponse;
import com.app.fimtale.model.TopicInfo;
import com.app.fimtale.model.TopicListResponse;
import com.app.fimtale.model.TopicViewItem;
import com.app.fimtale.network.RetrofitClient;
import com.app.fimtale.ui.ReaderCommentsPanel;
import com.app.fimtale.utils.UserPreferences;
import android.widget.ProgressBar;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.image.glide.GlideImagesPlugin;
import okhttp3.ResponseBody;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.slider.Slider;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
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
    private BottomSheetDialog readerCommentsSheet;
    private ReaderCommentsPanel readerCommentsSheetPanel;

    /** Chapters currently joined into one continuous reader data set. */
    private final List<LoadedChapter> loadedChapters = new ArrayList<>();
    private final Set<Integer> loadingChapterIds = new HashSet<>();

    private static class LoadedChapter {
        final int id;
        final String title;
        final String content;
        final List<ContentSegment> segments;

        LoadedChapter(int id, String title, String content, List<ContentSegment> segments) {
            this.id = id;
            this.title = title == null ? "" : title;
            this.content = content == null ? "" : content;
            this.segments = segments == null ? new ArrayList<>() : segments;
        }
    }

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
        static final int TYPE_TITLE = 6;
        
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
            if (item.getItemId() == R.id.action_edit_content) {
                if (workData != null && editorAccess.canEdit(workData.getAuthorInfo())) {
                    startActivity(currentTopicId == 0 ? EditorActivity.workIntent(this, rootTopicId)
                            : EditorActivity.chapterIntent(this, rootTopicId, currentTopicId));
                }
                return true;
            }
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
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) canTriggerChapterChange = true;
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
                    updateCurrentChapterFromParagraph(firstPos);
                    if (scrollProgressBar != null) {
                        float percent = calculateContentBasedPercent(layoutManager, firstPos, lastPos);
                        if (percent > 100) percent = 100;
                        if (percent < 0) percent = 0;
                        scrollProgressBar.setProgress((int) (percent * 10));
                    }
                    ensureAdjacentChapters(currentTopicId);
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
                ensureAdjacentChapters(currentTopicId);
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
    private final com.app.fimtale.editor.AuthoringAccess editorAccess = new com.app.fimtale.editor.AuthoringAccess(this);
    private long editorVersion;

    private void updateEditorMenu() {
        if (topToolbar != null && topToolbar.getMenu().findItem(R.id.action_edit_content) != null)
            topToolbar.getMenu().findItem(R.id.action_edit_content).setVisible(workData != null && editorAccess.canEdit(workData.getAuthorInfo()));
    }

    @Override protected void onResume() {
        super.onResume();
        editorAccess.refresh(this::updateEditorMenu);
        long version = com.app.fimtale.editor.EditorChanges.version(rootTopicId);
        if (version != editorVersion) { editorVersion = version; isLoadingChapter = false; loadWorkNavigation(); }
    }
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
        updateEditorMenu();
        chapterList = data.getMenu();
        filteredChapterList.clear(); filteredChapterList.addAll(chapterList);
        if (chapterListAdapter != null) chapterListAdapter.updateData(filteredChapterList);
    }
    private int cacheKey(int chapterId) { return chapterId == 0 ? -rootTopicId : chapterId; }

    private void fetchChapterContent(int topicId, boolean scrollToEnd) {
        LoadedChapter existing = findLoadedChapter(topicId);
        if (existing != null) {
            activateChapter(existing, scrollToEnd, false);
            ensureAdjacentChapters(topicId);
            return;
        }

        if (loadedChapters.isEmpty()) {
            isLoadingChapter = true;
            contentReady = false;
            fullChapterContent = "加载中...";
            if (viewPager.getVisibility() == View.VISIBLE) calculatePages(); else prepareVerticalContent();
        }
        requestChapter(topicId, true, scrollToEnd);
    }

    private LoadedChapter findLoadedChapter(int chapterId) {
        for (LoadedChapter chapter : loadedChapters) if (chapter.id == chapterId) return chapter;
        return null;
    }

    private LoadedChapter createLoadedChapter(int id, String title, String content) {
        String markdown = com.app.fimtale.utils.BbCode.toMarkdown(content == null ? "" : content);
        if (markdown.isEmpty()) markdown = "无内容";
        return new LoadedChapter(id, title, markdown, parseSegments(markdown));
    }

    private void requestChapter(int chapterId, boolean activate, boolean scrollToEnd) {
        if (loadingChapterIds.contains(chapterId)) return;
        loadingChapterIds.add(chapterId);

        if (chapterId == 0 && workData != null) {
            TopicInfo work = workData.getTopicInfo();
            loadingChapterIds.remove(chapterId);
            acceptLoadedChapter(createLoadedChapter(0, work.getTitle(), work.getContent()), activate, scrollToEnd);
            return;
        }

        CacheManager.getInstance(this).getChapter(cacheKey(chapterId), cached -> {
            if (isFinishing() || isDestroyed()) return;
            if (cached != null && cached.rootTopicId == rootTopicId) {
                loadingChapterIds.remove(chapterId);
                acceptLoadedChapter(createLoadedChapter(cached.postId, cached.title, cached.content), activate, scrollToEnd);
            } else {
                fetchChapterFromNetwork(chapterId, activate, scrollToEnd);
            }
        });
    }

    private void showReadError(String message) {
        contentReady = false;
        isLoadingChapter = false;
        fullChapterContent = message;
        loadedChapters.clear();
        parsedSegments.clear(); parsedSegments.add(new ContentSegment(ReaderPage.TYPE_TEXT, message));
        loadedChapters.add(new LoadedChapter(currentTopicId, "", message, parseSegments(message)));
        prepareVerticalContent(); calculatePages();
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void acceptLoadedChapter(LoadedChapter chapter, boolean activate, boolean scrollToEnd) {
        if (findLoadedChapter(chapter.id) != null) {
            if (activate) activateChapter(findLoadedChapter(chapter.id), scrollToEnd, false);
            return;
        }

        // A directory/branch jump starts a fresh continuous window around the target.
        // Background adjacent loads use activate=false and extend the existing window.
        boolean reset = activate;
        if (reset) loadedChapters.clear();
        insertLoadedChapter(chapter);

        if (activate) {
            activateChapter(chapter, scrollToEnd, false);
        } else {
            rebuildReaderContent(true);
        }
    }

    private void activateChapter(LoadedChapter chapter, boolean scrollToEnd, boolean reset) {
        if (reset) loadedChapters.clear();
        if (findLoadedChapter(chapter.id) == null) insertLoadedChapter(chapter);
        contentReady = true;
        isLoadingChapter = false;
        chapterTitle = chapter.title;
        currentPostId = chapter.id;
        currentTopicId = chapter.id;
        currentProgress = 0;
        fullChapterContent = chapter.content;
        parsedSegments = chapter.segments;
        topToolbar.setTitle(chapterTitle);
        tvChapterTitle.setText(chapterTitle);
        rebuildReaderContent(false);
        positionReader(currentTopicId, scrollToEnd);
        ensureAdjacentChapters(currentTopicId);
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

    private void insertLoadedChapter(LoadedChapter chapter) {
        if (findLoadedChapter(chapter.id) != null) return;
        if (loadedChapters.isEmpty()) { loadedChapters.add(chapter); return; }

        int currentIndex = indexOfLoadedChapter(currentTopicId);
        int previousId = previousChapterId(currentTopicId);
        int nextId = nextChapterId(currentTopicId);
        if (chapter.id == previousId && currentIndex >= 0) {
            loadedChapters.add(currentIndex, chapter);
        } else if (chapter.id == nextId && currentIndex >= 0) {
            loadedChapters.add(currentIndex + 1, chapter);
        } else if (chapter.id == 0) {
            loadedChapters.add(0, chapter);
        } else {
            loadedChapters.add(chapter);
        }
    }

    private int indexOfLoadedChapter(int chapterId) {
        for (int i = 0; i < loadedChapters.size(); i++) if (loadedChapters.get(i).id == chapterId) return i;
        return -1;
    }

    private int nextChapterId(int chapterId) {
        List<TopicDetailResponse.ChapterEdge> choices = com.app.fimtale.model.ChapterNavigation.choices(workData, chapterId);
        return choices.size() == 1 ? (choices.get(0).to == null ? 0 : choices.get(0).to) : -1;
    }

    private int previousChapterId(int chapterId) {
        return com.app.fimtale.model.ChapterNavigation.previous(workData, chapterId);
    }

    private void ensureAdjacentChapters(int chapterId) {
        if (!contentReady || chapterId < 0) return;
        int nextId = nextChapterId(chapterId);
        int previousId = previousChapterId(chapterId);
        if (nextId != -1 && findLoadedChapter(nextId) == null) requestChapter(nextId, false, false);
        if (previousId != -1 && findLoadedChapter(previousId) == null) requestChapter(previousId, false, false);
    }

    private void fetchChapterFromNetwork(int chapterId, boolean activate, boolean scrollToEnd) {
        RetrofitClient.getInstance().getChapter(chapterId).enqueue(new Callback<com.app.fimtale.model.ChapterResponse>() {
            @Override public void onResponse(Call<com.app.fimtale.model.ChapterResponse> call, Response<com.app.fimtale.model.ChapterResponse> response) {
                if (isFinishing() || isDestroyed()) return;
                loadingChapterIds.remove(chapterId);
                com.app.fimtale.model.ChapterResponse data = response.body();
                if (response.isSuccessful() && data != null && data.chapter != null && data.chapter.workId == rootTopicId) {
                    CacheManager.getInstance(ReaderActivity.this).cacheChapter(data.chapter.id, rootTopicId,
                            data.chapter.id, data.chapter.title, data.chapter.content, null);
                    acceptLoadedChapter(createLoadedChapter(data.chapter.id, data.chapter.title, data.chapter.content), activate, scrollToEnd);
                } else if (activate) showReadError("章节加载失败，请从目录重试");
            }
            @Override public void onFailure(Call<com.app.fimtale.model.ChapterResponse> call, Throwable t) {
                loadingChapterIds.remove(chapterId);
                if (activate && !isFinishing() && !isDestroyed()) showReadError("网络错误，请从目录重试");
            }
        });
    }

    private void positionReader(int topicId, boolean scrollToEnd) {
        if (viewPager.getVisibility() == View.VISIBLE) {
            int initialPage = chapterStartIndex(pages, topicId);
            if (!scrollToEnd && shouldApplyInitialProgress(topicId)) {
                initialPage = computeTargetPagedIndex(initialProgress);
                initialProgressApplied = true;
            } else if (scrollToEnd) {
                initialPage = chapterEndIndex(pages, topicId);
            }

            if (initialPage < 0) initialPage = 0;
            if (initialPage >= pages.size()) initialPage = Math.max(0, pages.size() - 1);
            viewPager.setCurrentItem(initialPage, false);
            updateCurrentChapterFromPage(initialPage);
        } else {
            int pos = chapterStartIndex(verticalPages, topicId);
            if (!scrollToEnd && shouldApplyInitialProgress(topicId)) {
                pos = computeTargetVerticalIndex(initialProgress);
                initialProgressApplied = true;
            } else if (scrollToEnd) {
                pos = chapterEndIndex(verticalPages, topicId);
            }

            if (pos < 0) pos = 0;
            if (pos >= verticalPages.size()) pos = Math.max(0, verticalPages.size() - 1);
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

    private int chapterStartIndex(List<ReaderPage> data, int chapterId) {
        for (int i = 0; i < data.size(); i++) if (data.get(i).chapterId == chapterId) return i;
        return data.isEmpty() ? 0 : 0;
    }

    private int chapterEndIndex(List<ReaderPage> data, int chapterId) {
        int start = chapterStartIndex(data, chapterId);
        for (int i = start + 1; i < data.size(); i++) {
            if (data.get(i).chapterId != chapterId) return i - 1;
        }
        return data.isEmpty() ? 0 : data.size() - 1;
    }

    private void rebuildReaderContent(boolean preservePosition) {
        final int preservedTopicId = currentTopicId;
        int oldPage = viewPager == null ? 0 : viewPager.getCurrentItem();
        int oldPageOffset = oldPage - chapterStartIndex(pages, preservedTopicId);
        int oldVerticalPosition = RecyclerView.NO_POSITION;
        int oldVerticalTop = 0;
        if (recyclerView != null && recyclerView.getLayoutManager() instanceof LinearLayoutManager) {
            LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
            oldVerticalPosition = manager.findFirstVisibleItemPosition();
            View first = oldVerticalPosition == RecyclerView.NO_POSITION ? null : manager.findViewByPosition(oldVerticalPosition);
            if (first != null) oldVerticalTop = first.getTop();
        }

        calculatePages();
        prepareVerticalContent();
        if (!preservePosition) return;

        int pageTarget = chapterStartIndex(pages, preservedTopicId) + Math.max(0, oldPageOffset);
        if (viewPager != null && !pages.isEmpty()) {
            viewPager.post(() -> {
                int target = Math.min(pageTarget, pages.size() - 1);
                viewPager.setCurrentItem(target, false);
                updateCurrentChapterFromPage(target);
            });
        }
        if (oldVerticalPosition != RecyclerView.NO_POSITION && !verticalPages.isEmpty()) {
            int verticalOffset = oldVerticalPosition - chapterStartIndex(verticalPages, preservedTopicId);
            int target = chapterStartIndex(verticalPages, preservedTopicId) + Math.max(0, verticalOffset);
            final int savedVerticalTop = oldVerticalTop;
            recyclerView.post(() -> {
                RecyclerView.LayoutManager layout = recyclerView.getLayoutManager();
                if (layout instanceof LinearLayoutManager) {
                    ((LinearLayoutManager) layout).scrollToPositionWithOffset(
                            Math.min(target, verticalPages.size() - 1), savedVerticalTop);
                    updateCurrentChapterFromParagraph(Math.min(target, verticalPages.size() - 1));
                }
            });
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
        saveReadingProgress();
    }

    @Override
    protected void onDestroy() {
        if (readerCommentsSheetPanel != null) readerCommentsSheetPanel.close();
        if (readerCommentsSheet != null) readerCommentsSheet.dismiss();
        editorAccess.close();
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

    private void showReaderCommentsBottomSheet(int chapterId) {
        if (isFinishing() || isDestroyed()) return;
        if (readerCommentsSheet != null && readerCommentsSheet.isShowing()) return;

        View content = getLayoutInflater().inflate(R.layout.item_reader_comment_page, null);
        readerCommentsSheet = new BottomSheetDialog(this);
        readerCommentsSheet.setContentView(content);
        readerCommentsSheetPanel = new ReaderCommentsPanel(this, content, rootTopicId);
        readerCommentsSheetPanel.bind(chapterId);
        readerCommentsSheet.setOnDismissListener(dialog -> {
            if (readerCommentsSheetPanel != null) {
                readerCommentsSheetPanel.close();
                readerCommentsSheetPanel = null;
            }
            readerCommentsSheet = null;
        });
        readerCommentsSheet.show();

        content.post(() -> {
            View parent = content.getParent() instanceof View ? (View) content.getParent() : null;
            if (parent == null) return;
            ViewGroup.LayoutParams params = parent.getLayoutParams();
            if (params != null) {
                params.height = (int) (getResources().getDisplayMetrics().heightPixels * 0.78f);
                parent.setLayoutParams(params);
            }
            try {
                com.google.android.material.bottomsheet.BottomSheetBehavior<View> behavior =
                        com.google.android.material.bottomsheet.BottomSheetBehavior.from(parent);
                behavior.setSkipCollapsed(true);
                behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
            } catch (IllegalArgumentException ignored) {
                // The dialog theme may provide a non-standard bottom sheet parent.
            }
        });
    }

    private String indentLine(String line) {
        if (line == null || line.isEmpty()) return line;
        return line.startsWith("\u3000\u3000") ? line : "\u3000\u3000" + line;
    }

    private String indentLines(String content) {
        if (content == null || content.isEmpty()) return content;
        String[] lines = content.split("\\n", -1);
        StringBuilder result = new StringBuilder(content.length() + lines.length * 2);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) result.append('\n');
            result.append(indentLine(lines[i]));
        }
        return result.toString();
    }

    private void updateCurrentChapterFromPage(int pageIndex) {
        if (pageIndex < 0 || pageIndex >= pages.size()) return;
        ReaderPage page = pages.get(pageIndex);
        LoadedChapter chapter = findLoadedChapter(page.chapterId);
        if (chapter == null) return;
        currentTopicId = chapter.id;
        currentPostId = chapter.id;
        chapterTitle = chapter.title;
        topToolbar.setTitle(chapterTitle);
        int chapterStart = chapterStartIndex(pages, chapter.id);
        int chapterEnd = chapterEndIndex(pages, chapter.id);
        int realTotal = Math.max(1, chapterEnd - chapterStart + 1);
        int realIndex = Math.max(0, Math.min(realTotal - 1, pageIndex - chapterStart));
        if (realTotal > 1) {
            currentProgress = (double) realIndex / (realTotal - 1);
        } else {
            currentProgress = 1.0;
        }
        updateHeader(chapter.title, (realIndex + 1) + "/" + realTotal);
    }

    private void updateCurrentChapterFromParagraph(int paragraphIndex) {
        if (paragraphIndex < 0 || paragraphIndex >= verticalPages.size()) return;
        ReaderPage page = verticalPages.get(paragraphIndex);
        LoadedChapter chapter = findLoadedChapter(page.chapterId);
        if (chapter == null) return;
        currentTopicId = chapter.id;
        currentPostId = chapter.id;
        chapterTitle = chapter.title;
        topToolbar.setTitle(chapterTitle);
        int chapterStart = chapterStartIndex(verticalPages, chapter.id);
        int chapterEnd = chapterEndIndex(verticalPages, chapter.id);
        int count = Math.max(1, chapterEnd - chapterStart + 1);
        int relative = Math.max(0, Math.min(count - 1, paragraphIndex - chapterStart));
        float percent = count > 1 ? (float) relative * 100f / (count - 1) : 100f;
        currentProgress = count > 1 ? (double) relative / (count - 1) : 1.0;
        updateHeader(chapter.title, String.format("%.1f%%", percent));
        if (scrollProgressBar != null) scrollProgressBar.setProgress((int) (percent * 10));
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
        parsedSegments = parseSegments(content);
    }

    private List<ContentSegment> parseSegments(String content) {
        List<ContentSegment> segments = new ArrayList<>();
        if (content == null) content = "";
        Pattern imgPattern = Pattern.compile("<img[^>]+src\\s*=\\s*['\"]([^'\"]+)['\"][^>]*>|!\\[([^\\]]*)\\]\\((.*?)\\)");
        Matcher matcher = imgPattern.matcher(content);
        int lastEnd = 0;
        
        while (matcher.find()) {
            String textPart = content.substring(lastEnd, matcher.start());
            if (!textPart.trim().isEmpty()) segments.add(new ContentSegment(ReaderPage.TYPE_TEXT, textPart));
            
            String imgSrc = matcher.group(1);
            String markdownAlt = matcher.group(2);
            if (imgSrc == null && markdownAlt != null && markdownAlt.matches("ftemoji_[a-zA-Z0-9_]+")) {
                String inlineEmoji = content.substring(matcher.start(), matcher.end());
                if (!inlineEmoji.trim().isEmpty()) segments.add(new ContentSegment(ReaderPage.TYPE_TEXT, inlineEmoji));
                lastEnd = matcher.end();
                continue;
            }
            if (imgSrc == null) imgSrc = matcher.group(3);
            
            if (imgSrc != null && !imgSrc.isEmpty()) {
                segments.add(new ContentSegment(ReaderPage.TYPE_IMAGE, imgSrc));
            }
            
            lastEnd = matcher.end();
        }
        
        String tail = content.substring(lastEnd);
        if (!tail.trim().isEmpty()) segments.add(new ContentSegment(ReaderPage.TYPE_TEXT, tail));
        
        if (segments.isEmpty() && !content.isEmpty()) {
            segments.add(new ContentSegment(ReaderPage.TYPE_TEXT, content));
        }
        return segments;
    }

    private void prepareVerticalContent() {
        verticalPages.clear();
        paragraphStartOffsets.clear();
        chapterVerticalIndices.clear();
        cachedWeights = null;
        
        if (fullChapterContent.equals("加载中...")) {
            verticalPages.add(new ReaderPage(ReaderPage.TYPE_LOADING, null, currentTopicId));
            if (recyclerAdapter != null) {
                recyclerAdapter.notifyDataSetChanged();
            }
            return;
        }

        int currentOffset = 0;
        String paragraphSpacing = "\n";

        for (LoadedChapter chapter : loadedChapters) {
            chapterVerticalIndices.add(verticalPages.size());
            verticalPages.add(new ReaderPage(ReaderPage.TYPE_TITLE, chapter.title + paragraphSpacing, chapter.id));
            paragraphStartOffsets.add(currentOffset);
            currentOffset += chapter.title.length() + paragraphSpacing.length();

            for (ContentSegment segment : chapter.segments) {
                if (segment.type == ReaderPage.TYPE_TEXT) {
                    String[] paragraphs = segment.content.split("\\n");
                    for (String paragraph : paragraphs) {
                        if (!paragraph.trim().isEmpty()) {
                            verticalPages.add(new ReaderPage(ReaderPage.TYPE_TEXT, indentLine(paragraph.trim()), chapter.id));
                            paragraphStartOffsets.add(currentOffset);
                            currentOffset += paragraph.length() + 1;
                        }
                    }
                } else if (segment.type == ReaderPage.TYPE_IMAGE) {
                    verticalPages.add(new ReaderPage(ReaderPage.TYPE_IMAGE, segment.content, chapter.id));
                    paragraphStartOffsets.add(currentOffset);
                    currentOffset += 1;
                }
            }

            // Keep a comments entry between every chapter in continuous mode.
            verticalPages.add(new ReaderPage(ReaderPage.TYPE_COMMENT, null, chapter.id));
            paragraphStartOffsets.add(currentOffset);
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
        int globalOffset = 0;
        float lineSpacingMultiplier = UserPreferences.getLineSpacing(this);

        for (LoadedChapter chapter : loadedChapters) {
            chapterStartPageIndices.add(pages.size());
            TextPaint titlePaint = new TextPaint(paint);
            titlePaint.setTextSize(readerTitleFontSize() * getResources().getDisplayMetrics().scaledDensity);
            titlePaint.setTypeface(Typeface.DEFAULT_BOLD);
            addPagedText(chapter.title, chapter.id, ReaderPage.TYPE_TITLE, titlePaint,
                    contentWidth, contentHeight, lineSpacingMultiplier, globalOffset);
            globalOffset += chapter.title.length();

            for (ContentSegment segment : chapter.segments) {
                if (segment.type == ReaderPage.TYPE_TEXT) {
                    String formattedContent = indentLines(segment.content);
                    StaticLayout layout = StaticLayout.Builder.obtain(formattedContent, 0, formattedContent.length(), paint, contentWidth)
                            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                            .setLineSpacing(0f, lineSpacingMultiplier)
                            .setIncludePad(false)
                            .build();

                    int startLine = 0;
                    while (startLine < layout.getLineCount()) {
                        int lineTop = layout.getLineTop(startLine);
                        int endLine = layout.getLineForVertical(lineTop + contentHeight);
                        if (layout.getLineBottom(endLine) > lineTop + contentHeight) endLine--;
                        if (endLine < startLine) endLine = startLine;

                        int startOffset = layout.getLineStart(startLine);
                        int endOffset = layout.getLineEnd(endLine);
                        if (endOffset > startOffset) {
                            String pageContent = formattedContent.substring(startOffset, endOffset);
                            boolean isLastPage = endLine >= layout.getLineCount() - 1;
                            if (!isLastPage || !pageContent.trim().isEmpty()) {
                                pages.add(new ReaderPage(ReaderPage.TYPE_TEXT, pageContent, chapter.id));
                                pageStartOffsets.add(globalOffset + startOffset);
                            }
                        }
                        startLine = endLine + 1;
                    }
                    globalOffset += formattedContent.length();
                } else if (segment.type == ReaderPage.TYPE_IMAGE) {
                    pages.add(new ReaderPage(ReaderPage.TYPE_IMAGE, segment.content, chapter.id));
                    pageStartOffsets.add(globalOffset++);
                }
            }

            // A full comments page separates adjacent chapters in page mode.
            pages.add(new ReaderPage(ReaderPage.TYPE_COMMENT, null, chapter.id));
            pageStartOffsets.add(globalOffset);
        }
        
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void addPagedText(String content, int chapterId, int pageType, TextPaint paint,
                              int contentWidth, int contentHeight, float lineSpacingMultiplier,
                              int globalOffset) {
        String formattedContent = content == null ? "" : content;
        StaticLayout layout = StaticLayout.Builder.obtain(formattedContent, 0, formattedContent.length(), paint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, lineSpacingMultiplier)
                .setIncludePad(false)
                .build();
        int startLine = 0;
        while (startLine < layout.getLineCount()) {
            int lineTop = layout.getLineTop(startLine);
            int endLine = layout.getLineForVertical(lineTop + contentHeight);
            if (layout.getLineBottom(endLine) > lineTop + contentHeight) endLine--;
            if (endLine < startLine) endLine = startLine;

            int startOffset = layout.getLineStart(startLine);
            int endOffset = layout.getLineEnd(endLine);
            if (endOffset > startOffset) {
                String pageContent = formattedContent.substring(startOffset, endOffset);
                boolean isLastPage = endLine >= layout.getLineCount() - 1;
                if (!isLastPage || !pageContent.trim().isEmpty()) {
                    pages.add(new ReaderPage(pageType, pageContent, chapterId));
                    pageStartOffsets.add(globalOffset + startOffset);
                }
            }
            startLine = endLine + 1;
        }
    }

    private void toggleMenu() {
        if (isMenuVisible) {
            hideMenu();
        } else {
            showMenu();
        }
    }

    private float readerTitleFontSize() {
        return currentFontSize * 1.25f;
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
                boolean isChapterTitle = page.type == ReaderPage.TYPE_TITLE;
                textHolder.textView.setTextSize(TypedValue.COMPLEX_UNIT_SP,
                        isChapterTitle ? readerTitleFontSize() : currentFontSize);
                textHolder.textView.setTypeface(isChapterTitle ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
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
            TextView tvContinueRead;
            View commentsHeader;
            View commentsButton;
            View commentsList;
            View commentsSkeleton;
            View commentsStatus;
            View commentsPager;
            View commentsComposer;
            ReaderCommentsPanel commentsPanel;
            
            CommentViewHolder(View itemView) {
                super(itemView);
                commentsHeader = itemView.findViewById(R.id.readerCommentsHeader);
                commentsButton = itemView.findViewById(R.id.readerCommentsButton);
                tvChapterTitle = itemView.findViewById(R.id.readerCommentsTitle);
                tvContinueRead = itemView.findViewById(R.id.readerContinueRead);
                commentsList = itemView.findViewById(R.id.readerCommentsList);
                commentsSkeleton = itemView.findViewById(R.id.readerCommentsSkeleton);
                commentsStatus = itemView.findViewById(R.id.readerCommentsStatus);
                commentsPager = itemView.findViewById(R.id.readerCommentsPager);
                commentsComposer = itemView.findViewById(R.id.readerCommentComposerBar);
            }
            
            void bind(int chapterId) {
                int theme = UserPreferences.getReaderTheme(ReaderActivity.this);
                int textColor;
                if (theme >= 1 && theme <= 3) {
                    textColor = Color.BLACK;
                } else {
                    textColor = getThemeColor(com.google.android.material.R.attr.colorOnSurface);
                }

                if (isVerticalMode) {
                    if (commentsPanel != null) { commentsPanel.close(); commentsPanel = null; }
                    commentsHeader.setVisibility(View.GONE);
                    commentsButton.setVisibility(View.VISIBLE);
                    commentsList.setVisibility(View.GONE);
                    commentsSkeleton.setVisibility(View.GONE);
                    commentsStatus.setVisibility(View.GONE);
                    commentsPager.setVisibility(View.GONE);
                    commentsComposer.setVisibility(View.GONE);
                    tvContinueRead.setVisibility(View.GONE);
                    commentsButton.setOnClickListener(v -> showReaderCommentsBottomSheet(chapterId));
                } else {
                    commentsButton.setVisibility(View.GONE);
                    commentsHeader.setVisibility(View.VISIBLE);
                    commentsComposer.setVisibility(View.VISIBLE);
                    tvChapterTitle.setText("评论");
                    tvChapterTitle.setTextColor(textColor);
                    if (commentsPanel != null) commentsPanel.close();
                    commentsPanel = new ReaderCommentsPanel(ReaderActivity.this, itemView, rootTopicId);
                    commentsPanel.bind(chapterId);
                }

                int nextChapterId = getNextChapterId(chapterId);
                tvContinueRead.setTextColor(textColor);
                if (isVerticalMode) {
                    tvContinueRead.setVisibility(View.GONE);
                    tvContinueRead.setOnClickListener(null);
                } else if (nextChoices(chapterId).size() > 1) {
                    tvContinueRead.setVisibility(View.VISIBLE);
                    tvContinueRead.setText("选择剧情分支");
                    tvContinueRead.setOnClickListener(v -> showBranchChoices(chapterId));
                } else if (nextChapterId != -1) {
                    tvContinueRead.setText("左滑继续阅读");
                    tvContinueRead.setOnClickListener(v -> jumpToChapter(nextChapterId));
                    tvContinueRead.setVisibility(View.VISIBLE);
                } else {
                    tvContinueRead.setText("当前为最后一章");
                    tvContinueRead.setOnClickListener(null);
                    tvContinueRead.setVisibility(View.VISIBLE);
                }
                
                if (!isVerticalMode) {
                    ViewGroup.LayoutParams params = tvContinueRead.getLayoutParams();
                    if (params != null) {
                        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
                        tvContinueRead.setLayoutParams(params);
                    }
                    tvContinueRead.setGravity(android.view.Gravity.CENTER);
                    tvContinueRead.setTextSize(14);
                }
            }
        }

        @Override
        public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
            if (holder instanceof CommentViewHolder) {
                CommentViewHolder commentHolder = (CommentViewHolder) holder;
                if (commentHolder.commentsPanel != null) {
                    commentHolder.commentsPanel.close();
                    commentHolder.commentsPanel = null;
                }
            }
            super.onViewRecycled(holder);
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
        int startOffset = chapterStartIndex(pages, initialTopicId);
        int realTotal = Math.max(1, chapterEndIndex(pages, initialTopicId) - startOffset + 1);
        int realIndex = (int) Math.floor(clamp01(progress01) * realTotal);
        if (realIndex >= realTotal) realIndex = realTotal - 1;
        if (realIndex < 0) realIndex = 0;
        return startOffset + realIndex;
    }
    
    private int computeTargetVerticalIndex(double progress01) {
        if (verticalPages == null || verticalPages.isEmpty()) return 0;
        int startOffset = chapterStartIndex(verticalPages, initialTopicId);
        int realTotal = Math.max(1, chapterEndIndex(verticalPages, initialTopicId) - startOffset + 1);
        int realIndex = (int) Math.floor(clamp01(progress01) * realTotal);
        if (realIndex >= realTotal) realIndex = realTotal - 1;
        if (realIndex < 0) realIndex = 0;
        return startOffset + realIndex;
    }
    
    private List<TopicDetailResponse.ChapterEdge> nextChoices() {
        return com.app.fimtale.model.ChapterNavigation.choices(workData, currentTopicId);
    }
    private List<TopicDetailResponse.ChapterEdge> nextChoices(int chapterId) {
        return com.app.fimtale.model.ChapterNavigation.choices(workData, chapterId);
    }
    private int getNextChapterId() {
        return nextChapterId(currentTopicId);
    }
    private int getNextChapterId(int chapterId) {
        return nextChapterId(chapterId);
    }
    private int getPrevChapterId() {
        return previousChapterId(currentTopicId);
    }
    private void showBranchChoices() {
        showBranchChoices(currentTopicId);
    }
    private void showBranchChoices(int chapterId) {
        List<TopicDetailResponse.ChapterEdge> choices = nextChoices(chapterId);
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
