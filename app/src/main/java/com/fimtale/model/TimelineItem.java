package com.fimtale.model;

import com.google.gson.annotations.SerializedName;

/** The timeline's entity is a work, chapter, work comment or channel comment. */
public class TimelineItem {
    public static final int COMMENT = 1, WORK = 6, CHAPTER = 7, CHANNEL_WORK = 8, CHANNEL_COMMENT = 14;
    public int type;
    @SerializedName("entity_id") public int entityId;
    @SerializedName("created_at") public String createdAt;
    @SerializedName("from_user") public AuthorInfo fromUser;
    public Entity entity;
    public Channel context;

    public static class Channel { public int id; public String name; }
    public static class Entity {
        public int id, type;
        public String title, content, intro, preface, cover;
        public AuthorInfo user;
        @SerializedName("work_id") public int workId;
        @SerializedName("chapter_id") public int chapterId;
        @SerializedName("channel_id") public int channelId;
        @SerializedName("context_title") public String contextTitle;
        @SerializedName("count_character") public int characters;
        @SerializedName("count_view") public int views;
        @SerializedName("count_comment") public int comments;
        @SerializedName("count_fav") public int favorites;
        @SerializedName("status_del") public int statusDel;
    }
    public static class Highlight {
        @SerializedName("comment_id") public final int commentId;
        public Highlight(int commentId) { this.commentId = commentId; }
    }

    public String key() { return type + ":" + entityId; }
    public boolean isWork() { return type == WORK || type == CHANNEL_WORK; }
    public boolean isAvailable() { return entity != null && entity.id > 0 && entity.statusDel == 0; }
    public boolean canHighlight() { return isAvailable() && (type == COMMENT || type == CHANNEL_COMMENT); }
    public String channelName() {
        return context != null && !blank(context.name) ? context.name
                : entity != null && type == CHANNEL_COMMENT ? value(entity.contextTitle) : "";
    }
    public String actionText() {
        switch (type) {
            case WORK: return "发布了新作品";
            case CHAPTER: return "更新了章节";
            case CHANNEL_WORK: return "频道 " + channelName() + " 收录了新作品";
            case CHANNEL_COMMENT: return "频道 " + channelName() + " 发布了新公告";
            case COMMENT: return "转发了评论";
            default: return "发布了动态";
        }
    }
    public String body() {
        if (!isAvailable()) return "该内容已删除或暂时无法查看";
        if (isWork()) return !blank(entity.intro) ? entity.intro : value(entity.preface);
        if (type == CHAPTER || type == COMMENT || type == CHANNEL_COMMENT) return value(entity.content);
        return "暂不支持此类动态";
    }
    /** Comment links retain their anchors, including comments attached to chapters. */
    public String path() {
        if (!isAvailable()) return null;
        if (isWork()) return "/work/" + entity.id;
        if (type == CHAPTER && entity.workId > 0)
            return "/work/" + entity.workId + "/chapter/" + entity.id;
        if (type == COMMENT && entity.workId > 0)
            return "/work/" + entity.workId + (entity.chapterId > 0 ? "/chapter/" + entity.chapterId : "")
                    + "#comment-" + entity.id;
        if (type == CHANNEL_COMMENT && entity.channelId > 0)
            return "/channel/" + entity.channelId + "#comment-" + entity.id;
        return null;
    }
    public String contextPath() {
        if (type == CHANNEL_WORK) return context != null && context.id > 0 ? "/channel/" + context.id : null;
        return type == COMMENT || type == CHANNEL_COMMENT ? path() : null;
    }
    public String contextLabel() {
        return type == COMMENT && entity != null ? value(entity.contextTitle) : channelName();
    }
    private static boolean blank(String text) { return text == null || text.trim().isEmpty(); }
    private static String value(String text) { return text == null ? "" : text; }
}
