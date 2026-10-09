package com.fimtale.model;

import com.google.gson.annotations.SerializedName;

public final class DeleteReadProgressRequest {
    @SerializedName("work_id") private final int workId;
    @SerializedName("chapter_id") private final int chapterId;

    public DeleteReadProgressRequest(HistoryResponse.HistoryTopic topic) {
        workId = topic.getWorkId();
        // Zero asks the API to delete the work entry and all of its chapter entries.
        chapterId = topic.getChapterId();
    }
}
