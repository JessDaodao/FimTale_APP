package com.fimtale.model;
import java.util.ArrayList;
import java.util.List;

/** Follows ft-front's explicit graph, or document order when no graph exists. */
public final class ChapterNavigation {
    private ChapterNavigation() {}
    public static List<TopicDetailResponse.ChapterEdge> choices(TopicDetailResponse work, int chapterId) {
        List<TopicDetailResponse.ChapterEdge> result = new ArrayList<>();
        if (work == null) return result;
        if (work.chapterEdges != null && !work.chapterEdges.isEmpty()) {
            for (TopicDetailResponse.ChapterEdge edge : work.chapterEdges) {
                if ((edge.from == null ? 0 : edge.from) == chapterId) result.add(edge);
            }
            return result;
        }
        List<ChapterMenuItem> chapters = work.getMenu();
        int next = -1;
        if (chapterId == 0 && !chapters.isEmpty()) next = chapters.get(0).getId();
        for (int i = 0; i + 1 < chapters.size(); i++) {
            if (chapters.get(i).getId() == chapterId) next = chapters.get(i + 1).getId();
        }
        if (next > 0) {
            TopicDetailResponse.ChapterEdge edge = new TopicDetailResponse.ChapterEdge();
            edge.from = chapterId == 0 ? null : chapterId; edge.to = next;
            edge.label = chapterId == 0 ? "开始阅读" : "下一章";
            result.add(edge);
        }
        return result;
    }
    public static int previous(TopicDetailResponse work, int chapterId) {
        if (work == null) return -1;
        if (work.chapterEdges != null && !work.chapterEdges.isEmpty()) {
            int count = 0, previous = -1;
            for (TopicDetailResponse.ChapterEdge edge : work.chapterEdges) {
                if (edge.to != null && edge.to == chapterId && edge.from != null) {
                    count++; previous = edge.from;
                }
            }
            return count == 1 ? previous : -1;
        }
        List<ChapterMenuItem> chapters = work.getMenu();
        for (int i = 1; i < chapters.size(); i++) if (chapters.get(i).getId() == chapterId) return chapters.get(i - 1).getId();
        return -1;
    }
}
