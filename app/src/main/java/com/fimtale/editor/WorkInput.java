package com.fimtale.editor;

import com.fimtale.R;
import com.fimtale.utils.AppStrings;

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
        if (type < 1 || type > 4) return AppStrings.get(R.string.editor_type_required);
        if (type != 3 && blank(title)) return AppStrings.get(R.string.editor_title_required);
        if (blank(preface)) return AppStrings.get(R.string.editor_preface_required);
        if (type != 3 && blank(intro)) return AppStrings.get(R.string.editor_intro_required);
        int min = type == 1 || type == 2 ? 1 : 0;
        if (length < min || length > 3 || rating < min || rating > 3
                || origin < min || origin > 3 || publish < min || publish > 4)
            return AppStrings.get(R.string.editor_classification_required);
        if (type == 4 && (length != 0 || rating != 0 || origin != 0 || publish != 0))
            return AppStrings.get(R.string.editor_announcement_no_classification);
        if (prequelId != null && (prequelId < 0 || prequelId.equals(id))) return AppStrings.get(R.string.editor_prequel_invalid);
        return null;
    }
    public static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
