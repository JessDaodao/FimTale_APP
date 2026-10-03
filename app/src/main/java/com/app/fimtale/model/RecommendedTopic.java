package com.app.fimtale.model;
public class RecommendedTopic {
    private Topic work;
    private AuthorInfo user;
    private String reason;
    public Topic getWork() { return work; }
    public int getId() { return work.getId(); }
    public String getTitle() { return work.getTitle(); }
    public String getAuthorName() { return work.getAuthorName(); }
    public String getBackground() { return work.getBackground(); }
    public String getRecommendWord() { return reason; }
    public String getRecommenderName() { return user == null ? "" : user.getUserName(); }
}
