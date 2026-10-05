package com.fimtale.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Advance only on success; offset pages may repeat entries as new activity arrives. */
public final class TimelineFeed {
    public static final int PAGE_SIZE = 12;
    public final List<TimelineItem> items = new ArrayList<>();
    public int page;
    public boolean finished;

    public void clear() { items.clear(); page = 0; finished = false; }
    public void accept(int loadedPage, List<TimelineItem> incoming) {
        if (loadedPage == 1) items.clear();
        Set<String> keys = new HashSet<>();
        for (TimelineItem item : items) keys.add(item.key());
        if (incoming != null) for (TimelineItem item : incoming)
            if (item != null && keys.add(item.key())) items.add(item);
        page = loadedPage;
        finished = incoming == null || incoming.size() < PAGE_SIZE;
    }
}
