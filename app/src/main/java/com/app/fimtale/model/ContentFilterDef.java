package com.app.fimtale.model;

import com.google.gson.annotations.SerializedName;

/** Content filter definition shared by the native settings editor and the API. */
public class ContentFilterDef {
    @SerializedName("rating_cap") public int ratingCap;
    @SerializedName("hidden_expr") public FilterNode hiddenExpr;

    public ContentFilterDef() {}
    public ContentFilterDef(int ratingCap, FilterNode hiddenExpr) {
        this.ratingCap = ratingCap;
        this.hiddenExpr = hiddenExpr;
    }

    public ContentFilterDef copy() {
        return new ContentFilterDef(ratingCap, hiddenExpr == null ? null : hiddenExpr.copy());
    }
}
