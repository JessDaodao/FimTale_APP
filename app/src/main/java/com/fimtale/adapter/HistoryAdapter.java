package com.fimtale.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.fimtale.R;
import com.fimtale.model.HistoryResponse;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    private List<HistoryResponse.HistoryTopic> historyTopics = new ArrayList<>();
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(HistoryResponse.HistoryTopic topic);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setHistoryTopics(List<HistoryResponse.HistoryTopic> topics) {
        this.historyTopics = topics;
        notifyDataSetChanged();
    }

    public void addHistoryTopics(List<HistoryResponse.HistoryTopic> topics) {
        int startPos = this.historyTopics.size();
        this.historyTopics.addAll(topics);
        notifyItemRangeInserted(startPos, topics.size());
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
        holder.bind(topic, listener);
    }

    @Override
    public int getItemCount() {
        return historyTopics.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private TextView tvTitle;
        private LinearProgressIndicator progressIndicator;
        private TextView tvProgress;
        private TextView tvDate;
        private SimpleDateFormat dateFormat = new SimpleDateFormat(itemView.getContext().getString(R.string.common_date_time), Locale.getDefault());

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            progressIndicator = itemView.findViewById(R.id.progressIndicator);
            tvProgress = itemView.findViewById(R.id.tvProgress);
            tvDate = itemView.findViewById(R.id.tvDate);
        }

        public void bind(HistoryResponse.HistoryTopic topic, OnItemClickListener listener) {
            tvTitle.setText(topic.getTitle());
            int progressPercent = (int) (topic.getProgress() * 100);
            int progressValue = (int) (topic.getProgress() * 1000);
            progressIndicator.setProgress(progressValue);
            tvProgress.setText(itemView.getContext().getString(R.string.common_percent, progressPercent));
            
            String dateStr = dateFormat.format(new Date(topic.getDateCreated() * 1000L));
            tvDate.setText(dateStr);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(topic);
                }
            });
        }
    }
}
