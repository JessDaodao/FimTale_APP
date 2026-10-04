package com.fimtale.model;
import com.google.gson.annotations.SerializedName;
import java.util.List;

public class Tags {
    private String type;
    private String source;
    private String rating;
    private String length;
    private String status;
    private List<String> otherTags;
    
    public static Tags from(int type, int origin, int rating, int length, int publish, List<TagGroup> groups) {
        Tags result = new Tags();
        result.type = label(type, "文章", "图集", "帖子", "公告");
        result.source = label(origin, "原创", "翻译", "转载");
        result.rating = label(rating, "Everyone", "Teen", "Restricted");
        result.length = label(length, "长篇", "中篇", "短篇");
        result.status = label(publish, "连载中", "已完结", "已暂停", "已弃坑");
        result.otherTags = new java.util.ArrayList<>();
        if (groups != null) for (TagGroup group : groups) {
            if (group.tags != null) for (TagInfo tag : group.tags) result.otherTags.add(tag.getName());
        }
        return result;
    }
    private static String label(int value, String... labels) {
        return value > 0 && value <= labels.length ? labels[value - 1] : "";
    }
    public String getType() { return type; }
    public String getSource() { return source; }
    public String getRating() { return rating; }
    public String getLength() { return length; }
    public String getStatus() { return status; }
    public List<String> getOtherTags() { return otherTags; }
    
    public void setType(String type) { this.type = type; }
    public void setSource(String source) { this.source = source; }
    public void setRating(String rating) { this.rating = rating; }
    public void setLength(String length) { this.length = length; }
    public void setStatus(String status) { this.status = status; }
    public void setOtherTags(List<String> otherTags) { this.otherTags = otherTags; }
}
