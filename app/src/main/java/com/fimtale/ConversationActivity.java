package com.fimtale;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import com.fimtale.notifications.Inbox;
import com.fimtale.utils.UserPreferences;
import com.google.gson.Gson;

/** One conversation, with the same flat toolbar as the search page. */
public class ConversationActivity extends InboxActivity {
    private static final String EXTRA_CONVERSATION = "conversation", EXTRA_OWNER = "conversation_owner";
    private Inbox.Conversation conversation;
    public static Intent intent(Context context, Inbox.Conversation conversation) {
        // Transfer only the identity needed by the header; message history is fetched by this page.
        Inbox.Conversation identity = new Inbox.Conversation();
        identity.id = conversation.id; identity.participants = conversation.participants;
        return new Intent(context, ConversationActivity.class)
                .putExtra(EXTRA_CONVERSATION, new Gson().toJson(identity))
                .putExtra(EXTRA_OWNER, UserPreferences.getUserId(context));
    }
    @Override protected int pageLayout() { return R.layout.activity_conversation; }
    @Override protected boolean standaloneConversation() { return true; }
    @Override protected Inbox.Conversation initialConversation() { return conversation; }
    @Override protected void onCreate(Bundle state) {
        try { conversation = new Gson().fromJson(getIntent().getStringExtra(EXTRA_CONVERSATION), Inbox.Conversation.class); }
        catch (RuntimeException ignored) { conversation = null; }
        super.onCreate(state);
        if (conversation == null || conversation.id <= 0) finish();
    }
    @Override protected void onResume() {
        String owner = getIntent().getStringExtra(EXTRA_OWNER);
        if (conversation == null || owner == null || !owner.equals(UserPreferences.getUserId(this))) {
            // Do not reopen another account's conversation after switching sessions.
            finish();
            super.onResume();
            return;
        }
        super.onResume();
    }
}
