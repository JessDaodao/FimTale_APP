package com.fimtale.model;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

/** Account material returned by user/get_user and user/set_content_filter. */
public class UserMaterial {
    @SerializedName("content_filter") public ContentFilterDef contentFilter;
    @SerializedName("filter_presets") public List<NamedContentFilter> filterPresets;
    @SerializedName("blocked_user_ids") public List<Integer> blockedUserIds;

    public List<NamedContentFilter> presets() {
        return filterPresets == null ? new ArrayList<>() : filterPresets;
    }
}
