package com.app.fimtale.adapter;

import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.fimtale.R;
import com.app.fimtale.model.RecommendedTopic;
import com.bumptech.glide.Glide;

import java.util.List;

public class BannerAdapter extends RecyclerView.Adapter<BannerAdapter.BannerViewHolder> {

    private List<RecommendedTopic> bannerItems;
    private OnBannerItemClickListener listener;

    public interface OnBannerItemClickListener {
        void onBannerItemClick(RecommendedTopic topic);
    }

    public BannerAdapter(List<RecommendedTopic> bannerItems, OnBannerItemClickListener listener) {
        this.bannerItems = bannerItems;
        this.listener = listener;
    }

    @NonNull
    @Override
    public BannerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.banner_item_topic, parent, false);
        return new BannerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BannerViewHolder holder, int position) {
        RecommendedTopic topic = bannerItems.get(position);
        
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onBannerItemClick(topic);
            }
        });

        holder.titleTextView.setText(topic.getTitle());
        holder.introTextView.setText(topic.getRecommendWord());

        if (holder.recommenderTextView != null) {
            holder.recommenderTextView.setText("推荐者: " + (topic.getRecommenderName() != null ? topic.getRecommenderName() : "未知"));
        }
        if (holder.authorTextView != null) {
            holder.authorTextView.setText("作者: " + (topic.getAuthorName() != null ? topic.getAuthorName() : "未知"));
        }

        Glide.with(holder.itemView.getContext())
                .load(topic.getBackground())
                .placeholder(R.drawable.ic_default_article_cover)
                .error(R.drawable.ic_default_article_cover)
                .into(holder.imageView);
    }

    @Override
    public int getItemCount() {
        if (bannerItems == null) return 0;
        return bannerItems.size();
    }

    @Override public void onViewRecycled(@NonNull BannerViewHolder holder) {
        Glide.with(holder.itemView.getContext().getApplicationContext()).clear(holder.imageView);
        super.onViewRecycled(holder);
    }

    public static class BannerViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView titleTextView;
        TextView introTextView;
        TextView authorTextView;
        TextView recommenderTextView;

        public BannerViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.bannerImageView);
            imageView.setRenderEffect(RenderEffect.createBlurEffect(15f, 15f, Shader.TileMode.CLAMP));
            titleTextView = itemView.findViewById(R.id.bannerTitleTextView);
            introTextView = itemView.findViewById(R.id.bannerIntroTextView);
            authorTextView = itemView.findViewById(R.id.bannerAuthorTextView);
            recommenderTextView = itemView.findViewById(R.id.bannerRecommenderTextView);
        }
    }
}
