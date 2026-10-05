package com.fimtale.review;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.fimtale.R;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

public class ReviewActionDialog extends DialogFragment {
    public static final String TAG = "review_action";
    private static final String DATE = "resubmit_date", TIME = "resubmit_time";
    private ReviewQueueViewModel model;
    private View content;
    private long pickedDate;
    private boolean binding;

    @NonNull @Override public Dialog onCreateDialog(Bundle state) {
        model = new ViewModelProvider(requireActivity()).get(ReviewQueueViewModel.class);
        pickedDate = state == null ? MaterialDatePicker.todayInUtcMilliseconds() : state.getLong("picked_date");
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        content = LayoutInflater.from(builder.getContext()).inflate(R.layout.dialog_review_action, null);
        EditText input = content.findViewById(R.id.reviewReasonInput);
        input.setText(model.reason);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { if (!binding) model.reason = s.toString(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        content.findViewById(R.id.reviewClearTime).setOnClickListener(v -> model.setResubmitAfter(null));
        content.findViewById(R.id.reviewChooseTime).setOnClickListener(v -> chooseDate());
        content.findViewById(R.id.reviewRetryMembers).setOnClickListener(v -> model.fetchReviewers());
        AlertDialog dialog = builder.setTitle(model.action == ReviewQueueViewModel.Action.PASS ? R.string.review_confirm_pass
                        : model.action == ReviewQueueViewModel.Action.REJECT ? R.string.review_confirm_reject : R.string.review_assign)
                .setView(content).setNegativeButton(R.string.review_cancel, (d, w) -> model.closeDialog())
                .setPositiveButton(model.action == ReviewQueueViewModel.Action.PASS ? R.string.review_pass
                        : model.action == ReviewQueueViewModel.Action.REJECT ? R.string.review_reject : R.string.review_save, null).create();
        dialog.setCanceledOnTouchOutside(false);
        model.changes.observe(this, ignored -> render());
        return dialog;
    }
    @Override public void onStart() {
        super.onStart();
        ((AlertDialog) requireDialog()).getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> model.confirm());
        // The picker fragments restore independently; reattach their result listeners after rotation.
        Fragment date = getChildFragmentManager().findFragmentByTag(DATE), time = getChildFragmentManager().findFragmentByTag(TIME);
        if (date instanceof MaterialDatePicker) attachDate((MaterialDatePicker<Long>) date);
        if (time instanceof MaterialTimePicker) attachTime((MaterialTimePicker) time);
        render();
    }
    private void render() {
        if (content == null || getDialog() == null) return;
        if (model.action == ReviewQueueViewModel.Action.NONE || model.selected == null) { dismissAllowingStateLoss(); return; }
        binding = true;
        boolean reject = model.action == ReviewQueueViewModel.Action.REJECT, assign = model.action == ReviewQueueViewModel.Action.ASSIGN;
        ((TextView) content.findViewById(R.id.reviewActionTitle)).setText(model.action == ReviewQueueViewModel.Action.PASS
                ? getString(R.string.review_confirm_pass_message, model.selected.title()) : model.selected.title());
        show(R.id.reviewReasonLayout, reject); show(R.id.reviewChooseTime, reject); show(R.id.reviewClearTime, reject && model.resubmitAfter != null);
        show(R.id.reviewMembers, assign); show(R.id.reviewActionProgress, model.mutating || (assign && model.loadingReviewers));
        show(R.id.reviewActionError, !model.dialogError.isEmpty());
        ((TextView) content.findViewById(R.id.reviewActionError)).setText(model.dialogError);
        show(R.id.reviewRetryMembers, assign && !model.loadingReviewers && !model.canConfirm() && !model.mutating && !model.uncertain);
        content.findViewById(R.id.reviewReasonInput).setEnabled(!model.mutating);
        content.findViewById(R.id.reviewChooseTime).setEnabled(!model.mutating);
        content.findViewById(R.id.reviewClearTime).setEnabled(!model.mutating);
        ((TextView) content.findViewById(R.id.reviewChooseTime)).setText(model.resubmitAfter == null ? getString(R.string.review_resubmit_time)
                : getString(R.string.review_resubmit_selected, ReviewEntry.date(Instant.ofEpochMilli(model.resubmitAfter).toString())));
        if (assign) members();
        AlertDialog dialog = (AlertDialog) requireDialog();
        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(model.canConfirm());
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(!model.mutating);
        }
        setCancelable(!model.mutating);
        binding = false;
    }
    private void members() {
        LinearLayout group = content.findViewById(R.id.reviewMembers); group.removeAllViews();
        Map<Integer, String> options = new LinkedHashMap<>();
        for (ReviewEntry.Reviewer reviewer : model.reviewers) options.put(reviewer.userId, reviewer.username);
        // Keep existing assignees visible even if the team list no longer includes them.
        if (model.selected.assignments != null) for (ReviewEntry.Assignment a : model.selected.assignments)
            if (a != null && a.reviewerId > 0 && !options.containsKey(a.reviewerId)) options.put(a.reviewerId, a.reviewerName);
        if (options.isEmpty() && !model.loadingReviewers) {
            TextView empty = new TextView(group.getContext()); empty.setText(R.string.review_no_team); group.addView(empty);
        }
        for (Map.Entry<Integer, String> option : options.entrySet()) {
            MaterialCheckBox box = new MaterialCheckBox(group.getContext());
            box.setText(option.getValue() == null || option.getValue().isEmpty() ? getString(R.string.review_user_number, option.getKey()) : option.getValue());
            box.setChecked(model.selectedReviewers.contains(option.getKey())); box.setTag(option.getKey());
            box.setEnabled(!model.mutating && !model.loadingReviewers);
            box.setOnCheckedChangeListener((button, checked) -> {
                if (binding) return;
                if (checked) model.selectedReviewers.add(option.getKey()); else model.selectedReviewers.remove(option.getKey());
            });
            group.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }
    private void show(int id, boolean show) { content.findViewById(id).setVisibility(show ? View.VISIBLE : View.GONE); }
    private void chooseDate() {
        if (getChildFragmentManager().isStateSaved() || getChildFragmentManager().findFragmentByTag(DATE) != null) return;
        if (model.resubmitAfter != null) pickedDate = Instant.ofEpochMilli(model.resubmitAfter).atZone(ZoneId.systemDefault())
                .toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker().setTitleText(R.string.review_choose_date).setSelection(pickedDate).build();
        attachDate(picker); picker.show(getChildFragmentManager(), DATE);
    }
    private void attachDate(MaterialDatePicker<Long> picker) {
        picker.clearOnPositiveButtonClickListeners();
        picker.addOnPositiveButtonClickListener(value -> {
            if (model.action != ReviewQueueViewModel.Action.REJECT || model.mutating) return;
            pickedDate = value;
            LocalTime time = model.resubmitAfter == null ? LocalTime.now() : Instant.ofEpochMilli(model.resubmitAfter).atZone(ZoneId.systemDefault()).toLocalTime();
            MaterialTimePicker clock = new MaterialTimePicker.Builder().setTitleText(R.string.review_choose_time)
                    .setTimeFormat(TimeFormat.CLOCK_24H).setHour(time.getHour()).setMinute(time.getMinute()).build();
            attachTime(clock); clock.show(getChildFragmentManager(), TIME);
        });
    }
    private void attachTime(MaterialTimePicker picker) {
        picker.clearOnPositiveButtonClickListeners();
        picker.addOnPositiveButtonClickListener(v -> {
            if (model.action != ReviewQueueViewModel.Action.REJECT || model.mutating) return;
            LocalDate date = Instant.ofEpochMilli(pickedDate).atZone(ZoneOffset.UTC).toLocalDate();
            model.setResubmitAfter(date.atTime(picker.getHour(), picker.getMinute()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        });
    }
    @Override public void onCancel(@NonNull DialogInterface dialog) { model.closeDialog(); super.onCancel(dialog); }
    @Override public void onSaveInstanceState(@NonNull Bundle out) { out.putLong("picked_date", pickedDate); super.onSaveInstanceState(out); }
    @Override public void onDestroyView() { content = null; super.onDestroyView(); }
}
