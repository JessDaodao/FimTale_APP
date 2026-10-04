package com.fimtale.model;
import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
public class TopicDetailResponse {
    private TopicInfo work;
    public WorkInteractions.Viewer viewer;
    private List<ChapterMenuItem> chapters;
    @SerializedName("chapter_edges") public List<ChapterEdge> chapterEdges;
    public TopicInfo getTopicInfo() { return work; }
    public AuthorInfo getAuthorInfo() { return work == null ? null : work.getUser(); }
    public List<ChapterMenuItem> getMenu() {
        List<ChapterMenuItem> result = new ArrayList<>();
        if (chapters != null) for (ChapterMenuItem chapter : chapters) {
            if (!chapter.isDeleted()) result.add(chapter);
        }
        result.sort(Comparator.comparingInt(ChapterMenuItem::getOrderNum).thenComparingInt(ChapterMenuItem::getId));
        return result;
    }
    public static class ChapterEdge {
        @SerializedName("from_chapter_id") public Integer from;
        @SerializedName("to_chapter_id") public Integer to;
        public String label;
    }
}
