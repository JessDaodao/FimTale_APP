package com.fimtale.network;

import com.fimtale.model.*;
import com.fimtale.utils.BbCode;
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
    @Test public void timelineUsesAuthenticatedPaginationAndDiscriminatedEntities() throws Exception {
        List<TimelineItem> items = api("[{\"type\":7,\"entity_id\":101,\"created_at\":\"2026-10-05T01:00:00Z\","
                + "\"from_user\":{\"username\":\"作者\"},\"entity\":{\"id\":101,\"work_id\":42,"
                + "\"title\":\"新章节\",\"content\":\"[b]正文[/b]\",\"count_character\":2000}}]")
                .getTimeline("session-token", 2, 12).execute().body();
        assertEquals("GET", request.get().method());
        assertEquals("/api/user/get_timeline", request.get().url().encodedPath());
        assertEquals("session-token", request.get().header("Token"));
        assertEquals("2", request.get().url().queryParameter("page"));
        assertEquals("12", request.get().url().queryParameter("per_page"));
        assertEquals("作者", items.get(0).fromUser.getUserName());
        assertEquals("/work/42/chapter/101", items.get(0).path());
        assertEquals("[b]正文[/b]", items.get(0).body());
        assertNull(api("null").getTimeline("session-token", 1, 12).execute().body());
    }

    @Test public void timelineReadCountsSpaceAndForwardingMatchWebsiteRoutes() throws Exception {
        assertEquals(Integer.valueOf(7), api("7").getTimelineUpdateCount("session-token").execute().body());
        assertEquals("/api/user/get_timeline_update_count", request.get().url().encodedPath());
        api("null").readTimeline("session-token").execute();
        assertEquals("POST", request.get().method());
        assertEquals("/api/user/read_timeline", request.get().url().encodedPath());
        api("null").activateUserSpace("session-token").execute();
        assertEquals("/api/user/activate_user_space", request.get().url().encodedPath());
        api("null").highlightWorkComment("session-token", new TimelineItem.Highlight(91)).execute();
        assertEquals("/api/work/highlight_timeline_comment", request.get().url().encodedPath());
        assertEquals("session-token", request.get().header("Token"));
        Buffer body = new Buffer(); request.get().body().writeTo(body);
        assertEquals(91, new JsonParser().parse(body.readUtf8()).getAsJsonObject().get("comment_id").getAsInt());
        api("null").highlightChannelComment("session-token", new TimelineItem.Highlight(92)).execute();
        assertEquals("POST", request.get().method());
        assertEquals("/api/channel/highlight_timeline_comment", request.get().url().encodedPath());
    }
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
                .getCuratedWorks(1, 5).execute().body();
        assertEquals("1", request.get().url().queryParameter("page"));
        assertEquals("5", request.get().url().queryParameter("per_page"));
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
        assertTrue(text.contains("src=\"" + SiteUrls.SITE + "/img/a.png\""));
        assertTrue(text.contains("<strong>正文</strong> [b]字面[/b]"));
        assertTrue(text.contains("<pre><code>[img]literal[/img]</code></pre>"));
        assertTrue(BbCode.toMarkdown("[spoiler]秘密[/spoiler]").contains("<span data-spoiler=\"true\">秘密</span>"));
    }
    @Test public void originsAndClassificationAreExplicit() {
        assertTrue(SiteUrls.isSite(SiteUrls.SITE + "/user/login"));
        assertTrue(SiteUrls.isApi(SiteUrls.API + "work/get_work"));
        assertFalse(SiteUrls.isSite("https://fimtale.dev.evil.example/"));
        assertFalse(SiteUrls.isApi(SiteUrls.SITE + "/api/user/get_user"));
        assertNull(SiteUrls.media("javascript:alert(1)"));
        assertNull(SiteUrls.media("http://img.example/avatar.png"));
        assertEquals("type:2", new JsonParser().parse(SearchQuery.type(2)).getAsJsonObject().get("filter").getAsString());
    }
    @Test public void bbCodeCommentReferencesResolveTheirWorkAndChapter() throws Exception {
        CommentResponse data = api("{\"comment\":{\"id\":8,\"work_id\":42,\"chapter_id\":9,\"content\":\"quoted\"}}")
                .getComment(8).execute().body();
        assertEquals("/api/work/get_comment", request.get().url().encodedPath());
        assertEquals("8", request.get().url().queryParameter("comment_id"));
        assertEquals(42, data.comment.workId);
        assertEquals(9, data.comment.chapterId);
    }
    @Test public void workViewerReadsVotesAndAllFavoriteFolders() throws Exception {
        TopicDetailResponse result = api("{\"work\":{\"id\":42,\"count_like\":12},\"viewer\":{"
                + "\"operations\":[{\"operation\":3},{\"operation\":1}],"
                + "\"favs\":[{\"folder_id\":null},{\"folder_id\":7},{\"folder_id\":7}]}}")
                .getWorkViewer("session-token", 42).execute().body();
        assertTrue(result.viewer.isLiked());
        assertEquals(1, result.viewer.highPraiseCount());
        assertEquals(new java.util.LinkedHashSet<>(java.util.Arrays.asList(0, 7)), result.viewer.folderIds());
        assertEquals(12, result.getTopicInfo().getLikeCount());
        assertEquals("session-token", request.get().header("Token"));
        TopicDetailResponse anonymous = api("{\"work\":{\"id\":42},\"viewer\":{\"operations\":null,\"favs\":null}}")
                .getWorkViewer("", 42).execute().body();
        assertFalse(anonymous.viewer.isLiked());
        assertTrue(anonymous.viewer.folderIds().isEmpty());
    }

    @Test public void voteAndFavoriteRequestsUseCurrentRoutesAndDefaultFolderOmission() throws Exception {
        api("null").voteWork("session-token", new WorkInteractions.Vote(42)).execute();
        assertEquals("/api/work/do_work_vote", request.get().url().encodedPath());
        assertEquals("POST", request.get().method());
        assertEquals("session-token", request.get().header("Token"));
        Buffer vote = new Buffer(); request.get().body().writeTo(vote);
        JsonObject voteBody = new JsonParser().parse(vote.readUtf8()).getAsJsonObject();
        assertEquals(42, voteBody.get("work_id").getAsInt());
        assertEquals(1, voteBody.get("operation").getAsInt());
        api("null").highPraiseWork("session-token", new WorkInteractions.HighPraise(42, 2)).execute();
        assertEquals("/api/work/do_work_high_praise", request.get().url().encodedPath());
        Buffer highPraise = new Buffer(); request.get().body().writeTo(highPraise);
        JsonObject highPraiseBody = new JsonParser().parse(highPraise.readUtf8()).getAsJsonObject();
        assertEquals(42, highPraiseBody.get("work_id").getAsInt());
        assertEquals(2, highPraiseBody.get("count").getAsInt());
        api("{\"id\":9,\"folder_id\":null}").addFavoriteWork("session-token", new WorkInteractions.FavoriteRequest(42, 0)).execute();
        assertEquals("/api/work/add_favorite_work", request.get().url().encodedPath());
        Buffer favorite = new Buffer(); request.get().body().writeTo(favorite);
        JsonObject favoriteBody = new JsonParser().parse(favorite.readUtf8()).getAsJsonObject();
        assertEquals(42, favoriteBody.get("work_id").getAsInt());
        assertFalse(favoriteBody.has("folder_id"));
        api("null").removeFavoriteWork("session-token", new WorkInteractions.FavoriteRequest(42, 7)).execute();
        assertEquals("/api/work/remove_favorite_work", request.get().url().encodedPath());
        Buffer removed = new Buffer(); request.get().body().writeTo(removed);
        assertEquals(7, new JsonParser().parse(removed.readUtf8()).getAsJsonObject().get("folder_id").getAsInt());
        List<WorkInteractions.Folder> folders = api("[{\"id\":7,\"name\":\"追更\"}]").getFavoriteFolders("session-token").execute().body();
        assertEquals("/api/user/get_favorite_folders", request.get().url().encodedPath());
        assertEquals("追更", folders.get(0).name);
    }

    @Test public void changingFavoritesPreservesUnchangedWebsiteMemberships() {
        java.util.Set<Integer> initial = new java.util.LinkedHashSet<>(java.util.Arrays.asList(0, 7));
        java.util.Set<Integer> selected = new java.util.LinkedHashSet<>(java.util.Arrays.asList(7, 9));
        List<WorkInteractions.Change> changes = WorkInteractions.changes(initial, selected);
        assertEquals(2, changes.size());
        assertEquals(9, changes.get(0).folderId); assertTrue(changes.get(0).add);
        assertEquals(0, changes.get(1).folderId); assertFalse(changes.get(1).add);
        assertTrue(WorkInteractions.changes(initial, initial).isEmpty());
        assertEquals(2, WorkInteractions.changes(initial, java.util.Collections.emptySet()).size());
    }

    @Test public void commentsReadCurrentItemsAuthorReplyAndChapterWithoutFilteringWorkComments() throws Exception {
        WorkCommentsResponse result = api("{\"items\":[{\"id\":91,\"chapter_id\":101,\"title\":\"第一章\","
                + "\"reply_comment_id\":89,\"content\":\"[b]评论[/b]\",\"created_at\":\"2026-10-04T12:00:00+08:00\","
                + "\"user\":{\"user_id\":3,\"username\":\"读者\"}}],\"total\":17}")
                .getWorkComments(42, 2, 16, "created_at", "asc").execute().body();
        assertEquals("/api/work/get_comments", request.get().url().encodedPath());
        assertEquals("42", request.get().url().queryParameter("work_id"));
        assertEquals("2", request.get().url().queryParameter("page"));
        assertEquals("16", request.get().url().queryParameter("per_page"));
        assertEquals("asc", request.get().url().queryParameter("order_option"));
        assertNull(request.get().url().queryParameter("chapter_id"));
        assertEquals(2, result.totalPages(16));
        Comment comment = result.getItems().get(0);
        assertEquals("读者", comment.getUserName()); assertEquals(89, comment.replyCommentId);
        assertEquals("第一章", comment.getChapterTitle());
        assertEquals("<div><b>评论</b></div>", BbCode.toMarkdown(comment.getContent()));
        comment.statusDel = 1;
        assertEquals("该评论已删除", comment.getContent());
        WorkCommentsResponse empty = api("{\"items\":null,\"total\":0}").getWorkComments(42, 1, 16, "created_at", "desc").execute().body();
        assertTrue(empty.getItems().isEmpty()); assertEquals(1, empty.totalPages(16));
    }

}
