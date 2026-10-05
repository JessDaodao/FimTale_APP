package com.fimtale.review;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.fimtale.LoginActivity;
import com.fimtale.R;
import com.fimtale.ReviewQueueActivity;
import com.fimtale.TopicDetailActivity;
import com.fimtale.utils.MdiIcons;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;
import java.util.ArrayList;
import java.util.List;

/** Author review sections share one scroll surface below the floating title card. */
public final class ReviewQueueAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final ReviewQueueActivity activity;
    private final ReviewQueueViewModel model;
    private final List<Object> rows = new ArrayList<>();
    private final int[] titles = {R.string.review_ready, R.string.review_pending, R.string.review_waiting, R.string.review_completed};
    private final int[] empty = {R.string.review_empty_ready, R.string.review_empty_pending, R.string.review_empty_waiting, R.string.review_empty_completed};
    private static class SectionRow {
        final int section, count;
        SectionRow(int section, int count) { this.section = section; this.count = count; }
    }
    public ReviewQueueAdapter(ReviewQueueActivity activity, ReviewQueueViewModel model) {
        this.activity = activity; this.model = model;
    }
    public void rebuild() {
        rows.clear();
        if (!model.reviews.isEmpty()) {
            long now = System.currentTimeMillis();
            for (ReviewQueueViewModel.Section section : ReviewQueueViewModel.Section.values()) {
                List<ReviewEntry> entries = new ArrayList<>();
                for (ReviewEntry entry : model.reviews) if (ReviewQueueViewModel.section(entry, now) == section) entries.add(entry);
                rows.add(new SectionRow(section.ordinal(), entries.size())); rows.addAll(entries);
            }
        }
        notifyDataSetChanged();
    }
    public int highlightedPosition() {
        for (int i = 0; i < rows.size(); i++) if (rows.get(i) instanceof ReviewEntry && activity.isHighlighted((ReviewEntry) rows.get(i))) return i + 1;
        return -1;
    }
    @Override public int getItemCount() { return rows.size() + 2; }
    @Override public int getItemViewType(int position) {
        return position == 0 ? 0 : position == getItemCount() - 1 ? 2 : rows.get(position - 1) instanceof SectionRow ? 3 : 1;
    }
    @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        int layout = type == 0 ? R.layout.item_review_queue_header : type == 2 ? R.layout.item_review_queue_footer
                : type == 3 ? R.layout.item_my_review_section : R.layout.item_review;
        return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false)) {};
    }
    @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        View view = holder.itemView;
        switch (getItemViewType(position)) {
            case 0:
                // Keep a small, stable loading slot so finishing a request does not shift the list's scroll origin.
                view.findViewById(R.id.reviewProgress).setVisibility(model.loading || model.mutating ? View.VISIBLE : View.INVISIBLE);
                break;
            case 1: row(view, (ReviewEntry) rows.get(position - 1)); break;
            case 2: footer(view); break;
            case 3:
                SectionRow section = (SectionRow) rows.get(position - 1);
                text(view, R.id.reviewSectionTitle, activity.getString(titles[section.section]));
                text(view, R.id.reviewSectionCount, String.valueOf(section.count));
                text(view, R.id.reviewSectionEmpty, activity.getString(empty[section.section]));
                visible(view, R.id.reviewSectionEmpty, section.count == 0);
                break;
        }
    }
    private void footer(View view) {
        String message = model.error;
        if (message.isEmpty() && model.uncertain) message = activity.getString(R.string.review_action_uncertain);
        if (message.isEmpty() && !model.loading && model.reviews.isEmpty()) message = activity.getString(R.string.review_empty);
        text(view, R.id.reviewMessage, message); visible(view, R.id.reviewMessage, !message.isEmpty());
        visible(view, R.id.reviewRetry, !model.loading && (!model.error.isEmpty() || model.uncertain));
        text(view, R.id.reviewRetry, activity.getString(model.needsLogin ? R.string.review_login : R.string.review_retry));
        view.findViewById(R.id.reviewRetry).setEnabled(!model.mutating);
        view.findViewById(R.id.reviewRetry).setOnClickListener(v -> {
            if (model.needsLogin) activity.startActivity(new Intent(activity, LoginActivity.class)); else model.refresh();
        });
    }
    private void row(View view, ReviewEntry entry) {
        ((MaterialCardView) view).setCardBackgroundColor(MaterialColors.getColor(view, activity.isHighlighted(entry)
                ? com.google.android.material.R.attr.colorPrimaryContainer : com.google.android.material.R.attr.colorSurfaceVariant));
        text(view, R.id.reviewTitle, entry.title());
        View.OnClickListener openWork = v -> activity.startActivity(new Intent(activity, TopicDetailActivity.class)
                .putExtra(TopicDetailActivity.EXTRA_TOPIC_ID, entry.workId));
        view.findViewById(R.id.reviewTitle).setOnClickListener(openWork);
        view.findViewById(R.id.reviewOpenWork).setOnClickListener(openWork);
        text(view, R.id.reviewNumber, activity.getString(R.string.review_work_number, entry.workId));
        Chip status = view.findViewById(R.id.reviewStatus);
        int label = entry.status == ReviewEntry.UNSUBMITTED ? R.string.review_unsubmitted
                : entry.status == ReviewEntry.PENDING ? R.string.review_pending : entry.status == ReviewEntry.PASSED ? R.string.review_passed
                : entry.status == ReviewEntry.REJECTED ? R.string.review_rejected : R.string.review_unknown;
        status.setText(label);
        status.setChipIcon(MdiIcons.drawable(activity, entry.status == ReviewEntry.UNSUBMITTED ? "file-document-outline"
                : entry.status == ReviewEntry.PENDING ? "clock-outline" : entry.status == ReviewEntry.PASSED ? "check-circle-outline" : "close-circle-outline"));
        status.setChipIconVisible(true); status.setChipIconSize(activity.getResources().getDimension(R.dimen.mdi_icon_size));
        int tone = entry.status == ReviewEntry.REJECTED ? com.google.android.material.R.attr.colorError
                : entry.status == ReviewEntry.PASSED ? com.google.android.material.R.attr.colorPrimary : com.google.android.material.R.attr.colorOnSurfaceVariant;
        status.setChipIconTint(ColorStateList.valueOf(MaterialColors.getColor(status, tone)));
        String description = entry.status == ReviewEntry.REJECTED ? entry.reason()
                : activity.getString(entry.status == ReviewEntry.UNSUBMITTED ? R.string.review_not_queued
                : entry.status == ReviewEntry.PENDING ? R.string.review_received
                : entry.status == ReviewEntry.PASSED ? R.string.review_finished : R.string.review_unknown);
        text(view, R.id.reviewReason, description); visible(view, R.id.reviewReason, !description.isEmpty());
        String time = entry.status == ReviewEntry.UNSUBMITTED ? "" : ReviewEntry.date(entry.updatedAt);
        if (time.isEmpty() && entry.status != ReviewEntry.UNSUBMITTED) time = ReviewEntry.date(entry.createdAt);
        text(view, R.id.reviewUpdated, activity.getString(R.string.review_updated, time.isEmpty() ? "—" : time));
        String after = entry.status == ReviewEntry.REJECTED && entry.payload != null ? ReviewEntry.date(entry.payload.resubmitAfter) : "";
        text(view, R.id.reviewResubmit, activity.getString(R.string.review_resubmit_selected, after));
        visible(view, R.id.reviewResubmit, !after.isEmpty());
        visible(view, R.id.reviewSubmit, entry.canSubmit(System.currentTimeMillis()));
        text(view, R.id.reviewSubmit, activity.getString(entry.status == ReviewEntry.UNSUBMITTED ? R.string.review_submit : R.string.review_resubmit));
        view.findViewById(R.id.reviewSubmit).setEnabled(model.canSubmit(entry));
        view.findViewById(R.id.reviewSubmit).setOnClickListener(v -> model.open(entry));
    }
    private static void text(View view, int id, String text) { ((TextView) view.findViewById(id)).setText(text); }
    private static void visible(View view, int id, boolean visible) { view.findViewById(id).setVisibility(visible ? View.VISIBLE : View.GONE); }
}
