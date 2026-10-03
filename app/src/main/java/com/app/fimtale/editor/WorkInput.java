package com.app.fimtale.editor;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

/** Only fields accepted by the current create_update_work endpoint. */
public class WorkInput {
    public Integer id;
    public String title = "", preface = "", intro = "", cover = "";
    public int type = 1, length = 1, rating = 1, origin = 1, publish = 1;
    @SerializedName("origin_link") public String originLink = "";
    @SerializedName("prequel_id") public Integer prequelId;
    @SerializedName("tag_ids") public List<Integer> tagIds = new ArrayList<>();

    public String validate() {
        if (type < 1 || type > 4) return "请选择作品类型";
        if (type != 3 && blank(title)) return "请填写标题";
        if (blank(preface)) return "请填写序言（长简介）";
        if (type != 3 && blank(intro)) return "请填写简介";
        int min = type == 1 || type == 2 ? 1 : 0;
        if (length < min || length > 3 || rating < min || rating > 3
                || origin < min || origin > 3 || publish < min || publish > 4)
            return "请完整填写长度、分级、来源和连载状态";
        if (type == 4 && (length != 0 || rating != 0 || origin != 0 || publish != 0))
            return "公告无需分类信息";
        if (prequelId != null && (prequelId < 0 || prequelId.equals(id))) return "前作 ID 无效";
        return null;
    }
    public static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
