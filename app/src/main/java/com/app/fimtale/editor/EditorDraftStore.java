package com.app.fimtale.editor;

import com.google.gson.Gson;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Independent drafts, scoped to an account and API. Existing entity drafts retain their keys. */
public final class EditorDraftStore {
    private final File file;
    private final String scope;
    private final Gson gson = new Gson();
    private static class Envelope {
        int version = 1;
        String scope;
        EditorDocument document;
    }
    public EditorDraftStore(File directory, String api, String userId, int workId, int chapterId) {
        this(directory, api, userId, workId, chapterId, null);
    }
    public EditorDraftStore(File directory, String api, String userId, int workId, int chapterId, String draftId) {
        if (draftId != null && !draftId.isEmpty() && !draftId.matches("[a-zA-Z0-9-]{1,64}"))
            throw new IllegalArgumentException("Invalid draft ID");
        scope = api + "\n" + userId + "\n" + workId + "\n" + chapterId
                + (draftId == null || draftId.isEmpty() ? "" : "\n" + draftId);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(scope.getBytes(StandardCharsets.UTF_8));
            StringBuilder name = new StringBuilder();
            for (byte b : digest) name.append(String.format(java.util.Locale.ROOT, "%02x", b));
            file = new File(directory, name + ".json");
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public EditorDocument read() throws IOException {
        synchronized (EditorDraftStore.class) {
            if (!file.exists()) return null;
            try {
                Envelope data = gson.fromJson(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8), Envelope.class);
                if (data == null || data.version != 1 || !scope.equals(data.scope) || data.document == null
                        || data.document.work == null || data.document.chapter == null || data.document.tags == null)
                    throw new IOException("草稿格式无效");
                return data.document;
            } catch (RuntimeException e) { throw new IOException("无法读取草稿", e); }
        }
    }
    public void write(EditorDocument document) throws IOException {
        synchronized (EditorDraftStore.class) {
            File directory = file.getParentFile();
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("无法创建草稿目录");
            Envelope envelope = new Envelope(); envelope.scope = scope; envelope.document = document;
            File temp = new File(directory, file.getName() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write(gson.toJson(envelope).getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
    }
    public void delete() throws IOException {
        synchronized (EditorDraftStore.class) { Files.deleteIfExists(file.toPath()); }
    }

    public static class Entry {
        public final int workId, chapterId;
        public final String draftId, title, preview;
        public final long savedAt;
        public final boolean submissionUncertain;
        public final String onlineKey;
        public final long revision;
        public final boolean pendingSync;
        private final EditorDraftStore store;
        private Entry(EditorDraftStore store, int workId, int chapterId, String draftId, EditorDocument document) {
            this.store = store; this.workId = workId; this.chapterId = chapterId; this.draftId = draftId;
            String name = chapterId >= 0 ? document.chapter.title : document.work.title;
            title = WorkInput.blank(name) ? (chapterId >= 0 ? "未命名章节" : "未命名文章") : name;
            String content = chapterId >= 0 ? document.chapter.content : document.work.preface;
            if (content == null) content = "";
            preview = content.substring(0, Math.min(content.length(), 300))
                    .replaceAll("\\[/?[^\\]\\r\\n]+]", "").replaceAll("\\s+", " ").trim();
            savedAt = document.savedAt; submissionUncertain = document.submissionUncertain;
            onlineKey = document.draftKey == null ? DraftCodec.key(workId, chapterId, draftId) : document.draftKey;
            revision = document.revision; pendingSync = document.pendingSync;
        }
        public void delete() throws IOException { store.delete(); }
        public EditorDocument read() throws IOException { return store.read(); }
        public DraftSync synchronizer() { return new DraftSync(store, onlineKey, workId, chapterId); }
    }

    /** Read both the existing four-part keys and new keys with a unique draft ID. */
    public static List<Entry> list(File directory, String api, String userId) throws IOException {
        synchronized (EditorDraftStore.class) {
            List<Entry> entries = new ArrayList<>();
            if (!directory.exists()) return entries;
            File[] files = directory.listFiles((dir, name) -> name.endsWith(".json"));
            if (files == null) throw new IOException("无法读取草稿目录");
            Gson gson = new Gson();
            for (File file : files) {
                try {
                    Envelope envelope = gson.fromJson(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8), Envelope.class);
                    if (envelope == null || envelope.scope == null || envelope.version != 1) continue;
                    String[] parts = envelope.scope.split("\n", -1);
                    if ((parts.length != 4 && parts.length != 5) || !api.equals(parts[0]) || !userId.equals(parts[1])) continue;
                    int workId = Integer.parseInt(parts[2]), chapterId = Integer.parseInt(parts[3]);
                    if (workId < 0 || chapterId < -1 || (chapterId >= 0 && workId == 0)) continue;
                    String draftId = parts.length == 5 ? parts[4] : null;
                    EditorDraftStore store = new EditorDraftStore(directory, api, userId, workId, chapterId, draftId);
                    if (!file.equals(store.file)) continue;
                    EditorDocument document = envelope.document;
                    if (document == null || document.work == null || document.chapter == null || document.tags == null) continue;
                    entries.add(new Entry(store, workId, chapterId, draftId, document));
                } catch (RuntimeException ignored) { /* One malformed file must not hide the other drafts. */ }
            }
            entries.sort(Comparator.comparingLong((Entry item) -> item.savedAt).reversed().thenComparing(item -> item.store.scope));
            return entries;
        }
    }
}
