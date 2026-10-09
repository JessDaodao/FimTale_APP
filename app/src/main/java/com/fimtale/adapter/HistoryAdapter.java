package com.fimtale.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.fimtale.R;
import com.fimtale.model.HistoryResponse;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    private final List<HistoryResponse.HistoryTopic> historyTopics = new ArrayList<>();
    private final Set<String> selectedKeys = new HashSet<>();
    private OnItemClickListener listener;
    private Runnable selectionChangedListener;
    private boolean batchMode;
    private boolean interactionEnabled = true;

    public interface OnItemClickListener {
        void onItemClick(HistoryResponse.HistoryTopic topic);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setOnSelectionChangedListener(Runnable listener) {
        selectionChangedListener = listener;
    }

    public boolean isBatchMode() { return batchMode; }
    public int getSelectedCount() { return selectedKeys.size(); }

    public void setBatchMode(boolean enabled) {
        if (batchMode == enabled) return;
        batchMode = enabled;
        if (!enabled) selectedKeys.clear();
        notifyDataSetChanged();
        selectionChanged();
    }

    public void setInteractionEnabled(boolean enabled) {
        if (interactionEnabled == enabled) return;
        interactionEnabled = enabled;
        notifyItemRangeChanged(0, getItemCount());
    }

    public boolean areAllSelected() {
        return !historyTopics.isEmpty() && selectedKeys.size() == historyTopics.size();
    }

    public void toggleSelectAll() {
        if (!batchMode || !interactionEnabled) return;
        if (areAllSelected()) selectedKeys.clear();
        else for (HistoryResponse.HistoryTopic topic : historyTopics) selectedKeys.add(topic.getKey());
        notifyItemRangeChanged(0, getItemCount());
        selectionChanged();
    }

    public List<HistoryResponse.HistoryTopic> getSelectedTopics() {
        List<HistoryResponse.HistoryTopic> selected = new ArrayList<>();
        for (HistoryResponse.HistoryTopic topic : historyTopics) {
            if (selectedKeys.contains(topic.getKey())) selected.add(topic);
        }
        return selected;
    }

    private void toggleSelection(HistoryResponse.HistoryTopic topic) {
        if (!batchMode || !interactionEnabled) return;
        if (!selectedKeys.remove(topic.getKey())) selectedKeys.add(topic.getKey());
        int index = historyTopics.indexOf(topic);
        if (index >= 0) notifyItemChanged(index);
        selectionChanged();
    }

    private void selectionChanged() {
        if (selectionChangedListener != null) selectionChangedListener.run();
    }

    public void setHistoryTopics(List<HistoryResponse.HistoryTopic> topics) {
        historyTopics.clear();
        historyTopics.addAll(topics);
        Set<String> present = new HashSet<>();
        for (HistoryResponse.HistoryTopic topic : topics) present.add(topic.getKey());
        selectedKeys.retainAll(present);
        notifyDataSetChanged();
        selectionChanged();
    }

    public void addHistoryTopics(List<HistoryResponse.HistoryTopic> topics) {
        int startPos = this.historyTopics.size();
        Set<String> present = new HashSet<>();
        for (HistoryResponse.HistoryTopic topic : historyTopics) present.add(topic.getKey());
        for (HistoryResponse.HistoryTopic topic : topics) {
            if (present.add(topic.getKey())) historyTopics.add(topic);
        }
        notifyItemRangeInserted(startPos, historyTopics.size() - startPos);
    }

    public void removeHistoryTopic(HistoryResponse.HistoryTopic deleted) {
        for (int i = historyTopics.size() - 1; i >= 0; i--) {
            HistoryResponse.HistoryTopic topic = historyTopics.get(i);
            if (topic.isRemovedBy(deleted)) {
                selectedKeys.remove(topic.getKey());
                historyTopics.remove(i);
                notifyItemRemoved(i);
            }
        }
        selectionChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HistoryResponse.HistoryTopic topic = historyTopics.get(position);
        holder.bind(topic);
        boolean selected = selectedKeys.contains(topic.getKey());
        holder.selected.setOnCheckedChangeListener(null);
        holder.selected.setVisibility(batchMode ? View.VISIBLE : View.GONE);
        holder.selected.setChecked(selected);
        holder.selected.setEnabled(interactionEnabled);
        holder.selected.setContentDescription(holder.itemView.getContext().getString(R.string.history_select_entry, topic.getTitle()));
        holder.selected.setOnCheckedChangeListener((button, checked) -> toggleSelection(topic));
        holder.card.setStrokeWidth(selected ? Math.round(holder.itemView.getResources().getDisplayMetrics().density * 2) : 0);
        holder.itemView.setEnabled(interactionEnabled);
        holder.itemView.setOnClickListener(v -> {
            if (!interactionEnabled) return;
            if (batchMode) toggleSelection(topic);
            else if (listener != null) listener.onItemClick(topic);
        });
    }

    @Override
    public int getItemCount() {
        return historyTopics.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTitle;
        private final LinearProgressIndicator progressIndicator;
        private final TextView tvProgress;
        private final TextView tvDate;
        private final MaterialCardView card;
        private final MaterialCheckBox selected;
        private final SimpleDateFormat dateFormat = new SimpleDateFormat(itemView.getContext().getString(R.string.common_date_time), Locale.getDefault());

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            progressIndicator = itemView.findViewById(R.id.progressIndicator);
            tvProgress = itemView.findViewById(R.id.tvProgress);
            tvDate = itemView.findViewById(R.id.tvDate);
            card = (MaterialCardView) itemView;
            card.setStrokeColor(MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorPrimary));
            selected = itemView.findViewById(R.id.historySelected);
        }

        public void bind(HistoryResponse.HistoryTopic topic) {
            tvTitle.setText(topic.getTitle());
            int progressPercent = (int) (topic.getProgress() * 100);
            int progressValue = (int) (topic.getProgress() * 1000);
            progressIndicator.setProgress(progressValue);
            tvProgress.setText(itemView.getContext().getString(R.string.common_percent, progressPercent));

            String dateStr = dateFormat.format(new Date(topic.getDateCreated() * 1000L));
            tvDate.setText(dateStr);
        }
    }
}
