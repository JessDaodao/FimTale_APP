package com.fimtale.notifications;

import android.app.Application;
import com.fimtale.network.FimTaleApiService;
import com.fimtale.utils.UserPreferences;
import com.google.gson.Gson;
import java.lang.reflect.Proxy;
import java.util.*;
import okhttp3.Request;
import okio.Timeout;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import retrofit2.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class InboxTest {
    private final List<Pending<?>> pending = new ArrayList<>();
    private InboxViewModel model;
    private Application app;
    @Before public void setup() {
        app = RuntimeEnvironment.getApplication(); UserPreferences.saveToken(app, "first");
        FimTaleApiService api = (FimTaleApiService) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{FimTaleApiService.class}, (proxy, method, args) -> {
            Pending<Object> call = new Pending<>(method.getName(), args); pending.add(call); return call;
        });
        model = new InboxViewModel(app, api); model.connect();
    }
    @After public void cleanup() { model.onCleared(); }
    @SuppressWarnings("unchecked") private <T> Pending<T> last(String method) {
        for (int i = pending.size() - 1; i >= 0; i--) if (pending.get(i).method.equals(method)) return (Pending<T>) pending.get(i);
        throw new AssertionError(method);
    }
    private Inbox.Notice notice(long id, int type, long entity) {
        Inbox.Notice n = new Inbox.Notice(); n.id = id; n.type = type; n.entity_id = entity; return n;
    }
    @Test public void groupsOnlyAdjacentEventsAndFollowsAcrossEntities() {
        List<List<Inbox.Notice>> groups = Inbox.groups(Arrays.asList(notice(1,4,9), notice(2,4,9), notice(3,3,9), notice(4,4,9), notice(5,18,11), notice(6,18,12)));
        assertEquals(4, groups.size()); assertEquals(2, groups.get(0).size()); assertEquals(2, groups.get(3).size());
    }
    @Test public void linksRetainCommentAnchorsAndReviewDestinations() {
        Gson gson = new Gson();
        Inbox.Notice n = gson.fromJson("{\"type\":3,\"entity\":{\"id\":5,\"work_id\":7,\"chapter_id\":8}}", Inbox.Notice.class);
        assertEquals("/work/7/chapter/8#comment-5", n.path());
        n = gson.fromJson("{\"type\":21,\"entity\":{\"id\":5,\"status\":1}}", Inbox.Notice.class);
        assertEquals("/admin/review?review_id=5", n.path());
        n = gson.fromJson("{\"type\":23,\"entity\":\"# 系统通知\\n正文\"}", Inbox.Notice.class);
        assertEquals("# 系统通知\n正文", n.preview()); assertNull(n.path());
    }
    @Test public void staleTabResponseCannotOverwriteCurrentPage() {
        Pending<List<Inbox.Notice>> old = last("getNotifications");
        model.select(1); assertTrue(old.canceled);
        old.reply(Arrays.asList(notice(1,1,2)));
        assertTrue(model.notices.isEmpty());
        this.<List<Inbox.Notice>>last("getNotifications").reply(Arrays.asList(notice(2,2,3)));
        assertEquals(2, model.notices.get(0).id); assertEquals(1, model.tab);
    }
    @Test public void accountSwitchDiscardsOldPrivateDataAndDrafts() {
        Pending<List<Inbox.Notice>> old = last("getNotifications"); model.drafts.put(5L, "private");
        UserPreferences.saveToken(app, "second"); model.connect(); old.reply(Arrays.asList(notice(1,1,2)));
        assertTrue(model.notices.isEmpty()); assertTrue(model.drafts.isEmpty());
        assertEquals("second", last("getNotifications").args[0]);
    }
    @Test public void failedReadDoesNotClearUnreadAndSuccessfulReadUsesOnlyLoadedIds() {
        Inbox.Notice n = notice(1,1,2); this.<List<Inbox.Notice>>last("getNotifications").reply(Arrays.asList(n));
        model.markRead(model.notices); assertTrue(model.mutating);
        this.<Void>last("readNotifications").fail(); assertFalse(n.is_read); assertFalse(model.mutating);
        model.markRead(model.notices);
        Inbox.Read body = (Inbox.Read) last("readNotifications").args[1]; assertEquals(Arrays.asList(1L), body.notif_ids); assertNull(body.read_through_message_ids);
        this.<Void>last("readNotifications").reply(null); assertTrue(n.is_read);
    }
    @Test public void messageReadCursorCannotAdvancePastFetchedMessages() {
        Inbox.Conversation conversation = new Inbox.Conversation(); conversation.id = 9; conversation.latest_unread_message_id = 100L;
        model.open(conversation);
        Inbox.Message m = new Inbox.Message(); m.id = 80;
        this.<List<Inbox.Message>>last("getMessages").reply(Arrays.asList(m));
        Inbox.Read read = (Inbox.Read)last("readNotifications").args[1];
        assertEquals(Arrays.asList(9L), read.notif_ids); assertEquals(Arrays.asList(80L), read.read_through_message_ids);
        this.<Void>last("readNotifications").reply(null);
        assertEquals(Long.valueOf(100), conversation.latest_unread_message_id);
    }
    @Test public void paginationDeduplicatesAndRetriesTheFailedPage() {
        List<Inbox.Notice> first = new ArrayList<>(); for (int i=1;i<=20;i++) first.add(notice(i,1,i));
        this.<List<Inbox.Notice>>last("getNotifications").reply(first); assertTrue(model.more);
        model.load(true); this.<List<Inbox.Notice>>last("getNotifications").fail(); assertEquals(1, model.page);
        model.load(true); assertEquals(2, last("getNotifications").args[2]);
        this.<List<Inbox.Notice>>last("getNotifications").reply(Arrays.asList(notice(20,1,20),notice(21,1,21)));
        assertEquals(21, model.notices.size()); assertFalse(model.more);
    }
    @Test public void sendPreservesDraftOnFailureAndClearsOnlyAfterSuccess() {
        Inbox.Conversation c = new Inbox.Conversation(); c.id = 9; model.open(c);
        this.<List<Inbox.Message>>last("getMessages").reply(Collections.emptyList());
        model.drafts.put(9L, "hello"); model.send("hello"); this.<Void>last("sendMessage").fail();
        assertEquals("hello", model.drafts.get(9L));
        model.send("hello"); Inbox.Send sent = (Inbox.Send)last("sendMessage").args[1]; assertEquals(9, sent.conversation_id);
        this.<Void>last("sendMessage").reply(null); assertFalse(model.drafts.containsKey(9L));
    }
    @Test public void pollingCatchesUpAcrossMultiplePagesWithoutGaps() {
        Inbox.Conversation c = new Inbox.Conversation(); c.id=9; model.open(c);
        Inbox.Message initial = new Inbox.Message(); initial.id=1;
        this.<List<Inbox.Message>>last("getMessages").reply(Arrays.asList(initial));
        model.poll(true);
        List<Inbox.Message> newest = new ArrayList<>(); for (int i=12;i<=21;i++) { Inbox.Message m=new Inbox.Message();m.id=i;newest.add(m); }
        this.<List<Inbox.Message>>last("getMessages").reply(newest);
        assertEquals(2, last("getMessages").args[2]);
        List<Inbox.Message> middle = new ArrayList<>(); for (int i=2;i<=11;i++) { Inbox.Message m=new Inbox.Message();m.id=i;middle.add(m); }
        this.<List<Inbox.Message>>last("getMessages").reply(middle);
        this.<List<Inbox.Message>>last("getMessages").reply(Arrays.asList(initial));
        assertEquals(21, model.messages.size()); for(int i=0;i<21;i++) assertEquals(i+1,model.messages.get(i).id);
    }
    private static final class Pending<T> implements Call<T> {
        final String method; final Object[] args; Callback<T> callback; boolean canceled;
        Pending(String method, Object[] args) { this.method=method;this.args=args; }
        void reply(T data) { callback.onResponse(this, Response.success(data)); }
        void fail() { callback.onFailure(this, new java.io.IOException("fixture")); }
        @Override public void enqueue(Callback<T> value) { callback=value; }
        @Override public Response<T> execute() { throw new UnsupportedOperationException(); }
        @Override public boolean isExecuted() { return callback != null; }
        @Override public void cancel() { canceled=true; }
        @Override public boolean isCanceled() { return canceled; }
        @Override public Call<T> clone() { return new Pending<>(method,args); }
        @Override public Request request() { return new Request.Builder().url("https://example.invalid/").build(); }
        @Override public Timeout timeout() { return Timeout.NONE; }
    }
}
