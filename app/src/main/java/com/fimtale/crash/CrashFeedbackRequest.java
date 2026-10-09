package com.fimtale.crash;

import com.fimtale.R;
import com.fimtale.utils.AppStrings;

import com.google.gson.annotations.SerializedName;
import java.util.Collections;
import java.util.List;

/** system_feedback and ReportDebugContext from ft-front/schema/openapi.json. */
public final class CrashFeedbackRequest {
    public final String kind = "system_feedback";
    public final String content;
    @SerializedName("debug_context") public final DebugContext debugContext;
    public CrashFeedbackRequest(CrashReport report, String description) {
        String notes = description.trim();
        content = escape(report.summary()) + (notes.isEmpty() ? "" : AppStrings.get(R.string.crash_feedback_notes, escape(notes)));
        debugContext = new DebugContext(report);
    }
    private static String escape(String value) { return value.replace("&", "&amp;").replace("[", "&#91;").replace("]", "&#93;"); }
    public static final class DebugContext {
        public final List<ClientException> exceptions;
        DebugContext(CrashReport report) { exceptions = Collections.singletonList(new ClientException(report)); }
    }
    public static final class ClientException {
        public final long ts;
        public final String path, kind, message, stack, release;
        ClientException(CrashReport report) {
            ts = report.timestamp; path = "android/" + report.screen; kind = report.kind;
            message = report.kind; stack = report.stack; release = "android/" + report.environment.appVersion;
        }
    }
}
