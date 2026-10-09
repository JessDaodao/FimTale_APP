package com.fimtale.editor;

import com.fimtale.R;
import com.fimtale.utils.AppStrings;

import com.google.gson.annotations.SerializedName;

public class ChapterInput {
    public Integer id;
    @SerializedName("work_id") public int workId;
    public String title = "", content = "";
    public String validate() {
        if (workId <= 0) return AppStrings.get(R.string.editor_work_missing);
        if (WorkInput.blank(title)) return AppStrings.get(R.string.editor_chapter_title_required);
        if (WorkInput.blank(content)) return AppStrings.get(R.string.editor_chapter_body_required);
        return null;
    }
}
