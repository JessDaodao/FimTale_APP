package com.fimtale.model;

import com.fimtale.R;
import com.fimtale.utils.AppStrings;
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
        result.type = label(type, AppStrings.array(R.array.work_type_labels));
        result.source = label(origin, AppStrings.array(R.array.work_origin_labels));
        result.rating = label(rating, AppStrings.array(R.array.work_rating_labels));
        result.length = label(length, AppStrings.array(R.array.work_length_labels));
        result.status = label(publish, AppStrings.array(R.array.work_status_labels));
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
