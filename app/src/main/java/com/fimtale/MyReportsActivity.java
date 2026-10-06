package com.fimtale;

import android.content.Intent;
import android.os.Bundle;

/** The report list retains its filters, page and scroll position while details are open. */
public class MyReportsActivity extends ReportsActivity {
    @Override protected void onCreate(Bundle state) {
        long legacyReport = getIntent().getLongExtra(EXTRA_REPORT_ID, 0);
        getIntent().removeExtra(EXTRA_REPORT_ID);
        super.onCreate(state);
        if (legacyReport > 0 && state == null) startActivity(new Intent(this, ReportDetailActivity.class)
                .putExtra(EXTRA_REPORT_ID, legacyReport));
    }
}
