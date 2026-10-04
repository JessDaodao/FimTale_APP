package com.app.fimtale.db;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.app.fimtale.model.TopicDetailResponse;
import com.app.fimtale.utils.UserPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CacheManager {

    public interface Callback<T> {
        void onResult(T result);
    }

    private static volatile CacheManager INSTANCE;

    private final ChapterCacheDao dao;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final Context appContext;
    private final Gson gson;

    private CacheManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.dao = AppDatabase.getInstance(appContext).chapterCacheDao();
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.gson = new Gson();
    }

    public static CacheManager getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (CacheManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new CacheManager(context);
                }
            }
        }
        return INSTANCE;
    }

    // --- Chapter content ---

    public void getChapter(int topicId, Callback<CachedChapter> callback) {
        executor.execute(() -> {
            CachedChapter chapter = dao.getChapter(topicId);
            if (chapter != null) {
                dao.touchChapter(topicId, System.currentTimeMillis());
            }
            mainHandler.post(() -> callback.onResult(chapter));
        });
    }

    public void cacheChapter(int topicId, int rootTopicId, int postId,
                             String title, String content, Runnable onComplete) {
        executor.execute(() -> {
            CachedChapter chapter = new CachedChapter();
            chapter.topicId = topicId;
            chapter.rootTopicId = rootTopicId;
            chapter.postId = postId;
            chapter.title = title;
            chapter.content = content;
            chapter.contentSizeBytes = content != null
                    ? content.getBytes(StandardCharsets.UTF_8).length : 0;
            long now = System.currentTimeMillis();
            chapter.cachedAt = now;
            chapter.lastAccessedAt = now;
            dao.insertChapter(chapter);
            evictIfNeeded();
            if (onComplete != null) {
                mainHandler.post(onComplete);
            }
        });
    }

    // --- Chapter menu ---

    public void getChapterMenu(int rootTopicId, Callback<TopicDetailResponse> callback) {
        executor.execute(() -> {
            CachedChapterMenu cached = dao.getChapterMenu(rootTopicId);
            TopicDetailResponse menu = null;
            if (cached != null && cached.menuJson != null) {
                menu = gson.fromJson(cached.menuJson,
                        TopicDetailResponse.class);
            }
            TopicDetailResponse finalMenu = menu;
            mainHandler.post(() -> callback.onResult(finalMenu));
        });
    }

    public void cacheChapterMenu(int rootTopicId, TopicDetailResponse menu) {
        executor.execute(() -> {
            CachedChapterMenu cached = new CachedChapterMenu();
            cached.rootTopicId = rootTopicId;
            cached.menuJson = gson.toJson(menu);
            cached.cachedAt = System.currentTimeMillis();
            dao.insertChapterMenu(cached);
        });
    }

    // --- Cache management ---

    public void invalidateWork(int workId, Runnable onComplete) {
        executor.execute(() -> {
            dao.deleteWorkMenu(workId);
            dao.deleteWorkChapters(workId);
            if (onComplete != null) {
                mainHandler.post(onComplete);
            }
        });
    }

    public void getTotalCacheSize(Callback<Long> callback) {
        executor.execute(() -> {
            long size = dao.getTotalCacheSize();
            mainHandler.post(() -> callback.onResult(size));
        });
    }

    public long getMaxCacheSize() {
        return UserPreferences.getMaxCacheSize(appContext);
    }

    public void setMaxCacheSize(long bytes) {
        UserPreferences.setMaxCacheSize(appContext, bytes);
        executor.execute(this::evictIfNeeded);
    }

    public void clearAllCache(Runnable onComplete) {
        executor.execute(() -> {
            dao.deleteAllChapters();
            dao.deleteAllMenus();
            if (onComplete != null) {
                mainHandler.post(onComplete);
            }
        });
    }

    private void evictIfNeeded() {
        long totalSize = dao.getTotalCacheSize();
        long maxSize = getMaxCacheSize();
        if (totalSize <= maxSize) return;

        List<CachedChapter> lruList = dao.getChaptersOrderedByLRU();
        for (CachedChapter entry : lruList) {
            if (totalSize <= maxSize) break;
            dao.deleteChapter(entry.topicId);
            totalSize -= entry.contentSizeBytes;
        }
    }
}
