package com.fimtale.notifications;

import android.app.Application;
import android.view.View;
import android.widget.TextView;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import com.fimtale.NotificationsActivity;
import com.fimtale.R;
import com.fimtale.model.AuthorInfo;
import com.fimtale.utils.UserPreferences;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.google.gson.Gson;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class InboxActivityTest {
    @Test public void guestPageAndConversationRenderWithTheSharedHeaderAndComposer() {
        UserPreferences.saveToken(RuntimeEnvironment.getApplication(), "");
        RuntimeEnvironment.getApplication().getSharedPreferences("fimtale_session", 0).edit().putString("user_id", "1").commit();
        try (ActivityController<NotificationsActivity> controller = Robolectric.buildActivity(NotificationsActivity.class).setup().visible()) {
            NotificationsActivity activity = controller.get();
            MaterialToolbar toolbar = activity.findViewById(R.id.toolbar);
            assertEquals("消息", toolbar.getTitle()); assertNotNull(activity.findViewById(R.id.toolbarContainer));
            assertEquals(5, ((TabLayout)activity.findViewById(R.id.inboxTabs)).getTabCount());
            assertEquals(View.GONE, activity.findViewById(R.id.inboxComposer).getVisibility());
            InboxViewModel model = new ViewModelProvider(activity).get(InboxViewModel.class);
            model.needsLogin = false; model.tab = 3; model.loaded = true;
            Inbox.Conversation conversation = new Inbox.Conversation(); conversation.id = 7;
            conversation.participants = java.util.Arrays.asList(new Gson().fromJson("{\"user_id\":2,\"username\":\"测试用户\"}", AuthorInfo.class));
            conversation.latest_unread_message_id = 1L;
            model.conversations.add(conversation); model.changes.setValue(98);
            RecyclerView list = activity.findViewById(R.id.inboxList);
            layout(list);
            assertEquals(1, list.getAdapter().getItemCount()); // No redundant refresh/summary row.
            assertNotNull(list.findViewById(R.id.conversationName));
            assertTrue(((View)list.findViewById(R.id.conversationName).getParent().getParent().getParent()).isClickable());
            assertEquals(View.VISIBLE, list.findViewById(R.id.conversationUnread).getVisibility());
            ((View) list.findViewById(R.id.conversationName).getParent().getParent().getParent()).performClick();
            android.content.Intent opened = Shadows.shadowOf(activity).getNextStartedActivity();
            assertEquals(com.fimtale.ConversationActivity.class.getName(), opened.getComponent().getClassName());
            assertNull(model.conversation);
            model.conversation = conversation;
            Inbox.Message message = new Inbox.Message(); message.id = 1; message.message = "[b]测试消息[/b]";
            message.user = new Gson().fromJson("{\"user_id\":2,\"username\":\"测试用户\"}", AuthorInfo.class);
            Inbox.Message sent = new Inbox.Message(); sent.id = 2;
            sent.message = "这是一条较长的消息，用来验证气泡会自动换行，同时为右侧头像保留足够空间。".repeat(4);
            sent.user = new Gson().fromJson("{\"user_id\":1,\"username\":\"我\"}", AuthorInfo.class);
            model.messages.add(message); model.messages.add(sent); model.drafts.put(7L, "未发送草稿"); model.changes.setValue(99);
            assertEquals(View.VISIBLE, activity.findViewById(R.id.inboxComposer).getVisibility());
            assertEquals("未发送草稿", ((TextView)activity.findViewById(R.id.inboxInput)).getText().toString());
            layout(list);
            assertEquals(2, list.getAdapter().getItemCount());
            View receivedRow = list.findViewHolderForAdapterPosition(0).itemView;
            View sentRow = list.findViewHolderForAdapterPosition(1).itemView;
            assertEquals(View.VISIBLE, receivedRow.findViewById(R.id.messageAvatarLeft).getVisibility());
            assertEquals(View.GONE, receivedRow.findViewById(R.id.messageAvatarRight).getVisibility());
            assertEquals(View.VISIBLE, sentRow.findViewById(R.id.messageAvatarRight).getVisibility());
            assertEquals(View.GONE, sentRow.findViewById(R.id.messageAvatarLeft).getVisibility());
            TextView bubble = sentRow.findViewById(R.id.messageBubble);
            assertTrue(bubble.getWidth() <= bubble.getMaxWidth());
            assertTrue(bubble.getLineCount() > 1);
            android.graphics.Rect bubbleBounds = new android.graphics.Rect(), avatarBounds = new android.graphics.Rect();
            bubble.getGlobalVisibleRect(bubbleBounds);
            sentRow.findViewById(R.id.messageAvatarRight).getGlobalVisibleRect(avatarBounds);
            assertTrue(bubbleBounds.right <= avatarBounds.left);

        }
    }
    @Test public void conversationHasItsOwnFlatSearchStyleToolbarAndSurvivesRecreation() {
        Application app = RuntimeEnvironment.getApplication();
        UserPreferences.saveToken(app, "");
        app.getSharedPreferences("fimtale_session", 0).edit().putString("user_id", "1").commit();
        Inbox.Conversation conversation = new Inbox.Conversation(); conversation.id = 7;
        conversation.participants = java.util.Arrays.asList(new Gson().fromJson("{\"user_id\":2,\"username\":\"测试用户\"}", AuthorInfo.class));
        try (ActivityController<com.fimtale.ConversationActivity> controller = Robolectric.buildActivity(
                com.fimtale.ConversationActivity.class, com.fimtale.ConversationActivity.intent(app, conversation)).setup().visible()) {
            com.fimtale.ConversationActivity activity = controller.get();
            assertNull(activity.findViewById(R.id.toolbarContainer));
            assertEquals("测试用户", ((TextView)activity.findViewById(R.id.conversationTitle)).getText().toString());
            assertEquals(View.GONE, activity.findViewById(R.id.inboxTabs).getVisibility());
            controller.recreate();
            activity = controller.get();
            assertEquals(7, new ViewModelProvider(activity).get(InboxViewModel.class).conversation.id);
            activity.getOnBackPressedDispatcher().onBackPressed();
            assertTrue(activity.isFinishing());
        }
    }
    private void layout(RecyclerView list) {
        list.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1400, View.MeasureSpec.EXACTLY));
        list.layout(0, 0, 1080, 1400);
    }
}
