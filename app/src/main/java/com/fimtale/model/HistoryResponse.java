package com.fimtale.model;
import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;
public class HistoryResponse {
    private List<HistoryTopic> items;
    private int total;
    public List<HistoryTopic> getHistoryTopics() { return items == null ? new ArrayList<>() : items; }
    public int getTotalPage() { return (total + 19) / 20; }
    public static class HistoryTopic {
        @SerializedName("work_id") private int workId;
        @SerializedName("chapter_id") private int chapterId;
        private String title;
        private double progress;
        @SerializedName("updated_at") private String updatedAt;
        public int getWorkId() { return workId; }
        public int getChapterId() { return chapterId; }
        public String getTitle() { return title; }
        public double getProgress() { return progress; }
        public long getDateCreated() {
            try { return java.time.OffsetDateTime.parse(updatedAt).toEpochSecond(); }
            catch (Exception e) { return 0; }
        }
    }
}
