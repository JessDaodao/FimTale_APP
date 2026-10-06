package com.fimtale.report;

import java.util.ArrayList;
import java.util.List;

/** ReportPanel.vue and the report API's response models. */
public final class MyReport {
    public static final int PENDING = 1, RESOLVED = 2, REJECTED = 3;
    public long id, source_user_id, target_id;
    public int status, target_type;
    public String created_at, updated_at, source_username;
    public Payload payload;
    public static final class Payload {
        public String kind, content_snapshot;
        public List<Message> messages;
        public DebugContext debug_context;
    }
    public static final class Message { public long user_id; public String username, content, created_at; }
    public static final class DebugContext { public List<Failure> failures; }
    public static final class Failure { public Long ts; public int status; public String path, api_path, rid, trace_id; }
    public static final class Page { public List<MyReport> items; public long total; }
    public static final class Reply {
        public final long report_id; public final String content;
        public Reply(long id, String content) { report_id = id; this.content = ReportRequest.trim(content); }
    }
    public String statusLabel() {
        return status == PENDING ? "待处理" : status == RESOLVED ? "已处理" : status == REJECTED ? "已驳回" : "未知";
    }
    public String kindLabel() {
        String kind = payload == null ? "" : payload.kind;
        return "report".equals(kind) ? "内容报告" : "recovery_request".equals(kind) ? "恢复请求" : "system_feedback".equals(kind) ? "反馈" : "报告";
    }
    public String targetLabel() {
        if (target_type == 0 || target_id <= 0) return "站点";
        String name;
        switch (target_type) {
            case 1: name = "作品"; break;
            case 3: name = "章节"; break;
            case 4: name = "评论"; break;
            case 5: name = "频道"; break;
            case 6: name = "频道评论"; break;
            case 7: name = "用户"; break;
            default: name = "实体";
        }
        return name + " #" + target_id;
    }
    public String targetPath() {
        if (target_id <= 0) return null;
        return target_type == 1 ? "/work/" + target_id : target_type == 5 ? "/channel/" + target_id : target_type == 7 ? "/user/" + target_id : null;
    }
    public List<Message> messages() { return payload == null || payload.messages == null ? new ArrayList<>() : payload.messages; }
    public List<Failure> failures() {
        return payload == null || payload.debug_context == null || payload.debug_context.failures == null
                ? new ArrayList<>() : payload.debug_context.failures;
    }
}
