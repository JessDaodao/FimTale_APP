package com.app.fimtale.network;

import com.app.fimtale.model.*;
import com.app.fimtale.utils.BbCode;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.*;
import okio.Buffer;
import org.junit.Test;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import static org.junit.Assert.*;

public class CurrentApiTest {
    private final AtomicReference<Request> request = new AtomicReference<>();
    private FimTaleApiService api(String data) {
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> {
            request.set(chain.request());
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body(ResponseBody.create(MediaType.get("application/json"), "{\"data\":" + data + "}")).build();
        }).build();
        return new Retrofit.Builder().baseUrl(SiteUrls.API).client(client)
                .addConverterFactory(new ApiDataConverter()).addConverterFactory(GsonConverterFactory.create())
                .build().create(FimTaleApiService.class);
    }
    private static final String WORK = "{\"id\":42,\"title\":\"作品\",\"user\":{\"user_id\":9,\"username\":\"作者\",\"avatar\":\"https://img.example/avatar.png\"},"
            + "\"cover\":\"/cover.png\",\"preface\":\"[b]前言[/b]\",\"type\":2,\"origin\":2,\"length\":1,\"rating\":3,\"publish\":2,"
            + "\"count_view\":100,\"count_character\":2500,\"count_comment\":3,\"count_fav\":8,"
            + "\"tags\":[{\"name\":\"角色\",\"tags\":[{\"id\":1,\"name\":\"暮光闪闪\"}]}]}";

    @Test public void searchUsesJsonQueryAndRankAndReadsItemsTotal() throws Exception {
        TopicListResponse result = api("{\"items\":[" + WORK + "],\"total\":21}")
                .getTopicList(2, SearchQuery.keywords("中文 & \"引号\""), SearchQuery.rank("last_chapter_at")).execute().body();
        assertEquals("/api/search/search_works", request.get().url().encodedPath());
        assertEquals("2", request.get().url().queryParameter("page"));
        JsonObject query = new JsonParser().parse(request.get().url().queryParameter("query")).getAsJsonObject();
        assertEquals("中文 & \"引号\"", query.get("keywords").getAsString());
        assertFalse(request.get().url().toString().contains("APIKey"));
        assertEquals(2, result.getTotalPage());
        Topic work = result.getTopicArray().get(0);
        assertEquals("作者", work.getAuthorName());
        assertEquals(SiteUrls.SITE + "/cover.png", work.getBackground());
        assertEquals("图集", work.getTags().getType());
        assertEquals("Restricted", work.getTags().getRating());
        assertEquals("已完结", work.getTags().getStatus());
        assertEquals("暮光闪闪", work.getTags().getOtherTags().get(0));
        assertEquals(2500, work.getWordCount());
        assertEquals(100, work.getViews());
    }
    @Test public void workKeepsFirstChapterAndReadsPrefaceSeparately() throws Exception {
        TopicDetailResponse result = api("{\"work\":" + WORK + ",\"chapters\":["
                + "{\"id\":102,\"title\":\"第二章\",\"order_num\":2},"
                + "{\"id\":101,\"title\":\"第一章\",\"order_num\":1},"
                + "{\"id\":103,\"status_del\":1,\"order_num\":3}],\"chapter_edges\":null}")
                .getWork(42).execute().body();
        assertEquals("/api/work/get_work", request.get().url().encodedPath());
        assertEquals("42", request.get().url().queryParameter("work_id"));
        assertEquals(101, result.getMenu().get(0).getId());
        assertEquals(2, result.getMenu().size());
        assertEquals("[b]前言[/b]", result.getTopicInfo().getContent());
        assertEquals(9, result.getAuthorInfo().getId());
        assertEquals(Integer.valueOf(101), ChapterNavigation.choices(result, 0).get(0).to);
        assertEquals(Integer.valueOf(102), ChapterNavigation.choices(result, 101).get(0).to);
    }
    @Test public void chapterAndProgressUseDifferentWorkAndChapterIds() throws Exception {
        ChapterResponse chapter = api("{\"chapter\":{\"id\":101,\"work_id\":42,\"title\":\"第一章\",\"content\":\"正文\"}}")
                .getChapter(101).execute().body();
        assertEquals("/api/work/get_chapter", request.get().url().encodedPath());
        assertEquals(42, chapter.chapter.workId);
        api("{\"progress\":0.25}").saveReadingProgress(new ReadProgress(42, 101, 0.25)).execute();
        assertEquals("POST", request.get().method());
        assertEquals("/api/user/update_read_progress", request.get().url().encodedPath());
        Buffer body = new Buffer(); request.get().body().writeTo(body);
        JsonObject payload = new JsonParser().parse(body.readUtf8()).getAsJsonObject();
        assertEquals(42, payload.get("work_id").getAsInt());
        assertEquals(101, payload.get("chapter_id").getAsInt());
        assertEquals(0.25, payload.get("progress").getAsDouble(), 0);
        assertFalse(new Gson().toJson(new ReadProgress(42, 0, 0.5)).contains("chapter_id"));
    }
    @Test public void explicitBranchesDoNotFallThroughToLinearOrder() throws Exception {
        TopicDetailResponse work = (TopicDetailResponse) new ApiDataConverter().decode(
                "{\"data\":{\"chapter_edges\":["
                + "{\"from_chapter_id\":null,\"to_chapter_id\":101,\"label\":\"开始\"},"
                + "{\"from_chapter_id\":101,\"to_chapter_id\":102,\"label\":\"左\"},"
                + "{\"from_chapter_id\":101,\"to_chapter_id\":103,\"label\":\"右\"},"
                + "{\"from_chapter_id\":102,\"to_chapter_id\":104},"
                + "{\"from_chapter_id\":103,\"to_chapter_id\":104},"
                + "{\"from_chapter_id\":104,\"to_chapter_id\":null}]}}", TopicDetailResponse.class);
        assertEquals(2, ChapterNavigation.choices(work, 101).size());
        assertEquals(-1, ChapterNavigation.previous(work, 104));
        assertEquals(101, ChapterNavigation.previous(work, 102));
        assertNull(ChapterNavigation.choices(work, 104).get(0).to);
        assertTrue(ChapterNavigation.choices(work, 999).isEmpty());
    }
    @Test public void historyUsesChapterIdNotHistoryRowIdAndParsesIsoTime() throws Exception {
        HistoryResponse data = api("{\"items\":[{\"id\":999,\"work_id\":42,\"chapter_id\":101,\"progress\":0.5,\"title\":\"第一章\",\"updated_at\":\"2026-10-03T13:00:00+08:00\"}],\"total\":1}")
                .getHistory(1).execute().body();
        HistoryResponse.HistoryTopic item = data.getHistoryTopics().get(0);
        assertEquals(42, item.getWorkId()); assertEquals(101, item.getChapterId());
        assertEquals(java.time.Instant.parse("2026-10-03T05:00:00Z").getEpochSecond(), item.getDateCreated());
    }
    @Test public void userTabsCurationsAndTagGroupsMatchCurrentNesting() throws Exception {
        UserWorksResponse user = api("{\"current_tab\":\"work\",\"content\":{\"items\":[" + WORK + "],\"total\":1}}")
                .getUserTopics("作者", "work", 1).execute().body();
        assertEquals("work", request.get().url().queryParameter("tab"));
        assertEquals(42, user.content.getTopicArray().get(0).getId());
        CuratedResponse curated = api("{\"items\":[{\"id\":999,\"work\":" + WORK + ",\"reason\":\"推荐语\",\"user\":{\"username\":\"推荐者\"}}]}")
                .getCuratedWorks(1).execute().body();
        assertEquals(42, curated.items.get(0).getId());
        assertEquals("推荐者", curated.items.get(0).getRecommenderName());
        List<TagGroup> tags = api("[{\"name\":\"角色\",\"tags\":[{\"id\":1,\"name\":\"暮光\"}]}]").getTags(1, "暮光").execute().body();
        assertEquals("暮光", tags.get(0).tags.get(0).getName());
        assertEquals("暮光", request.get().url().queryParameter("keyword"));
    }
    @Test public void tokenIsAHeaderAndPublicEndpointsNeedNoCredentials() throws Exception {
        CurrentUser user = api("{\"id\":9,\"username\":\"作者\",\"avatar\":\"/avatar.png\"}").getCurrentUser("session-token").execute().body();
        assertEquals("session-token", request.get().header("Token"));
        assertEquals(9, user.id);
        api("{\"items\":null,\"total\":0}").getFavorites(1).execute();
        assertEquals("/api/work/get_favorite_works", request.get().url().encodedPath());
        assertEquals(1, request.get().url().querySize());
        ResponseBody logout = api("null").logout("session-token").execute().body();
        if (logout != null) logout.close();
        assertEquals("POST", request.get().method());
        assertEquals("/api/user/logout", request.get().url().encodedPath());
        assertEquals("session-token", request.get().header("Token"));
    }
    @Test(expected = IOException.class) public void retiredEnvelopeIsRejected() throws Exception {
        new ApiDataConverter().decode("{\"Status\":1,\"TopicArray\":[]}", TopicListResponse.class);
    }
    @Test public void bbCodeImagesAndLiteralBlocksSurviveConversion() {
        String text = BbCode.toMarkdown("[b]粗体[/b]\n[img width=100]/img/a.png[/img]\n[markdown]**正文** [b]字面[/b][/markdown]\n[code][img]literal[/img][/code]");
        assertTrue(text.contains("<b>粗体</b>"));
        assertTrue(text.contains("![](" + SiteUrls.SITE + "/img/a.png)"));
        assertTrue(text.contains("**正文** [b]字面[/b]"));
        assertTrue(text.contains("```\n[img]literal[/img]\n```"));
        assertFalse(BbCode.toMarkdown("[spoiler]秘密[/spoiler]").contains("秘密"));
    }
    @Test public void originsAndClassificationAreExplicit() {
        assertTrue(SiteUrls.isSite(SiteUrls.SITE + "/user/login"));
        assertTrue(SiteUrls.isApi(SiteUrls.API + "work/get_work"));
        assertFalse(SiteUrls.isSite("https://fimtale.dev.evil.example/"));
        assertFalse(SiteUrls.isApi(SiteUrls.SITE + "/api/user/get_user"));
        assertNull(SiteUrls.media("javascript:alert(1)"));
        assertEquals("type:2", new JsonParser().parse(SearchQuery.type(2)).getAsJsonObject().get("filter").getAsString());
    }
}
