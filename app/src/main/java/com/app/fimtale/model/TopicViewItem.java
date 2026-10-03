package com.app.fimtale.model;

import com.app.fimtale.model.RecommendedTopic;
import com.app.fimtale.model.Topic;

public class TopicViewItem {
    private int id;
    private String title;
    private String authorName;
    private String background;
    private String intro; // 新增
    
    // Tags
    private Tags tags;

    // 统计数据
    private String wordCount;
    private String viewCount;
    private String commentCount;
    private String favoriteCount;

    public TopicViewItem(RecommendedTopic topic) {
        this(topic.getWork());
        this.intro = topic.getRecommendWord();
    }

    public TopicViewItem(Topic topic) {
        this.id = topic.getId();
        this.title = topic.getTitle();
        this.authorName = topic.getAuthorName();
        this.background = topic.getBackground();
        this.intro = topic.getIntro();
        this.tags = topic.getTags();
        
        this.wordCount = String.valueOf(topic.getWordCount());
        this.viewCount = String.valueOf(topic.getViews());
        this.commentCount = String.valueOf(topic.getComments());
        this.favoriteCount = String.valueOf(topic.getFollowers());
    }
    
    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getAuthorName() { return authorName; }
    public String getBackground() { return background; }
    public String getIntro() { return intro; }
    public Tags getTags() { return tags; }
    
    public String getWordCount() { return wordCount; }
    public String getViewCount() { return viewCount; }
    public String getCommentCount() { return commentCount; }
    public String getFavoriteCount() { return favoriteCount; }
}
