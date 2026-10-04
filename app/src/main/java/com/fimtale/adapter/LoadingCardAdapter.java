package com.fimtale.adapter;

import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.fimtale.ui.ShimmerSkeletonView;

/** A placeholder at the end of an existing list while the next page loads. */
public final class LoadingCardAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private boolean loading;
    private final ShimmerSkeletonView.Layout layout;
    public LoadingCardAdapter() { this(ShimmerSkeletonView.Layout.CARD); }
    public LoadingCardAdapter(ShimmerSkeletonView.Layout layout) { this.layout = layout; }
    public void setLoading(boolean loading) {
        if (this.loading == loading) return;
        this.loading = loading;
        if (loading) notifyItemInserted(0); else notifyItemRemoved(0);
    }
    @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ShimmerSkeletonView view = new ShimmerSkeletonView(parent.getContext());
        view.setSkeletonLayout(layout);
        view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new RecyclerView.ViewHolder(view) {};
    }
    @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {}
    @Override public int getItemCount() { return loading ? 1 : 0; }
}
