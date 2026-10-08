package com.fimtale.ui;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.core.view.ViewCompat;
import com.fimtale.R;
import com.fimtale.utils.BbCodeRendering;
import com.fimtale.utils.SpoilerSpan;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import io.noties.markwon.Markwon;

/** A scrollable bottom sheet for one concealed BBCode block. */
public final class CollapseSheet {
    private CollapseSheet() {}

    public static BottomSheetDialog show(TextView owner, String title, String source) {
        Context context = owner.getContext();
        Context host = context;
        while (host instanceof ContextWrapper && !(host instanceof Activity)) host = ((ContextWrapper) host).getBaseContext();
        if (host instanceof Activity && (((Activity) host).isFinishing() || ((Activity) host).isDestroyed())) return null;
        BottomSheetDialog sheet = new BottomSheetDialog(context);
        sheet.setContentView(R.layout.dialog_collapsed_content);
        View root = sheet.findViewById(R.id.collapseSheetRoot);
        TextView heading = sheet.findViewById(R.id.collapseSheetTitle);
        TextView content = sheet.findViewById(R.id.collapseSheetContent);
        heading.setText(title);
        content.setTextSize(TypedValue.COMPLEX_UNIT_PX, owner.getTextSize());
        content.setLineSpacing(owner.getLineSpacingExtra(), owner.getLineSpacingMultiplier());
        ViewCompat.setAccessibilityPaneTitle(root, title);
        View surface = sheet.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        surface.setBackgroundResource(R.drawable.bg_bottom_sheet_rounded);
        int windowHeight = host instanceof Activity ? ((Activity) host).getWindow().getDecorView().getHeight()
                : owner.getRootView().getHeight();
        if (windowHeight <= 0) windowHeight = context.getResources().getDisplayMetrics().heightPixels;
        ViewGroup.LayoutParams params = surface.getLayoutParams();
        params.height = Math.max(1, Math.round(windowHeight * .78f));
        surface.setLayoutParams(params);
        sheet.getBehavior().setSkipCollapsed(true);
        Markwon renderer = BbCodeRendering.create(sheet.getContext());
        sheet.setOnShowListener(ignored -> {
            sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
            content.post(() -> {
                if (sheet.isShowing()) BbCodeRendering.setText(renderer, content, source);
            });
        });
        sheet.show();
        SpoilerSpan.observe(sheet.getWindow());
        return sheet;
    }
}
