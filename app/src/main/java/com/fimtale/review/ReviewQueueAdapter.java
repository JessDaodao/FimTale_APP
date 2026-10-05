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
import com.fimtale.UserDetailActivity;
import com.fimtale.utils.MdiIcons;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.tabs.TabLayout;
import java.util.LinkedHashMap;
import java.util.Map;

/** One scroll surface: filters and pagination move under the fixed floating title card. */
public final class ReviewQueueAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final ReviewQueueActivity activity;
    private final ReviewQueueViewModel model;
    private boolean binding;
    public ReviewQueueAdapter(ReviewQueueActivity activity, ReviewQueueViewModel model) {
        this.activity = activity; this.model = model;
    }
    @Override public int getItemCount() { return model.reviews.size() + 2; }
    @Override public int getItemViewType(int position) { return position == 0 ? 0 : position == getItemCount() - 1 ? 2 : 1; }
    @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        View view = LayoutInflater.from(parent.getContext()).inflate(type == 0 ? R.layout.item_review_queue_header
                : type == 2 ? R.layout.item_review_queue_footer : R.layout.item_review, parent, false);
        if (type == 0) {
            TabLayout tabs = view.findViewById(R.id.reviewTabs);
            for (int title : new int[]{R.string.review_assigned, R.string.review_pending_tab, R.string.review_history_tab})
                tabs.addTab(tabs.newTab().setText(title));
            tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override public void onTabSelected(TabLayout.Tab tab) {
                    if (!binding) model.chooseMode(ReviewQueueViewModel.Mode.values()[tab.getPosition()]);
                }
                @Override public void onTabUnselected(TabLayout.Tab tab) {}
                @Override public void onTabReselected(TabLayout.Tab tab) {}
            });
            ((ChipGroup) view.findViewById(R.id.reviewStatusFilters)).setOnCheckedStateChangeListener((group, ids) -> {
                if (binding || ids.isEmpty()) return;
                Integer status = null;
                if (ids.get(0) == R.id.reviewFilterPassed) status = ReviewEntry.PASSED;
                else if (ids.get(0) == R.id.reviewFilterRejected) status = ReviewEntry.REJECTED;
                model.chooseStatus(status);
            });
            view.findViewById(R.id.reviewClearFilter).setOnClickListener(v -> model.clearFilter());
        } else if (type == 2) {
            view.findViewById(R.id.reviewRetry).setOnClickListener(v -> {
                if (model.needsLogin) activity.startActivity(new Intent(activity, LoginActivity.class)); else model.retry();
            });
            view.findViewById(R.id.reviewPrevious).setOnClickListener(v -> model.previousPage());
            view.findViewById(R.id.reviewNext).setOnClickListener(v -> model.nextPage());
        }
        return new RecyclerView.ViewHolder(view) {};
    }
    @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        View v = holder.itemView;
        binding = true;
        if (getItemViewType(position) == 0) header(v);
        else if (getItemViewType(position) == 2) footer(v);
        else row(v, model.reviews.get(position - 1));
        binding = false;
    }
    private void header(View v) {
        TabLayout tabs = v.findViewById(R.id.reviewTabs);
        if (tabs.getSelectedTabPosition() != model.mode.ordinal()) tabs.selectTab(tabs.getTabAt(model.mode.ordinal()));
        enable(tabs, model.canReview() && !model.mutating);
        ChipGroup filters = v.findViewById(R.id.reviewStatusFilters);
        filters.setVisibility(model.mode == ReviewQueueViewModel.Mode.HISTORY && model.reviewFilter == null ? View.VISIBLE : View.GONE);
        filters.check(model.statusFilter == null ? R.id.reviewFilterAll : model.statusFilter == ReviewEntry.PASSED ? R.id.reviewFilterPassed : R.id.reviewFilterRejected);
        enable(filters, model.canReview() && !model.mutating);
        visible(v, R.id.reviewProgress, model.loading || model.mutating);
        boolean filtered = model.reviewFilter != null || model.workFilter != null;
        visible(v, R.id.reviewFilterLabel, filtered); visible(v, R.id.reviewClearFilter, filtered);
        if (filtered) text(v, R.id.reviewFilterLabel, activity.getString(model.reviewFilter != null ? R.string.review_record_filter : R.string.review_work_filter,
                model.reviewFilter != null ? model.reviewFilter : model.workFilter));
        v.findViewById(R.id.reviewClearFilter).setEnabled(!model.mutating);
    }
    private void footer(View v) {
        String message = model.error;
        if (message.isEmpty() && model.uncertain) message = activity.getString(R.string.review_action_uncertain);
        if (message.isEmpty() && !model.loading && model.reviews.isEmpty()) message = activity.getString(
                model.mode == ReviewQueueViewModel.Mode.ASSIGNED ? R.string.review_empty_assigned
                        : model.mode == ReviewQueueViewModel.Mode.PENDING ? R.string.review_empty_pending : R.string.review_empty_history);
        text(v, R.id.reviewMessage, message); visible(v, R.id.reviewMessage, !message.isEmpty());
        visible(v, R.id.reviewRetry, !model.loading && (!model.error.isEmpty() || model.uncertain));
        text(v, R.id.reviewRetry, activity.getString(model.needsLogin ? R.string.review_login : R.string.review_retry));
        visible(v, R.id.reviewPagination, model.canReview() && (model.page > 1 || model.hasNext));
        text(v, R.id.reviewPage, activity.getString(R.string.review_page, model.page));
        v.findViewById(R.id.reviewPrevious).setEnabled(!model.loading && !model.mutating && model.page > 1);
        v.findViewById(R.id.reviewNext).setEnabled(!model.loading && !model.mutating && model.hasNext);
    }
    private void row(View v, ReviewEntry item) {
        text(v, R.id.reviewTitle, item.title());
        v.findViewById(R.id.reviewTitle).setOnClickListener(view -> activity.startActivity(new Intent(activity, TopicDetailActivity.class)
                .putExtra(TopicDetailActivity.EXTRA_TOPIC_ID, item.workId)));
        text(v, R.id.reviewNumber, activity.getString(R.string.review_work_number, item.workId, item.id));
        Chip status = v.findViewById(R.id.reviewStatus);
        status.setText(item.status == ReviewEntry.PENDING ? R.string.review_pending : item.status == ReviewEntry.PASSED ? R.string.review_passed
                : item.status == ReviewEntry.REJECTED ? R.string.review_rejected : R.string.review_unknown);
        status.setChipIcon(MdiIcons.drawable(activity, item.status == ReviewEntry.PENDING ? "clock-outline"
                : item.status == ReviewEntry.PASSED ? "check-circle-outline" : "close-circle-outline"));
        status.setChipIconVisible(true); status.setChipIconSize(activity.getResources().getDimension(R.dimen.mdi_icon_size));
        int tone = item.status == ReviewEntry.REJECTED ? com.google.android.material.R.attr.colorError
                : item.status == ReviewEntry.PASSED ? com.google.android.material.R.attr.colorPrimary : com.google.android.material.R.attr.colorOnSurfaceVariant;
        status.setChipIconTint(ColorStateList.valueOf(MaterialColors.getColor(status, tone)));
        text(v, R.id.reviewReason, item.reason()); visible(v, R.id.reviewReason, !item.reason().isEmpty());
        text(v, R.id.reviewCreated, activity.getString(R.string.review_created, ReviewEntry.date(item.createdAt)));
        text(v, R.id.reviewUpdated, activity.getString(R.string.review_updated, ReviewEntry.date(item.updatedAt)));
        String after = item.payload == null ? "" : ReviewEntry.date(item.payload.resubmitAfter);
        visible(v, R.id.reviewResubmit, !after.isEmpty());
        text(v, R.id.reviewResubmit, activity.getString(R.string.review_resubmit_selected, after));
        visible(v, R.id.reviewHistory, item.previousReviewCount > 0);
        text(v, R.id.reviewHistory, activity.getString(R.string.review_previous_count, item.previousReviewCount));
        v.findViewById(R.id.reviewHistory).setOnClickListener(view -> activity.startActivity(new Intent(activity, ReviewQueueActivity.class)
                .putExtra(ReviewQueueActivity.EXTRA_WORK_ID, item.workId)));
        ChipGroup people = v.findViewById(R.id.reviewAssignments); people.removeAllViews();
        Map<Integer, String> assignments = new LinkedHashMap<>();
        int resolver = item.status == ReviewEntry.PENDING ? 0 : item.resolver();
        if (resolver > 0) assignments.put(resolver, model.names.get(resolver));
        if (item.assignments != null) for (ReviewEntry.Assignment assignment : item.assignments) if (assignment != null && assignment.reviewerId > 0)
            assignments.put(assignment.reviewerId, assignment.reviewerName == null ? model.names.get(assignment.reviewerId) : assignment.reviewerName);
        visible(v, R.id.reviewUnassigned, assignments.isEmpty());
        for (Map.Entry<Integer, String> person : assignments.entrySet()) {
            String name = person.getValue();
            String label = name == null || name.isEmpty() ? activity.getString(R.string.review_user_number, person.getKey()) : name;
            Chip chip = new Chip(activity); chip.setEnsureMinTouchTargetSize(true); chip.setCheckable(false);
            chip.setText(person.getKey() == resolver ? activity.getString(R.string.review_resolver, label) : label);
            if (name != null && !name.isEmpty()) chip.setOnClickListener(view -> activity.startActivity(new Intent(activity, UserDetailActivity.class)
                    .putExtra(UserDetailActivity.EXTRA_USERNAME, name)));
            else chip.setClickable(false);
            people.addView(chip);
        }
        visible(v, R.id.reviewActions, item.status == ReviewEntry.PENDING && model.mode != ReviewQueueViewModel.Mode.HISTORY && model.canReview());
        visible(v, R.id.reviewAssign, model.canAssign());
        for (int id : new int[]{R.id.reviewPass, R.id.reviewReject, R.id.reviewAssign}) v.findViewById(id).setEnabled(model.canAct(item));
        v.findViewById(R.id.reviewPass).setOnClickListener(view -> model.open(ReviewQueueViewModel.Action.PASS, item));
        v.findViewById(R.id.reviewReject).setOnClickListener(view -> model.open(ReviewQueueViewModel.Action.REJECT, item));
        v.findViewById(R.id.reviewAssign).setOnClickListener(view -> model.open(ReviewQueueViewModel.Action.ASSIGN, item));
    }
    private static void text(View v, int id, String text) { ((TextView) v.findViewById(id)).setText(text); }
    private static void visible(View v, int id, boolean visible) { v.findViewById(id).setVisibility(visible ? View.VISIBLE : View.GONE); }
    private static void enable(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) enable(((ViewGroup) view).getChildAt(i), enabled);
    }
}
