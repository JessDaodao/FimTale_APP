package com.fimtale.model;

import com.google.gson.annotations.SerializedName;

/** A comment from work/get_comments, including its author and chapter context. */
public class Comment {
    public int id;
    public AuthorInfo user;
    public String content;
    public String title;
    @SerializedName("work_id") public int workId;
    @SerializedName("chapter_id") public int chapterId;
    @SerializedName("reply_comment_id") public int replyCommentId;
    @SerializedName("created_at") public String createdAt;
    @SerializedName("status_del") public int statusDel;
    @SerializedName("status_top") public boolean pinned;
    public String getAvatarUrl() { return user == null ? null : user.getAvatar(); }
    public String getUserName() { return user == null ? "未知用户" : user.getUserName(); }
    public String getContent() { return statusDel != 0 ? "该评论已删除" : content; }
    public String getChapterTitle() { return chapterId == 0 ? "文章" : title == null || title.isEmpty() ? "章节 " + chapterId : title; }
    public String getTime() {
        try {
            return java.time.OffsetDateTime.parse(createdAt).atZoneSameInstant(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        } catch (RuntimeException ignored) { return ""; }
    }
}
