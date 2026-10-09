package com.fimtale.report;

import com.fimtale.R;
import com.fimtale.utils.AppStrings;

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
        return status == PENDING ? AppStrings.get(R.string.report_status_pending) : status == RESOLVED ? AppStrings.get(R.string.report_status_resolved) : status == REJECTED ? AppStrings.get(R.string.report_status_rejected) : AppStrings.get(R.string.common_unknown);
    }
    public String kindLabel() {
        String kind = payload == null ? "" : payload.kind;
        return "report".equals(kind) ? AppStrings.get(R.string.report_kind_content) : "recovery_request".equals(kind) ? AppStrings.get(R.string.report_kind_recovery) : "system_feedback".equals(kind) ? AppStrings.get(R.string.report_kind_feedback) : AppStrings.get(R.string.report_label);
    }
    public String targetLabel() {
        if (target_type == 0 || target_id <= 0) return AppStrings.get(R.string.report_target_site);
        String name;
        switch (target_type) {
            case 1: name = AppStrings.get(R.string.work_label); break;
            case 3: name = AppStrings.get(R.string.work_chapter_label); break;
            case 4: name = AppStrings.get(R.string.comments_title); break;
            case 5: name = AppStrings.get(R.string.report_target_channel); break;
            case 6: name = AppStrings.get(R.string.report_target_channel_comment); break;
            case 7: name = AppStrings.get(R.string.profile_user_label); break;
            default: name = AppStrings.get(R.string.report_target_entity);
        }
        return AppStrings.get(R.string.common_entity_number, name, target_id);
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
