package com.app.fimtale.model;
import com.google.gson.annotations.SerializedName;
public class ReadProgress {
    @SerializedName("work_id") public int workId;
    @SerializedName("chapter_id") public Integer chapterId;
    public double progress;
    public ReadProgress(int workId, int chapterId, double progress) {
        this.workId = workId; this.chapterId = chapterId > 0 ? chapterId : null;
        this.progress = Math.max(0, Math.min(1, progress));
    }
}
