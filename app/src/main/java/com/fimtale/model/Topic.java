package com.fimtale.model;

import com.google.gson.annotations.SerializedName;

public class Topic {
    @SerializedName("id")
    private int id;
    @SerializedName("title")
    private String title;
    private AuthorInfo user;
    private transient String authorName;
    @SerializedName("cover")
    private String background;

    @SerializedName("intro")
    private String intro;

    private transient Tags displayTags;
    private java.util.List<TagGroup> tags;
    private int type, origin, rating, length, publish;

    @SerializedName("count_view")
    private int views;
    
    @SerializedName("count_comment")
    private int comments;
    
    @SerializedName("count_fav")
    private int followers;
    
    @SerializedName("count_character")
    private int wordCount;

    @SerializedName("count_high_praise")
    private int highPraise;

    @SerializedName("faved")
    private boolean isFavorite;

    public AuthorInfo getUser() { return user; }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getAuthorName() { return user != null ? user.getUserName() : authorName; }
    public String getBackground() { return com.fimtale.network.SiteUrls.media(background); }
    public String getIntro() { return intro; }
    public Tags getTags() { return displayTags != null ? displayTags : Tags.from(type, origin, rating, length, publish, tags); }
    public int getViews() { return views; }
    public int getComments() { return comments; }
    public int getFollowers() { return followers; }
    public int getWordCount() { return wordCount; }
    public int getHighPraise() { return highPraise; }
    public int getOrigin() { return origin; }
    public boolean isFavorite() { return isFavorite; }
    
    public void setId(int id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }
    public void setBackground(String background) { this.background = background; }
    public void setIntro(String intro) { this.intro = intro; }
    public void setTags(Tags tags) { this.displayTags = tags; }
    public void setViews(int views) { this.views = views; }
    public void setComments(int comments) { this.comments = comments; }
    public void setFollowers(int followers) { this.followers = followers; }
    public void setWordCount(int wordCount) { this.wordCount = wordCount; }
    public void setHighPraise(int highPraise) { this.highPraise = highPraise; }
    public void setFavorite(boolean favorite) { isFavorite = favorite; }
}
