package com.fimtale.notifications;

import com.fimtale.model.AuthorInfo;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.*;

/** Wire models shared with ft-front's notification and conversation pages. */
public final class Inbox {
    private Inbox() {}
    public static final String[] KEYS = {"reply", "mention", "interaction", "message", "system"};
    public static final String[] TITLES = {"回复", "提到", "互动", "私信", "系统"};
    public static class Notice {
        public long id, entity_id;
        public int type;
        public boolean is_read;
        public String created_at;
        public AuthorInfo sender;
        public JsonElement entity;
        public String acted;
        public JsonObject object() { return entity != null && entity.isJsonObject() ? entity.getAsJsonObject() : new JsonObject(); }
        public String preview() {
            if (type == 23) return entity != null && entity.isJsonPrimitive() ? entity.getAsString() : "";
            if (type == 21) return string(child(object(), "payload"), "state_reason");
            return string(object(), "content");
        }
        public String action() {
            String[] actions = {"发来了通知", "评论了", "在评论中提到了你", "回复了你的评论", "赞了你的评论", "给你的评论点了SH",
                "发布了作品", "发布了章节", "在频道中添加了作品", "在作品中提到了你", "在章节中提到了你", "赞了你的作品", "给你的作品HP",
                "赞了频道", "评论了频道", "在频道评论中提到了你", "赞了你的频道评论", "推荐了你的作品", "关注了你", "发来了私信",
                "邀请你成为频道协作者", "发送了审核通知", "发送了报告", "发布了系统通知", "希望将你的作品设为其前作"};
            return actions[type > 0 && type < actions.length ? type : 0];
        }
        public String label() {
            JsonObject e = object();
            for (String key : new String[]{"context_title", "title", "name", "work_title"}) if (!string(e, key).isEmpty()) return string(e, key);
            return type == 21 ? "查看审核" : type == 22 ? "查看报告" : "查看详情";
        }
        public String action(String self) {
            if (type != 22) return action();
            if (!String.valueOf(number(object(), "source_user_id")).equals(self)) return "提交/更新了报告";
            long status = number(object(), "status");
            return status == 3 ? "驳回了你的报告" : status == 2 ? "处理了你的报告" : "回复了你的报告";
        }
        public String path(String self, boolean manager) {
            if (type == 22 && manager && !String.valueOf(number(object(), "source_user_id")).equals(self)
                    && number(object(), "id") > 0) return "/admin/report?report_id=" + number(object(), "id");
            return path();
        }
        public String path() {
            JsonObject e = object(); long id = number(e, "id");
            if (id <= 0 || number(e, "status_del") != 0) return null;
            if (type >= 1 && type <= 5) {
                long work = number(e, "work_id"), chapter = number(e, "chapter_id");
                return work > 0 ? "/work/" + work + (chapter > 0 ? "/chapter/" + chapter : "") + "#comment-" + id : null;
            }
            if (type == 6 || type == 8 || type == 9 || type == 11 || type == 12 || type == 24) return "/work/" + id;
            if (type == 7 || type == 10) return number(e, "work_id") > 0 ? "/work/" + number(e, "work_id") + "/chapter/" + id : null;
            if (type == 13 || type == 20) return "/channel/" + id;
            if (type >= 14 && type <= 16) return number(e, "channel_id") > 0 ? "/channel/" + number(e, "channel_id") + "#comment-" + id : null;
            if (type == 17) return number(child(e, "work"), "id") > 0 ? "/work/" + number(child(e, "work"), "id") : null;
            if (type == 21) return (number(e, "status") == 1 ? "/admin/review?review_id=" : "/user/reviews?review_id=") + id;
            if (type == 22) return "/user/reports?report_id=" + id;
            return null;
        }
    }
    public static class Message {
        public long id, conversation_id;
        public String message, created_at;
        public AuthorInfo user;
    }
    public static class Conversation {
        public long id;
        public List<AuthorInfo> participants;
        public Message latest_message;
        public Long latest_unread_message_id;
        public String title(String self) {
            List<String> names = new ArrayList<>();
            if (participants != null) for (AuthorInfo user : participants)
                if (user != null && !String.valueOf(user.getId()).equals(self)) names.add(user.getUserName());
            return names.isEmpty() ? "私信" : String.join("、", names);
        }
    }
    public static class Read {
        public final List<Long> notif_ids;
        public final List<Long> read_through_message_ids;
        public Read(List<Long> ids) { notif_ids = ids; read_through_message_ids = null; }
        public Read(long conversation, long through) { notif_ids = Collections.singletonList(conversation); read_through_message_ids = Collections.singletonList(through); }
    }
    public static class Send {
        public final long conversation_id; public final String message;
        public Send(long id, String body) { conversation_id = id; message = body; }
    }
    public static class Invite {
        public final long sequel_id; public final String action;
        public Invite(long id, String value) { sequel_id = id; action = value; }
    }
    public static List<List<Notice>> groups(List<Notice> notices) {
        List<List<Notice>> groups = new ArrayList<>();
        for (Notice n : notices) {
            List<Notice> last = groups.isEmpty() ? null : groups.get(groups.size() - 1);
            if (last == null || last.get(0).type != n.type || (n.type != 18 && last.get(0).entity_id != n.entity_id)) {
                last = new ArrayList<>(); groups.add(last);
            }
            last.add(n);
        }
        return groups;
    }
    public static List<Long> unread(List<Notice> notices) {
        List<Long> ids = new ArrayList<>(); for (Notice n : notices) if (!n.is_read) ids.add(n.id); return ids;
    }
    public static JsonObject child(JsonObject object, String key) { JsonElement e = object.get(key); return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject(); }
    public static String string(JsonObject object, String key) { JsonElement e = object.get(key); return e != null && e.isJsonPrimitive() ? e.getAsString() : ""; }
    public static long number(JsonObject object, String key) { try { return Long.parseLong(string(object, key)); } catch (NumberFormatException e) { return 0; } }
}
