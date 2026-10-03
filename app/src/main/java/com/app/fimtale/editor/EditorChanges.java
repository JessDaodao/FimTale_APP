package com.app.fimtale.editor;

import java.util.concurrent.ConcurrentHashMap;

/** In-process invalidation for screens retained behind an editor. */
public final class EditorChanges {
    private static final ConcurrentHashMap<Integer, Long> versions = new ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicLong all = new java.util.concurrent.atomic.AtomicLong();
    private EditorChanges() {}
    public static long version(int workId) { return versions.getOrDefault(workId, 0L); }
    public static long version() { return all.get(); }
    public static void mark(int workId) { versions.merge(workId, 1L, Long::sum); all.incrementAndGet(); }
}
