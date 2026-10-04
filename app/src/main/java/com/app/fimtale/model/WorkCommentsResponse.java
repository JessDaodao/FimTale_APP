package com.app.fimtale.model;

import java.util.ArrayList;
import java.util.List;

public class WorkCommentsResponse {
    private List<Comment> items;
    public int total;
    public List<Comment> getItems() { return items == null ? new ArrayList<>() : items; }
    public int totalPages(int perPage) { return Math.max(1, (total + perPage - 1) / perPage); }
}
