package com.fimtale.editor;

import android.content.Context;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import com.fimtale.R;
import com.fimtale.utils.BbCodeRendering;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import io.noties.markwon.Markwon;

/** A snapshot of unsaved source, rendered by the same pipeline as articles and chapters. */
public final class EditorPreviewDialog {
    private EditorPreviewDialog() {}
    public static AlertDialog show(Context context, String source, float textSizeSp) {
        TextView content = new TextView(context);
        content.setId(R.id.editorPreviewContent);
        int padding = Math.round(20 * context.getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        content.setTextSize(textSizeSp);
        ScrollView scroll = new ScrollView(context); scroll.addView(content);
        Markwon renderer = BbCodeRendering.create(context);
        AlertDialog dialog = new MaterialAlertDialogBuilder(context).setTitle(R.string.editor_preview)
                .setView(scroll).setPositiveButton(R.string.editor_return_to_editing, null).create();
        dialog.setOnShowListener(ignored -> content.post(() -> {
            if (dialog.isShowing()) BbCodeRendering.setText(renderer, content, source);
        }));
        dialog.show();
        return dialog;
    }
}
