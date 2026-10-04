package com.fimtale.model;
public class TopicInfo extends Topic {
    @com.google.gson.annotations.SerializedName("count_like") private int likeCount;
    public int getLikeCount() { return likeCount; }
    private String preface;
    public String getContent() { return preface; }
    public int getViewCount() { return getViews(); }
    public int getCommentCount() { return getComments(); }
    public int getFavoriteCount() { return getFollowers(); }
    @Override public TopicTags getTags() { return TopicTags.from(super.getTags()); }
}
