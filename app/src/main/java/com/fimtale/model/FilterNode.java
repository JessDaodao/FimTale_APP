package com.fimtale.model;

import com.fimtale.R;
import com.fimtale.utils.AppStrings;

import java.util.ArrayList;
import java.util.List;

/** Recursive filter expression used by the current content-filter API. */
public class FilterNode {
    public String op;
    public String tag;
    public List<FilterNode> children;

    public static FilterNode tag(String value) {
        FilterNode node = new FilterNode(); node.op = "tag"; node.tag = value; return node;
    }

    public static FilterNode group(String op, List<FilterNode> children) {
        FilterNode node = new FilterNode(); node.op = op;
        node.children = children == null ? new ArrayList<>() : children;
        return node;
    }

    public static FilterNode not(FilterNode child) {
        FilterNode node = new FilterNode(); node.op = "not";
        node.children = new ArrayList<>(); node.children.add(child); return node;
    }

    public FilterNode copy() {
        FilterNode result = new FilterNode(); result.op = op; result.tag = tag;
        if (children != null) {
            result.children = new ArrayList<>();
            for (FilterNode child : children) if (child != null) result.children.add(child.copy());
        }
        return result;
    }

    public void collectTags(List<String> result) {
        if ("tag".equals(op)) { if (tag != null && !tag.trim().isEmpty()) result.add(tag); return; }
        if (children != null) for (FilterNode child : children) if (child != null) child.collectTags(result);
    }

    public String summary() {
        if ("tag".equals(op)) return tag == null ? "" : tag;
        if ("not".equals(op)) return AppStrings.get(R.string.filters_not, childSummary());
        String joiner = "and".equals(op) ? AppStrings.get(R.string.filters_and_separator) : AppStrings.get(R.string.filters_or_separator);
        if (children == null || children.isEmpty()) return "";
        StringBuilder text = new StringBuilder();
        for (FilterNode child : children) {
            if (text.length() > 0) text.append(joiner);
            text.append(child == null ? "" : child.summary());
        }
        return text.toString();
    }

    private String childSummary() {
        return children == null || children.isEmpty() || children.get(0) == null ? "" : children.get(0).summary();
    }
}
