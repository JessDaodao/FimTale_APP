package com.app.fimtale.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ChapterCacheDao {

    @Query("SELECT * FROM cached_chapters WHERE topicId = :topicId")
    CachedChapter getChapter(int topicId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertChapter(CachedChapter chapter);

    @Query("UPDATE cached_chapters SET lastAccessedAt = :timestamp WHERE topicId = :topicId")
    void touchChapter(int topicId, long timestamp);

    @Query("SELECT COALESCE(SUM(contentSizeBytes), 0) FROM cached_chapters")
    long getTotalCacheSize();

    @Query("SELECT * FROM cached_chapters ORDER BY lastAccessedAt ASC")
    List<CachedChapter> getChaptersOrderedByLRU();

    @Query("DELETE FROM cached_chapters WHERE topicId = :topicId")
    void deleteChapter(int topicId);

    @Query("DELETE FROM cached_chapters")
    void deleteAllChapters();

    @Query("SELECT * FROM cached_chapter_menus WHERE rootTopicId = :rootTopicId")
    CachedChapterMenu getChapterMenu(int rootTopicId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertChapterMenu(CachedChapterMenu menu);

    @Query("DELETE FROM cached_chapter_menus")
    void deleteAllMenus();

    @Query("DELETE FROM cached_chapter_menus WHERE rootTopicId = :workId")
    void deleteWorkMenu(int workId);

    @Query("DELETE FROM cached_chapters WHERE rootTopicId = :workId")
    void deleteWorkChapters(int workId);
}
