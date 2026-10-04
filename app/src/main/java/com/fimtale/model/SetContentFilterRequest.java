package com.fimtale.model;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.annotations.SerializedName;
import java.util.List;

/** Uses JsonNull for content_filter so restoring the server default is sent explicitly. */
public class SetContentFilterRequest {
    @SerializedName("content_filter") public final JsonElement contentFilter;
    @SerializedName("filter_presets") public final List<NamedContentFilter> filterPresets;

    public SetContentFilterRequest(ContentFilterDef filter, List<NamedContentFilter> presets) {
        contentFilter = filter == null ? JsonNull.INSTANCE : new Gson().toJsonTree(filter);
        filterPresets = presets;
    }
}
