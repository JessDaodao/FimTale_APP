package com.fimtale.editor;

import com.fimtale.R;
import com.fimtale.utils.AppStrings;

import com.google.gson.JsonObject;
import java.io.IOException;

/** Serial optimistic-concurrency synchronization, with a durable pending copy. */
public final class DraftSync {
    private final EditorDraftStore local;
    private final String key;
    private final int workId, chapterId;
    private long revision;
    private JsonObject baseline;
    private boolean known;
    public boolean conflict;
    public String warning;
    public DraftSync(EditorDraftStore local, String key, int workId, int chapterId) {
        this.local = local; this.key = key; this.workId = workId; this.chapterId = chapterId;
    }
    public EditorDocument load(DraftRemote remote) throws IOException {
        EditorDocument pending = local.read(); warning = null; conflict = false;
        if (pending != null) revision = pending.revision;
        OnlineDraft server;
        try { server = remote.get(key); known = true; }
        catch (IOException e) {
            if (pending == null) throw e;
            warning = AppStrings.get(R.string.drafts_local_restored); return pending;
        }
        baseline = server == null ? null : server.payload;
        long serverRevision = server == null ? 0 : server.revision;
        if (pending != null && (pending.pendingSync || pending.submissionUncertain)) {
            pending.draftKey = key;
            JsonObject payload = DraftCodec.payload(pending, chapterId >= 0);
            if (server != null && payload.equals(server.payload)) {
                revision = serverRevision; pending.revision = revision; pending.pendingSync = false;
                pending.onlinePayload = DraftCodec.copy(server.payload); local.write(pending);
            } else if (revision != serverRevision) {
                conflict = true; warning = AppStrings.get(R.string.drafts_conflict_notice);
            }
            return pending;
        }
        revision = serverRevision;
        if (server == null) { local.delete(); return null; }
        EditorDocument document = DraftCodec.document(server, workId, chapterId); local.write(document); return document;
    }
    public void persist(EditorDocument document) throws IOException {
        document.draftKey = key; document.revision = revision;
        JsonObject payload = DraftCodec.payload(document, chapterId >= 0);
        document.pendingSync = conflict || baseline == null || !baseline.equals(payload);
        local.write(document);
    }
    public EditorDocument save(DraftRemote remote, EditorDocument document) throws IOException {
        persist(document);
        if (conflict) throw new DraftRemote.Failure(409, AppStrings.get(R.string.drafts_conflict_updated));
        JsonObject payload = DraftCodec.payload(document, chapterId >= 0);
        if (known && baseline != null && baseline.equals(payload)) { document.pendingSync = false; return document; }
        try {
            OnlineDraft saved = remote.save(new OnlineDraft.Save(key, revision, payload));
            revision = saved.revision; baseline = payload; known = true;
            document.revision = revision; document.pendingSync = false; document.onlinePayload = DraftCodec.copy(payload);
            local.write(document); return document;
        } catch (DraftRemote.Failure failure) {
            if (failure.status == 409) conflict = true;
            throw failure;
        }
    }
    public EditorDocument resolve(DraftRemote remote, EditorDocument document, boolean keepLocal) throws IOException {
        OnlineDraft server = remote.get(key);
        revision = server == null ? 0 : server.revision; baseline = server == null ? null : server.payload; known = true;
        conflict = false;
        if (keepLocal) return save(remote, document);
        if (server == null) { local.delete(); return null; }
        EditorDocument result = DraftCodec.document(server, workId, chapterId); local.write(result); return result;
    }
    public void discard(DraftRemote remote) throws IOException {
        try { remote.delete(key, revision); }
        catch (DraftRemote.Failure failure) { if (failure.status == 409) conflict = true; throw failure; }
        local.delete(); revision = 0; baseline = null; known = true; conflict = false;
    }
}
