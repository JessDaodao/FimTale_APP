package com.fimtale.db;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "cached_chapters", indices = {
        @Index("rootTopicId"),
        @Index("lastAccessedAt")
})
public class CachedChapter {
    @PrimaryKey
    public int topicId;

    public int rootTopicId;
    public int postId;
    public String title;
    public String content;
    public long contentSizeBytes;
    public long cachedAt;
    public long lastAccessedAt;
}
