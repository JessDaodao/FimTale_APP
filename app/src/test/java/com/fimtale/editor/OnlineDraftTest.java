package com.fimtale.editor;

import com.fimtale.network.ApiDataConverter;
import com.fimtale.network.FimTaleApiService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import static org.junit.Assert.*;

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 34, application = com.fimtale.ResourceApplication.class)
public class OnlineDraftTest {
    @Rule public TemporaryFolder directory = new TemporaryFolder();
    private final Gson gson = new Gson();
    private final Map<String, OnlineDraft> cloud = new LinkedHashMap<>();
    private Request request;
    private boolean offline, loseSaveResponse;
    private int saves;
    private final String key = "work:new:11111111-2222-4333-8444-555555555555";
    private DraftRemote remote() {
        OkHttpClient client = new OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor(chain -> {
            request = chain.request();
            assertEquals("fixture-token", request.header("Token"));
            if (offline) throw new IOException("offline");
            String path = request.url().encodedPath(); String data = "null"; int status = 200;
            if (path.endsWith("get_draft")) {
                OnlineDraft value = cloud.get(request.url().queryParameter("draft_key"));
                if (value == null) status = 404; else data = gson.toJson(value);
            } else if (path.endsWith("get_drafts")) data = "[]";
            else {
                Buffer buffer = new Buffer(); request.body().writeTo(buffer);
                JsonObject input = new JsonParser().parse(buffer.readUtf8()).getAsJsonObject();
                String key = input.get("draft_key").getAsString();
                OnlineDraft existing = cloud.get(key); long revision = existing == null ? 0 : existing.revision;
                if (input.get("expected_revision").getAsLong() != revision) status = 409;
                else if (path.endsWith("save_draft")) {
                    OnlineDraft draft = new OnlineDraft(); draft.key = key; draft.revision = revision + 1;
                    draft.payload = input.getAsJsonObject("payload"); draft.updatedAt = "2026-10-03T12:00:00+08:00";
                    cloud.put(key, draft); saves++; data = gson.toJson(draft);
                    if (loseSaveResponse) { loseSaveResponse = false; throw new IOException("response lost"); }
                } else if (path.endsWith("delete_draft")) cloud.remove(key);
                else throw new AssertionError("Unexpected route " + path);
            }
            return new okhttp3.Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status).message("Fixture")
                    .body(ResponseBody.create(MediaType.get("application/json"), "{\"data\":" + data + ",\"msg\":\"conflict\"}")).build();
        }).build();
        FimTaleApiService api = new Retrofit.Builder().baseUrl("https://fixture.invalid/api/").client(client)
                .addConverterFactory(new ApiDataConverter()).addConverterFactory(GsonConverterFactory.create()).build().create(FimTaleApiService.class);
        return new DraftRemote(api, "fixture-token");
    }
    private EditorDraftStore store(File root) { return new EditorDraftStore(root, "api", "9", 0, -1, "11111111-2222-4333-8444-555555555555"); }
    private DraftSync sync(EditorDraftStore local) { return new DraftSync(local, key, 0, -1); }
    private EditorDocument document(String title) {
        EditorDocument document = new EditorDocument(); document.work.title = title; document.work.preface = "[b]正文[/b]"; return document;
    }
    @Test public void websitePayloadRoundTripPreservesTagObjectsAndUnknownFields() {
        OnlineDraft draft = new OnlineDraft(); draft.key = key; draft.revision = 7;
        draft.payload = new JsonParser().parse("{\"title\":\"网站草稿\",\"preface\":\"[b]正文[/b]\",\"type\":1,"
                + "\"cover\":\"/cover.png\",\"prequel_id\":12,\"extension\":\"keep\",\"selected_tags\":{"
                + "\"regular\":[{\"id\":1,\"name\":\"冒险\",\"intro\":\"保留\"}],"
                + "\"characters\":[{\"id\":2,\"name\":\"暮光\",\"icon\":\"/icon.png\"}]}}").getAsJsonObject();
        EditorDocument document = DraftCodec.document(draft, 0, -1); document.work.title = "手机修改";
        JsonObject payload = DraftCodec.payload(document, false);
        assertEquals("手机修改", payload.get("title").getAsString()); assertEquals("keep", payload.get("extension").getAsString());
        assertEquals("[b]正文[/b]", payload.get("preface").getAsString());
        assertEquals("/icon.png", payload.getAsJsonObject("selected_tags").getAsJsonArray("characters").get(0).getAsJsonObject().get("icon").getAsString());
        assertEquals("保留", payload.getAsJsonObject("selected_tags").getAsJsonArray("regular").get(0).getAsJsonObject().get("intro").getAsString());
        assertFalse(payload.has("tag_ids")); assertFalse(payload.has("pendingSync")); assertFalse(payload.has("revision"));
        document.tags.remove(1); payload = DraftCodec.payload(document, false);
        assertEquals(0, payload.getAsJsonObject("selected_tags").getAsJsonArray("regular").size());
    }
    @Test public void canonicalKeysAndChapterTargetsMatchWebsiteRoutes() {
        assertEquals("work:42", DraftCodec.key(42, -1, null)); assertEquals("chapter:81", DraftCodec.key(42, 81, null));
        String uuid = "11111111-2222-4333-8444-555555555555";
        assertEquals(key, DraftCodec.key(0, -1, uuid));
        assertEquals("chapter:new:42:" + uuid, DraftCodec.key(42, 0, uuid));
        JsonObject payload = new JsonObject(); payload.addProperty("work_id", 42);
        DraftCodec.Target target = DraftCodec.target("chapter:81", payload);
        assertEquals(42, target.workId); assertEquals(81, target.chapterId);
        assertEquals(DraftCodec.key(0, -1, null), DraftCodec.key(0, -1, ""));
        assertEquals(DraftCodec.key(42, 0, null), DraftCodec.key(42, 0, ""));
    }
    @Test public void migratedLegacyDraftReopensUnderTheSameOnlineKey() throws Exception {
        File root = directory.newFolder();
        EditorDraftStore local = new EditorDraftStore(root, "api", "9", 0, -1, null);
        local.write(document("legacy"));
        EditorDraftStore.Entry entry = EditorDraftStore.list(root, "api", "9").get(0);
        DraftRemote remote = remote(); DraftSync migration = entry.synchronizer();
        migration.save(remote, migration.load(remote));

        // DraftsActivity passes an empty intent extra when a legacy slot is absent.
        EditorDraftStore reopenedStore = new EditorDraftStore(root, "api", "9", 0, -1, "");
        DraftSync reopened = new DraftSync(reopenedStore, DraftCodec.key(0, -1, ""), 0, -1);
        EditorDocument restored = reopened.load(remote);
        assertNotNull(restored); assertEquals("legacy", restored.work.title);
        restored.work.title = "edited"; reopened.save(remote, restored);
        assertEquals(1, cloud.size()); assertEquals(2, cloud.get(entry.onlineKey).revision);
        assertEquals("edited", cloud.get(entry.onlineKey).payload.get("title").getAsString());
    }
    @Test public void listAndSaveUseCurrentRoutesAndRevisionFields() throws Exception {
        DraftRemote remote = remote(); remote.list();
        assertEquals("/api/work/get_drafts", request.url().encodedPath());
        assertEquals(java.util.Arrays.asList("work:", "chapter:"), request.url().queryParameterValues("prefix"));
        OnlineDraft saved = remote.save(new OnlineDraft.Save(key, 0, DraftCodec.payload(document("new"), false)));
        assertEquals(1, saved.revision); assertEquals("POST", request.method());
        assertEquals("/api/work/save_draft", request.url().encodedPath());
        assertEquals(key, remote.get(key).key);
        assertEquals(key, request.url().queryParameter("draft_key"));
        remote.delete(key, 1); assertTrue(cloud.isEmpty());
    }
    @Test public void staleSnapshotsInOneEditorUseLatestAcknowledgedRevision() throws Exception {
        EditorDraftStore store = store(directory.newFolder()); DraftSync sync = sync(store); DraftRemote remote = remote();
        assertNull(sync.load(remote));
        sync.save(remote, document("first"));
        EditorDocument second = document("second"); assertEquals(0, second.revision);
        sync.save(remote, second);
        assertEquals(2, cloud.get(key).revision); assertEquals("second", store.read().work.title); assertFalse(store.read().pendingSync);
    }
    @Test public void conflictsKeepPendingContentAndCanReloadTheServerVersion() throws Exception {
        DraftRemote remote = remote(); DraftSync first = sync(store(directory.newFolder()));
        first.load(remote); first.save(remote, document("initial"));
        EditorDraftStore secondStore = store(directory.newFolder()); DraftSync second = sync(secondStore); second.load(remote);
        first.save(remote, document("newer online"));
        try { second.save(remote, document("my unsynced text")); fail(); }
        catch (DraftRemote.Failure conflict) { assertEquals(409, conflict.status); }
        assertTrue(second.conflict); assertEquals("my unsynced text", secondStore.read().work.title); assertTrue(secondStore.read().pendingSync);
        EditorDocument loaded = second.resolve(remote, secondStore.read(), false);
        assertEquals("newer online", loaded.work.title); assertFalse(loaded.pendingSync); assertFalse(second.conflict);
    }
    @Test public void explicitConflictOverwriteUsesTheCurrentServerRevision() throws Exception {
        DraftRemote remote = remote(); DraftSync first = sync(store(directory.newFolder())); first.load(remote); first.save(remote, document("first"));
        EditorDraftStore local = store(directory.newFolder()); DraftSync second = sync(local); second.load(remote);
        first.save(remote, document("remote"));
        try { second.save(remote, document("local")); } catch (DraftRemote.Failure expected) { assertEquals(409, expected.status); }
        second.resolve(remote, local.read(), true);
        assertEquals(3, cloud.get(key).revision); assertEquals("local", cloud.get(key).payload.get("title").getAsString());
    }
    @Test public void offlineEditsAndDeleteConflictsKeepTheRecoveryCopy() throws Exception {
        DraftRemote remote = remote(); EditorDraftStore local = store(directory.newFolder()); DraftSync sync = sync(local);
        sync.load(remote); sync.save(remote, document("saved"));
        offline = true;
        try { sync.save(remote, document("offline change")); fail(); } catch (IOException expected) {}
        assertEquals("offline change", local.read().work.title);
        DraftSync reopened = sync(local); assertEquals("offline change", reopened.load(remote).work.title);
        offline = false;
        remote.save(new OnlineDraft.Save(key, 1, DraftCodec.payload(document("other device"), false)));
        try { sync.discard(remote); fail(); } catch (DraftRemote.Failure expected) { assertEquals(409, expected.status); }
        assertNotNull(local.read()); assertNotNull(cloud.get(key));
    }
    @Test public void lostSaveResponseReconcilesOnReopenWithoutDuplicatingDrafts() throws Exception {
        EditorDraftStore local = store(directory.newFolder()); DraftSync sync = sync(local); DraftRemote remote = remote(); sync.load(remote);
        loseSaveResponse = true;
        try { sync.save(remote, document("saved but response lost")); fail(); } catch (IOException expected) {}
        assertTrue(local.read().pendingSync);
        EditorDocument restored = sync(local).load(remote);
        assertFalse(restored.pendingSync); assertEquals(1, restored.revision); assertEquals(1, saves); assertEquals(1, cloud.size());
    }
}
