package com.fimtale.model;
import com.google.gson.annotations.SerializedName;
public class ChapterResponse {
    public Chapter chapter;
    public static class Chapter {
        public int id;
        @SerializedName("work_id") public int workId;
        public String title;
        public String content;
    }
}
