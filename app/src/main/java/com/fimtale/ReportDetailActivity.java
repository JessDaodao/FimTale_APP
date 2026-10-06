package com.fimtale;

import android.os.Bundle;

/** A report loaded by ID, with its own reply draft and floating settings-style header. */
public class ReportDetailActivity extends ReportsActivity {
    @Override protected boolean isDetailPage() { return true; }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (getIntent().getLongExtra(EXTRA_REPORT_ID, 0) <= 0) finish();
    }
}
