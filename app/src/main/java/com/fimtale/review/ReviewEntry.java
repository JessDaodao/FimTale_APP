package com.fimtale.review;

import com.google.gson.annotations.SerializedName;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Author-facing entries also include unsubmitted works, whose review ID may be zero. */
public class ReviewEntry {
    public static final int UNSUBMITTED = 0, PENDING = 1, PASSED = 2, REJECTED = 3;
    public int id, status;
    @SerializedName("work_id") public int workId;
    @SerializedName("work_title") public String workTitle;
    @SerializedName("created_at") public String createdAt;
    @SerializedName("updated_at") public String updatedAt;
    public Payload payload;

    public String title() { return workTitle == null || workTitle.isEmpty() ? "作品 " + workId : workTitle; }
    public String reason() { return payload == null || payload.reason == null ? "" : payload.reason; }
    public Long resubmitTime() {
        if (payload == null || payload.resubmitAfter == null || payload.resubmitAfter.trim().isEmpty()) return null;
        try { return OffsetDateTime.parse(payload.resubmitAfter).toInstant().toEpochMilli(); }
        catch (RuntimeException ignored) { return Long.MAX_VALUE; }
    }
    public boolean canSubmit(long now) {
        if (status == UNSUBMITTED) return true;
        if (status != REJECTED) return false;
        Long after = resubmitTime();
        return after == null || after < now;
    }
    public static String date(String value) {
        if (value == null || value.isEmpty() || value.startsWith("0001-")) return "";
        try { return OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")); }
        catch (RuntimeException ignored) { return value; }
    }
    public static class Payload {
        @SerializedName("state_reason") public String reason;
        @SerializedName("resubmit_after") public String resubmitAfter;
    }
    public static class Submit {
        @SerializedName("work_id") public final int workId;
        public Submit(int workId) { this.workId = workId; }
    }
}
