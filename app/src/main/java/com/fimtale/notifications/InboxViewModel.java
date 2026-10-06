package com.fimtale.notifications;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import com.fimtale.network.*;
import com.fimtale.utils.UserPreferences;
import java.util.*;
import java.util.function.Consumer;
import retrofit2.*;

/** Retains loaded pages and drafts across recreation; callbacks are scoped to account and view. */
public class InboxViewModel extends AndroidViewModel {
    public final MutableLiveData<Integer> changes = new MutableLiveData<>(0);
    public final List<Inbox.Notice> notices = new ArrayList<>();
    public final List<Inbox.Conversation> conversations = new ArrayList<>();
    public final List<Inbox.Message> messages = new ArrayList<>();
    public final Map<String, Integer> counts = new HashMap<>();
    public final Map<Long, String> drafts = new HashMap<>();
    public Inbox.Conversation conversation;
    public int tab, page;
    public boolean manager;
    public boolean loading, mutating, more, loaded, needsLogin;
    public String error = "", notice;
    public int scrollToBottom;
    private String session = "";
    private int generation;
    private boolean counting, retryNext;
    private final List<Call<?>> calls = new ArrayList<>();
    private final FimTaleApiService api;
    public InboxViewModel(@NonNull Application app) { this(app, RetrofitClient.getInstance()); }
    InboxViewModel(Application app, FimTaleApiService api) { super(app); this.api = api; }
    private void changed() { changes.setValue(changes.getValue() + 1); }
    public void connect() { connect(null); }
    public void connect(Inbox.Conversation initial) {
        String token = UserPreferences.getToken(getApplication());
        if (!session.equals(token)) {
            cancel(); session = token; notices.clear(); conversations.clear(); messages.clear(); counts.clear(); drafts.clear();
            conversation = initial; if (initial != null) tab = 3; loaded = false; page = 0; more = false; error = ""; notice = null; manager = false;
            if (!token.isEmpty()) request(api.getUserAuth(token), auth -> { manager = auth != null && auth.canManageReviews(); }, false);
        }
        if (initial != null && conversation == null && !loaded) { conversation = initial; tab = 3; }
        needsLogin = token.isEmpty();
        if (needsLogin) { changed(); return; }
        if (!loaded && !loading) load(false);
        refreshCounts();
    }
    public void select(int position) {
        if (mutating || position == tab && conversation == null) return;
        cancel(); tab = position; conversation = null; notices.clear(); conversations.clear(); messages.clear(); loaded = false; page = 0; more = false;
        load(false);
    }
    public void open(Inbox.Conversation selected) {
        if (mutating) return;
        cancel(); conversation = selected; messages.clear(); page = 0; more = false; loaded = false; load(false);
    }
    public void back() {
        if (mutating) return;
        cancel(); conversation = null; messages.clear(); page = 0; more = false; loaded = false; load(false);
    }
    public void load(boolean next) {
        if (loading || mutating || needsLogin) return;
        if (!session.equals(UserPreferences.getToken(getApplication()))) { connect(); return; }
        retryNext = next;
        final int requestedPage = next ? page + 1 : 1;
        loading = true; error = ""; changed();
        if (conversation != null) {
            final Inbox.Conversation selected = conversation;
            request(api.getMessages(session, selected.id, requestedPage, 10), data -> {
                List<Inbox.Message> incoming = data == null ? Collections.emptyList() : data;
                if (!next) messages.clear();
                Set<Long> ids = new HashSet<>(); for (Inbox.Message m : messages) ids.add(m.id);
                List<Inbox.Message> added = new ArrayList<>(); for (Inbox.Message m : incoming) if (m != null && ids.add(m.id)) added.add(m);
                if (next) messages.addAll(0, added); else messages.addAll(added);
                messages.sort(Comparator.comparingLong(m -> m.id));
                complete(requestedPage, incoming.size() == 10);
                if (!next) scrollToBottom++;
                if (!next) acknowledge(selected, incoming);
            }, true);
        } else if (tab == 3) {
            request(api.getConversations(session, requestedPage, 10), data -> {
                List<Inbox.Conversation> incoming = data == null ? Collections.emptyList() : data;
                if (!next) conversations.clear();
                Set<Long> ids = new HashSet<>(); for (Inbox.Conversation c : conversations) ids.add(c.id);
                for (Inbox.Conversation c : incoming) if (c != null && ids.add(c.id)) conversations.add(c);
                complete(requestedPage, incoming.size() == 10);
            }, true);
        } else request(api.getNotifications(session, Inbox.KEYS[tab], requestedPage, 20), data -> {
            List<Inbox.Notice> incoming = data == null ? Collections.emptyList() : data;
            if (!next) notices.clear();
            Set<Long> ids = new HashSet<>(); for (Inbox.Notice n : notices) ids.add(n.id);
            for (Inbox.Notice n : incoming) if (n != null && ids.add(n.id)) notices.add(n);
            complete(requestedPage, incoming.size() == 20);
        }, true);
    }
    private void acknowledge(Inbox.Conversation selected, List<Inbox.Message> observed) {
        long through = 0; for (Inbox.Message m : observed) if (m != null) through = Math.max(through, m.id);
        if (through <= 0) return;
        final long cursor = through;
        request(api.readNotifications(session, new Inbox.Read(selected.id, cursor)), ignored -> {
            if (selected.latest_unread_message_id != null && selected.latest_unread_message_id <= cursor) selected.latest_unread_message_id = null;
            refreshCounts();
        }, false);
    }
    /** Poll only while the latest messages are visible; catch up across page boundaries without holes. */
    public void poll(boolean followingLatest) {
        refreshCounts();
        if (!followingLatest || conversation == null || loading || mutating || needsLogin) return;
        loading = true;
        catchUp(conversation, 1, new ArrayList<>());
    }
    private void catchUp(Inbox.Conversation selected, int requestedPage, List<Inbox.Message> fetched) {
        request(api.getMessages(session, selected.id, requestedPage, 10), data -> {
            List<Inbox.Message> incoming = data == null ? Collections.emptyList() : data;
            fetched.addAll(incoming);
            Set<Long> shown = new HashSet<>(); for (Inbox.Message m : messages) shown.add(m.id);
            boolean overlap = messages.isEmpty() || incoming.size() < 10;
            for (Inbox.Message m : incoming) if (shown.contains(m.id)) overlap = true;
            if (!overlap && requestedPage < 5) { catchUp(selected, requestedPage + 1, fetched); return; }
            boolean added = false;
            if (!overlap) { messages.clear(); shown.clear(); more = true; }
            for (Inbox.Message m : fetched) if (shown.add(m.id)) { messages.add(m); added = true; }
            messages.sort(Comparator.comparingLong(m -> m.id));
            page = Math.max(1, messages.size() / 10);
            loading = false;
            if (added) scrollToBottom++;
            acknowledge(selected, fetched);
        }, true);
    }
    public void retry() { load(retryNext); }
    private void complete(int value, boolean hasMore) { page = value; more = hasMore; loaded = true; loading = false; refreshCounts(); }
    public void refreshCounts() {
        if (needsLogin || session.isEmpty() || counting) return;
        counting = true;
        request(api.getNotificationCount(session), data -> { counting = false; counts.clear(); if (data != null) counts.putAll(data); }, false);
    }
    public void markRead(List<Inbox.Notice> group) {
        if (mutating || loading || needsLogin) return;
        List<Long> ids = Inbox.unread(group); if (ids.isEmpty()) return;
        retryNext = false; mutating = true; changed();
        request(api.readNotifications(session, new Inbox.Read(ids)), ignored -> {
            for (Inbox.Notice n : notices) if (ids.contains(n.id)) n.is_read = true;
            mutating = false; refreshCounts();
        }, true);
    }
    public void send(String text) {
        if (conversation == null || mutating || loading || text.trim().isEmpty() || needsLogin) return;
        long id = conversation.id;
        retryNext = false; mutating = true; changed();
        request(api.sendMessage(session, new Inbox.Send(id, text)), ignored -> {
            drafts.remove(id); mutating = false; notice = "私信已发送"; load(false);
        }, true);
    }
    public void invite(Inbox.Notice n, String action) {
        if (mutating || loading || needsLogin || n.acted != null) return;
        retryNext = false; mutating = true; changed();
        request(api.actOnPrequelInvite(session, new Inbox.Invite(Inbox.number(n.object(), "id"), action)), ignored -> {
            n.acted = action; mutating = false;
        }, true);
    }
    private <T> void request(Call<T> call, Consumer<T> success, boolean primary) {
        int epoch = generation; String token = session; calls.add(call);
        call.enqueue(new Callback<T>() {
            private boolean current() {
                calls.remove(call);
                if (epoch != generation) return false;
                if (!token.equals(UserPreferences.getToken(getApplication()))) { connect(); return false; }
                return true;
            }
            @Override public void onResponse(Call<T> ignored, Response<T> response) {
                if (!current()) return;
                if (response.code() == 401) { UserPreferences.clearSession(getApplication()); connect(); return; }
                if (response.isSuccessful()) success.accept(response.body());
                else failed(ApiErrors.message(response));
                changed();
            }
            @Override public void onFailure(Call<T> ignored, Throwable t) {
                if (!current()) return;
                failed(mutating ? "操作结果未确认，请刷新消息核对后再重试" : "消息加载失败，请重试"); changed();
            }
            private void failed(String message) {
                if (primary) { loading = false; mutating = false; error = message; }
                else { counting = false; notice = message; }
            }
        });
    }
    private void cancel() {
        generation++; for (Call<?> call : calls) call.cancel(); calls.clear();
        loading = false; mutating = false; counting = false;
    }
    @Override protected void onCleared() { cancel(); }
}
