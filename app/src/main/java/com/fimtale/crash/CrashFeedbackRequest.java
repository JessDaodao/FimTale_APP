package com.fimtale.crash;

import com.fimtale.R;
import com.fimtale.utils.AppStrings;

/** System feedback with the previewed diagnostics included directly in the content. */
public final class CrashFeedbackRequest {
    public final String kind = "system_feedback";
    public final String content;
    public CrashFeedbackRequest(CrashReport report, String description) {
        String notes = description.trim();
        content = escape(report.diagnostics()) + (notes.isEmpty() ? "" : AppStrings.get(R.string.crash_feedback_notes, escape(notes)));
    }
    private static String escape(String value) { return value.replace("&", "&amp;").replace("[", "&#91;").replace("]", "&#93;"); }
}
