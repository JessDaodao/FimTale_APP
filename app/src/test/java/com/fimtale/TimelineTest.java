package com.fimtale;

import com.fimtale.model.TimelineFeed;
import com.fimtale.model.TimelineItem;
import com.fimtale.model.UserAuth;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 34, application = com.fimtale.ResourceApplication.class)
public class TimelineTest {
    private TimelineItem item(int type, int id, String extra) {
        return new Gson().fromJson("{\"type\":" + type + ",\"entity_id\":" + id
                + ",\"entity\":{\"id\":" + id + extra + "}}", TimelineItem.class);
    }
    @Test public void commentLinksRetainWorkChapterAndChannelAnchors() {
        assertEquals("/work/42#comment-91", item(1, 91, ",\"work_id\":42").path());
        assertEquals("/work/42/chapter/101#comment-91", item(1, 91, ",\"work_id\":42,\"chapter_id\":101").path());
        assertEquals("/channel/7#comment-92", item(14, 92, ",\"channel_id\":7").path());
        assertNull(item(1, 91, "").path());
        assertNull(item(7, 101, "").path());
        assertNull(item(14, 92, "").path());
    }
    @Test public void workAndChannelContextHaveSeparateDestinationsAndPreserveBbcode() {
        TimelineItem item = item(8, 42, ",\"preface\":\"[b]帖子内容[/b]\"");
        item.context = new TimelineItem.Channel(); item.context.id = 8; item.context.name = "测试频道";
        assertEquals("/work/42", item.path()); assertEquals("/channel/8", item.contextPath());
        assertEquals("频道 测试频道 收录了新作品", item.actionText());
        assertEquals("[b]帖子内容[/b]", item.body());
    }
    @Test public void deletedOrMissingEntitiesDoNotCreateBrokenLinksOrForwardActions() {
        TimelineItem item = item(1, 91, ",\"work_id\":42,\"status_del\":1,\"content\":\"hidden\"");
        assertNull(item.path()); assertFalse(item.canHighlight()); assertFalse(item.body().contains("hidden"));
        item.entity = null;
        assertNull(item.path()); assertFalse(item.canHighlight()); assertFalse(item.body().isEmpty());
        assertEquals("暂不支持此类动态", item(99, 42, "").body());
    }
    @Test public void paginationDeduplicatesMovingPageBoundariesWithoutHidingNewEntityTypes() {
        TimelineFeed feed = new TimelineFeed(); List<TimelineItem> page = new ArrayList<>();
        for (int i = 1; i <= 12; i++) page.add(item(6, i, ""));
        feed.accept(1, page); assertFalse(feed.finished); assertEquals(12, feed.items.size());
        feed.accept(2, Arrays.asList(item(6, 12, ""), item(7, 12, ""), item(7, 12, ""), item(6, 13, "")));
        assertEquals(14, feed.items.size()); assertEquals(2, feed.page); assertTrue(feed.finished);
        feed.accept(1, null); assertTrue(feed.finished); assertTrue(feed.items.isEmpty());
        feed.clear(); assertEquals(0, feed.page); assertFalse(feed.finished);
    }
    @Test public void fullDuplicatePageStillAllowsNextPageAndSpaceBanCannotBeActivated() {
        TimelineFeed feed = new TimelineFeed(); List<TimelineItem> page = new ArrayList<>();
        for (int i = 0; i < 12; i++) page.add(item(6, 1, ""));
        feed.accept(1, page); feed.accept(2, page);
        assertEquals(1, feed.items.size()); assertFalse(feed.finished); assertEquals(2, feed.page);
        UserAuth auth = new UserAuth(); auth.userId = 9; auth.qualifyStatus = 2;
        assertTrue(auth.needsSpace()); assertFalse(auth.canPost());
        auth.spaceStatus = 1; assertTrue(auth.canPost()); assertFalse(auth.needsSpace());
        auth.spaceStatus = 3; assertFalse(auth.canPost()); assertFalse(auth.needsSpace());
        auth.spaceStatus = 0; auth.qualifyStatus = 1; assertFalse(auth.needsSpace());
    }
}
