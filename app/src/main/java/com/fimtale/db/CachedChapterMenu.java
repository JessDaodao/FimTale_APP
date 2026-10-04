package com.fimtale.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "cached_chapter_menus")
public class CachedChapterMenu {
    @PrimaryKey
    public int rootTopicId;

    public String menuJson;
    public long cachedAt;
}
