package com.app.fimtale.editor;

import com.google.gson.annotations.SerializedName;

public class ChapterInput {
    public Integer id;
    @SerializedName("work_id") public int workId;
    public String title = "", content = "";
    public String validate() {
        if (workId <= 0) return "未指定作品";
        if (WorkInput.blank(title)) return "请填写章节标题";
        if (WorkInput.blank(content)) return "请填写章节正文";
        return null;
    }
}
