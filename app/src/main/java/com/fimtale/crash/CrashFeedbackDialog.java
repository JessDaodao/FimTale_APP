package com.fimtale.crash;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import com.fimtale.LoginActivity;
import com.fimtale.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

public final class CrashFeedbackDialog extends DialogFragment {
    public static final String TAG = "crash_feedback";
    private CrashFeedbackViewModel model;
    private View content;
    private TextInputEditText input;

    static void show(FragmentActivity activity, CrashReport report) {
        FragmentManager manager = activity.getSupportFragmentManager();
        if (manager.isStateSaved() || manager.findFragmentByTag(TAG) != null) return;
        CrashFeedbackDialog dialog = new CrashFeedbackDialog();
        Bundle args = new Bundle(); args.putString("crash_id", report.id); dialog.setArguments(args);
        dialog.showNow(manager, TAG);
    }
    @NonNull @Override public Dialog onCreateDialog(Bundle state) {
        model = new ViewModelProvider(this).get(CrashFeedbackViewModel.class);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        content = LayoutInflater.from(builder.getContext()).inflate(R.layout.dialog_crash_feedback, null);
        input = content.findViewById(R.id.crashFeedbackDescription);
        input.setText(model.description());
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { model.setDescription(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
        ((TextView) content.findViewById(R.id.crashFeedbackDetails)).setText(model.report == null ? "" : model.report.diagnostics());
        content.findViewById(R.id.crashFeedbackShowDetails).setOnClickListener(v -> model.toggleDetails());
        AlertDialog dialog = builder.setTitle(R.string.crash_feedback_title).setView(content)
                .setNegativeButton(R.string.crash_feedback_decline, null)
                .setPositiveButton(R.string.crash_feedback_send, null).create();
        dialog.setCanceledOnTouchOutside(false);
        model.changes.observe(this, ignored -> render());
        return dialog;
    }
    @Override public void onStart() {
        super.onStart();
        AlertDialog dialog = (AlertDialog) requireDialog();
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> model.decline());
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            model.connect();
            if (model.needsLogin) startActivity(new Intent(requireContext(), LoginActivity.class));
            else model.submit();
        });
        render();
    }
    @Override public void onResume() { super.onResume(); model.connect(); }
    @Override public void onCancel(@NonNull DialogInterface dialog) { model.decline(); super.onCancel(dialog); }
    private void render() {
        if (content == null || getDialog() == null) return;
        if (model.complete) {
            if (model.sent && !model.successAnnounced) {
                model.successAnnounced = true;
                Toast.makeText(requireContext(), R.string.crash_feedback_success, Toast.LENGTH_LONG).show();
            }
            dismissAllowingStateLoss(); return;
        }
        input.setEnabled(!model.submitting);
        content.findViewById(R.id.crashFeedbackProgress).setVisibility(model.submitting ? View.VISIBLE : View.GONE);
        content.findViewById(R.id.crashFeedbackDetails).setVisibility(model.detailsVisible() ? View.VISIBLE : View.GONE);
        ((TextView) content.findViewById(R.id.crashFeedbackShowDetails)).setText(model.detailsVisible()
                ? R.string.crash_feedback_hide_details : R.string.crash_feedback_show_details);
        TextView error = content.findViewById(R.id.crashFeedbackError);
        String message = model.needsLogin ? getString(R.string.crash_feedback_login_required) : model.error;
        error.setText(message); error.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE);
        AlertDialog dialog = (AlertDialog) requireDialog();
        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(model.needsLogin ? R.string.crash_feedback_login
                    : model.submitting ? R.string.crash_feedback_sending : R.string.crash_feedback_send);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!model.submitting);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(!model.submitting);
        }
        setCancelable(!model.submitting);
    }
    @Override public void onDestroyView() { content = null; input = null; super.onDestroyView(); }
}
