package com.app.fimtale.adapter;

import android.view.LayoutInflater;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.fimtale.R;
import com.app.fimtale.model.Comment;
import com.app.fimtale.UserDetailActivity;
import com.app.fimtale.utils.BbCode;
import io.noties.markwon.Markwon;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.image.glide.GlideImagesPlugin;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;

import java.util.List;

public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.CommentViewHolder> {

    private List<Comment> commentList;
    private final Markwon markwon;

    public CommentAdapter(List<Comment> commentList, Context context) {
        this.commentList = commentList;
        markwon = Markwon.builder(context).usePlugin(HtmlPlugin.create())
                .usePlugin(GlideImagesPlugin.create(context.getApplicationContext())).build();
    }

    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_comment, parent, false);
        return new CommentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        Comment comment = commentList.get(position);
        holder.tvUserName.setText((comment.pinned ? "置顶 · " : "") + comment.getUserName());
        holder.tvTime.setText(comment.getTime());
        markwon.setMarkdown(holder.tvContent, BbCode.toMarkdown(comment.getContent()));
        holder.tvChapter.setText("#" + comment.id + " · 评于：" + comment.getChapterTitle()
                + (comment.replyCommentId > 0 ? " · 回复 #" + comment.replyCommentId : ""));
        View.OnClickListener openUser = v -> {
            if (comment.user == null || comment.getUserName() == null || comment.getUserName().isEmpty()) return;
            v.getContext().startActivity(new Intent(v.getContext(), UserDetailActivity.class)
                    .putExtra(UserDetailActivity.EXTRA_USERNAME, comment.getUserName()));
        };
        holder.ivAvatar.setOnClickListener(openUser); holder.tvUserName.setOnClickListener(openUser);

        Glide.with(holder.itemView.getContext())
                .load(comment.getAvatarUrl())
                .transform(new CircleCrop())
                .placeholder(R.drawable.placeholder_image)
                .error(R.drawable.placeholder_image)
                .into(holder.ivAvatar);
    }

    @Override
    public int getItemCount() {
        return commentList.size();
    }

    public void updateData(List<Comment> newComments) {
        this.commentList = newComments;
        notifyDataSetChanged();
    }

    @Override public void onViewRecycled(@NonNull CommentViewHolder holder) {
        Glide.with(holder.itemView.getContext().getApplicationContext()).clear(holder.ivAvatar);
        holder.tvContent.setText(null);
        super.onViewRecycled(holder);
    }

    static class CommentViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvUserName;
        TextView tvTime;
        TextView tvContent;
        TextView tvChapter;

        public CommentViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivAvatar);
            tvUserName = itemView.findViewById(R.id.tvUserName);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvContent = itemView.findViewById(R.id.tvContent);
            tvChapter = itemView.findViewById(R.id.tvChapter);
        }
    }
}
