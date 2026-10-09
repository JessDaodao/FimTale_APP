package com.fimtale.editor;

import com.fimtale.model.UserAuth;
import com.fimtale.network.ApiDataConverter;
import com.fimtale.network.FimTaleApiService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
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
public class AuthoringTest {
    @Rule public TemporaryFolder directory = new TemporaryFolder();
    private final AtomicReference<Request> request = new AtomicReference<>();
    private FimTaleApiService api(String data) {
        OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain -> {
            request.set(chain.request());
            return new okhttp3.Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(ResponseBody.create(MediaType.get("application/json"), "{\"data\":" + data + "}")).build();
        }).build();
        return new Retrofit.Builder().baseUrl("https://api.fimtale.dev/api/").client(http)
                .addConverterFactory(new ApiDataConverter()).addConverterFactory(GsonConverterFactory.create()).build().create(FimTaleApiService.class);
    }
    private JsonObject sentBody() throws IOException {
        Buffer buffer = new Buffer(); request.get().body().writeTo(buffer);
        return new JsonParser().parse(buffer.readUtf8()).getAsJsonObject();
    }
    @Test public void editsRequestRawContentAndPreserveMetadataAndAllTags() throws Exception {
        WorkEditResponse response = api("{\"work\":{\"id\":42,\"title\":\"标题\",\"preface\":\"[b]原始正文[/b]\","
                + "\"intro\":\"简介\",\"type\":1,\"length\":3,\"rating\":2,\"origin\":3,\"publish\":4,"
                + "\"origin_link\":\"https://source.example/story\",\"cover\":\"/image/cover.png\",\"prequel_id\":12,"
                + "\"user\":{\"user_id\":9},\"status_review\":2,\"tags\":[{\"name\":\"角色\",\"tags\":[{\"id\":1,\"name\":\"暮光\"}]},"
                + "{\"name\":\"其他标签\",\"tags\":[{\"id\":81,\"name\":\"oc:角色\"}]}]}}")
                .getWorkForEdit(42, true).execute().body();
        assertEquals("true", request.get().url().queryParameter("for_edit"));
        assertEquals("42", request.get().url().queryParameter("work_id"));
        EditorDocument draft = response.toDocument();
        draft.work.title = "修改后的标题";
        api("{\"id\":42}").saveWork("owner-token", draft.work, "captcha+&中文", "hcaptcha").execute();
        JsonObject sent = sentBody();
        assertEquals("POST", request.get().method());
        assertEquals("/api/work/create_update_work", request.get().url().encodedPath());
        assertEquals("owner-token", request.get().header("Token"));
        assertEquals("captcha+&中文", request.get().url().queryParameter("captcha_response"));
        assertEquals("hcaptcha", request.get().url().queryParameter("captcha_type"));
        assertEquals("[b]原始正文[/b]", sent.get("preface").getAsString());
        assertEquals("/image/cover.png", sent.get("cover").getAsString());
        assertEquals(12, sent.get("prequel_id").getAsInt());
        assertEquals(3, sent.get("length").getAsInt());
        assertEquals(2, sent.get("rating").getAsInt());
        assertEquals(4, sent.get("publish").getAsInt());
        assertEquals("https://source.example/story", sent.get("origin_link").getAsString());
        assertEquals("[1,81]", sent.get("tag_ids").toString());
        assertFalse(sent.has("tags")); assertFalse(sent.has("user")); assertFalse(sent.has("status_review"));
        assertFalse(sent.has("captcha_response")); assertFalse(sent.has("submissionUncertain"));
        assertEquals("12", draft.prequelText);
    }
    @Test public void newWorkOmitsIdAndSendsEmptyTagsToAllowRemoval() throws Exception {
        WorkInput work = new WorkInput(); work.title = "文章"; work.preface = "序言"; work.intro = "简介";
        SaveResult result = api("{\"id\":89}").saveWork("token", work, "captcha", "turnstile").execute().body();
        assertEquals(89, result.id); assertFalse(sentBody().has("id")); assertEquals("[]", sentBody().get("tag_ids").toString());
    }
    @Test public void chaptersUseIndependentIdsAndNoCaptcha() throws Exception {
        api("{\"chapter\":{\"id\":81,\"work_id\":42,\"content\":\"[i]正文[/i]\"}}")
                .getChapterForEdit(81, true).execute();
        assertEquals("81", request.get().url().queryParameter("chapter_id"));
        assertEquals("true", request.get().url().queryParameter("for_edit"));
        ChapterInput chapter = new ChapterInput(); chapter.workId = 42; chapter.title = "第一章"; chapter.content = "[i]正文[/i]";
        api("{\"id\":81}").saveChapter("token", chapter).execute();
        assertEquals("/api/work/create_update_chapter", request.get().url().encodedPath());
        assertNull(request.get().url().query()); assertFalse(sentBody().has("id"));
        chapter.id = 81;
        api("{\"id\":81}").saveChapter("token", chapter).execute();
        assertEquals(81, sentBody().get("id").getAsInt()); assertEquals(42, sentBody().get("work_id").getAsInt());
        assertEquals(4, sentBody().entrySet().size());
    }
    @Test public void tagSearchUsesRepeatedTypedQueryAndPagination() throws Exception {
        api("[]").getEditorTags(3, 30, "暮光", Arrays.asList("角色", "其他标签")).execute();
        assertEquals(Arrays.asList("角色", "其他标签"), request.get().url().queryParameterValues("type_names"));
        assertEquals("3", request.get().url().queryParameter("page")); assertEquals("30", request.get().url().queryParameter("per_page"));
    }
    @Test public void draftsSurviveRestartAndAreIsolatedByAccountSiteAndEntity() throws Exception {
        File root = directory.newFolder();
        EditorDraftStore first = new EditorDraftStore(root, "https://api.one/", "9", 42, 81);
        EditorDocument document = new EditorDocument(); document.chapter.id = 81; document.chapter.workId = 42;
        document.chapter.content = "未完成正文🐴\n[b]草稿[/b]".repeat(10000);
        document.tags.put(81, "角色"); document.submissionUncertain = true; document.prequelText = "12";
        first.write(document);
        EditorDocument read = new EditorDraftStore(root, "https://api.one/", "9", 42, 81).read();
        assertEquals(document.chapter.content, read.chapter.content); assertTrue(read.submissionUncertain);
        assertEquals("角色", read.tags.get(81)); assertEquals("12", read.prequelText);
        assertNull(new EditorDraftStore(root, "https://api.one/", "10", 42, 81).read());
        assertNull(new EditorDraftStore(root, "https://api.two/", "9", 42, 81).read());
        assertNull(new EditorDraftStore(root, "https://api.one/", "9", 42, -1).read());
        assertNull(new EditorDraftStore(root, "https://api.one/", "9", 43, 81).read());
        EditorDraftStore other = new EditorDraftStore(root, "https://api.one/", "9", 42, 82); other.write(new EditorDocument());
        first.delete(); assertNull(first.read()); assertNotNull(other.read());
    }
    @Test(expected = IOException.class) public void corruptDraftIsReportedInsteadOfSilentlyDiscarded() throws Exception {
        File root = directory.newFolder(); EditorDraftStore store = new EditorDraftStore(root, "api", "9", 0, -1);
        store.write(new EditorDocument());
        Files.write(root.listFiles()[0].toPath(), "broken".getBytes(StandardCharsets.UTF_8)); store.read();
    }
    @Test public void draftBoxListsIndependentNewDraftsAndExistingDraftsBySaveTime() throws Exception {
        File root = directory.newFolder();
        EditorDocument older = new EditorDocument(); older.work.title = "旧草稿"; older.savedAt = 100;
        new EditorDraftStore(root, "api", "9", 0, -1).write(older);
        EditorDocument first = new EditorDocument(); first.work.title = "第一篇"; first.savedAt = 200;
        first.work.preface = "[b]正文[/b]\n第二行";
        new EditorDraftStore(root, "api", "9", 0, -1, "first").write(first);
        EditorDocument second = new EditorDocument(); second.work.title = "第二篇"; second.savedAt = 300;
        new EditorDraftStore(root, "api", "9", 0, -1, "second").write(second);
        EditorDocument chapter = new EditorDocument(); chapter.chapter.title = "章节草稿"; chapter.savedAt = 400;
        chapter.submissionUncertain = true;
        new EditorDraftStore(root, "api", "9", 42, 0, "chapter").write(chapter);
        new EditorDraftStore(root, "api", "10", 0, -1, "hidden").write(second);
        new EditorDraftStore(root, "other-api", "9", 0, -1, "hidden").write(second);
        java.util.List<EditorDraftStore.Entry> entries = EditorDraftStore.list(root, "api", "9");
        assertEquals(4, entries.size());
        assertEquals("章节草稿", entries.get(0).title); assertEquals(42, entries.get(0).workId);
        assertTrue(entries.get(0).submissionUncertain);
        assertEquals("second", entries.get(1).draftId);
        assertEquals("正文 第二行", entries.get(2).preview);
        assertNull(entries.get(3).draftId); assertEquals("旧草稿", entries.get(3).title);
        entries.get(1).delete();
        assertEquals(3, EditorDraftStore.list(root, "api", "9").size());
        assertNotNull(new EditorDraftStore(root, "api", "9", 0, -1, "first").read());
        assertNull(new EditorDraftStore(root, "api", "9", 0, -1, "second").read());
        assertEquals(1, EditorDraftStore.list(root, "api", "10").size());
    }
    @Test public void draftBoxSkipsCorruptFilesAndKeepsOriginalDraftAddress() throws Exception {
        File root = directory.newFolder();
        EditorDraftStore existing = new EditorDraftStore(root, "api", "9", 42, -1);
        EditorDocument document = new EditorDocument(); document.work.preface = "原有草稿"; existing.write(document);
        Files.write(new File(root, "broken.json").toPath(), "broken".getBytes(StandardCharsets.UTF_8));
        java.util.List<EditorDraftStore.Entry> entries = EditorDraftStore.list(root, "api", "9");
        assertEquals(1, entries.size());
        EditorDraftStore.Entry entry = entries.get(0);
        assertEquals("原有草稿", new EditorDraftStore(root, "api", "9", entry.workId, entry.chapterId, entry.draftId).read().work.preface);
    }
    @Test public void validationMatchesCurrentTaxonomyAndChapterRequirements() {
        WorkInput work = new WorkInput(); assertNotNull(work.validate());
        work.title = "标题"; work.preface = "序言"; work.intro = "简介"; assertNull(work.validate());
        work.length = 0; assertNotNull(work.validate());
        work.type = 3; work.title = ""; work.intro = ""; work.rating = work.origin = work.publish = 0; assertNull(work.validate());
        work.preface = " \n"; assertNotNull(work.validate());
        work.type = 4; work.preface = "公告"; work.title = "标题"; work.intro = "简介"; assertNull(work.validate());
        work.id = 42; work.prequelId = 42; assertNotNull(work.validate());
        ChapterInput chapter = new ChapterInput(); chapter.title = "章节"; chapter.content = "正文";
        assertNotNull(chapter.validate()); chapter.workId = 42; assertNull(chapter.validate());
    }
    @Test public void editRightsSeparateOwnershipRoleAndPublishingQualification() throws Exception {
        UserAuth auth = api("{\"user_id\":9,\"role_id\":1,\"qualify_status\":2}").getUserAuth("token").execute().body();
        assertTrue(auth.canEdit(9)); assertFalse(auth.canEdit(10)); assertTrue(auth.canPublish());
        auth.roleId = 3; assertFalse(auth.canEdit(10)); auth.roleId = 4; assertTrue(auth.canEdit(10));
        auth.qualifyStatus = 1; assertFalse(auth.canPublish()); auth.userId = 0; assertFalse(auth.canEdit(9));
    }
}
