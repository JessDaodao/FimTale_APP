package com.fimtale.model;

public class NamedContentFilter extends ContentFilterDef {
    public String name;
    public NamedContentFilter() {}
    public NamedContentFilter(String name, ContentFilterDef source) {
        super(source == null ? 0 : source.ratingCap, source == null || source.hiddenExpr == null ? null : source.hiddenExpr.copy());
        this.name = name;
    }
}
