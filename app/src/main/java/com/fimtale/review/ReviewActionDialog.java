package com.fimtale.review;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import com.fimtale.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Native author submission confirmation. The retained model owns the single in-flight request. */
public class ReviewActionDialog extends DialogFragment {
    public static final String TAG = "review_action";
    private ReviewQueueViewModel model;
    private View content;

    @NonNull @Override public Dialog onCreateDialog(Bundle state) {
        model = new ViewModelProvider(requireActivity()).get(ReviewQueueViewModel.class);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        content = LayoutInflater.from(builder.getContext()).inflate(R.layout.dialog_review_action, null);
        AlertDialog dialog = builder.setTitle(R.string.review_confirm_submit).setView(content)
                .setNegativeButton(R.string.review_cancel, (d, w) -> model.closeDialog())
                .setPositiveButton(R.string.review_submit, null).create();
        dialog.setCanceledOnTouchOutside(false);
        model.changes.observe(this, ignored -> render());
        return dialog;
    }
    @Override public void onStart() {
        super.onStart();
        ((AlertDialog) requireDialog()).getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> model.confirm());
        render();
    }
    private void render() {
        if (content == null || getDialog() == null) return;
        if (model.selected == null) { dismissAllowingStateLoss(); return; }
        String action = getString(model.selected.status == ReviewEntry.UNSUBMITTED ? R.string.review_submit : R.string.review_resubmit);
        ((TextView) content.findViewById(R.id.reviewActionTitle)).setText(getString(R.string.review_confirm_submit_message, action, model.selected.title()));
        content.findViewById(R.id.reviewActionProgress).setVisibility(model.mutating ? View.VISIBLE : View.GONE);
        TextView error = content.findViewById(R.id.reviewActionError);
        error.setText(model.dialogError); error.setVisibility(model.dialogError.isEmpty() ? View.GONE : View.VISIBLE);
        AlertDialog dialog = (AlertDialog) requireDialog();
        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(action);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(model.canConfirm());
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(!model.mutating);
        }
        setCancelable(!model.mutating);
    }
    @Override public void onCancel(@NonNull DialogInterface dialog) { model.closeDialog(); super.onCancel(dialog); }
    @Override public void onDestroyView() { content = null; super.onDestroyView(); }
}
