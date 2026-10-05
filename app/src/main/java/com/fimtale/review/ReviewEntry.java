package com.fimtale.review;

import com.google.gson.annotations.SerializedName;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Review records have different status values from a work's legacy status_review field. */
public class ReviewEntry {
    public static final int PENDING = 1, PASSED = 2, REJECTED = 3;
    public int id, status;
    @SerializedName("work_id") public int workId;
    @SerializedName("work_title") public String workTitle;
    @SerializedName("created_at") public String createdAt;
    @SerializedName("updated_at") public String updatedAt;
    @SerializedName("previous_review_count") public int previousReviewCount;
    public Payload payload;
    public List<Assignment> assignments = new ArrayList<>();

    public String title() { return workTitle == null || workTitle.isEmpty() ? "作品 " + workId : workTitle; }
    public String reason() { return payload == null || payload.reason == null ? "" : payload.reason; }
    public int resolver() { return payload == null ? 0 : payload.resolverUserId; }
    public static String date(String value) {
        if (value == null || value.isEmpty() || value.startsWith("0001-")) return "";
        try { return OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")); }
        catch (RuntimeException ignored) { return value; }
    }
    public static class Payload {
        @SerializedName("resolver_user_id") public int resolverUserId;
        @SerializedName("state_reason") public String reason;
        @SerializedName("resubmit_after") public String resubmitAfter;
    }
    public static class Assignment {
        @SerializedName("reviewer_user_id") public int reviewerId;
        @SerializedName("reviewer_name") public String reviewerName;
    }
    public static class Reviewer {
        @SerializedName("user_id") public int userId;
        @SerializedName("role_id") public int roleId;
        public String username;
    }
    public static class Resolve {
        @SerializedName("review_id") public final int reviewId;
        public final int status;
        public final String reason;
        @SerializedName("resubmit_after") public final String resubmitAfter;
        public Resolve(int reviewId, int status, String reason, String resubmitAfter) {
            this.reviewId = reviewId; this.status = status; this.reason = reason; this.resubmitAfter = resubmitAfter;
        }
    }
    public static class Assign {
        @SerializedName("review_id") public final int reviewId;
        @SerializedName("reviewer_user_ids") public final List<Integer> reviewers;
        public Assign(int reviewId, List<Integer> reviewers) { this.reviewId = reviewId; this.reviewers = reviewers; }
    }
}
