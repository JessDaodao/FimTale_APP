package com.fimtale.adapter;


import com.fimtale.utils.MdiIcons;

import android.view.LayoutInflater;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fimtale.R;
import com.fimtale.model.Comment;
import com.fimtale.UserDetailActivity;
import com.fimtale.utils.BbCodeRendering;
import io.noties.markwon.Markwon;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;

import java.util.List;

public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.CommentViewHolder> {

    private List<Comment> commentList;
    private final Markwon markwon;

    public CommentAdapter(List<Comment> commentList, Context context) {
        this.commentList = commentList;
        markwon = BbCodeRendering.create(context);
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
        holder.tvUserName.setText(comment.pinned ? holder.itemView.getContext().getString(R.string.comments_pinned_author, comment.getUserName()) : comment.getUserName());
        holder.tvTime.setText(comment.getTime());
        BbCodeRendering.setText(markwon, holder.tvContent, comment.getContent());
        holder.tvChapter.setText(holder.itemView.getContext().getString(R.string.comments_context, comment.id, comment.getChapterTitle(), comment.replyCommentId > 0 ? holder.itemView.getContext().getString(R.string.comments_reply_to, comment.replyCommentId) : ""));
        View.OnClickListener openUser = v -> {
            if (comment.user == null || comment.getUserName() == null || comment.getUserName().isEmpty()) return;
            v.getContext().startActivity(new Intent(v.getContext(), UserDetailActivity.class)
                    .putExtra(UserDetailActivity.EXTRA_USERNAME, comment.getUserName()));
        };
        holder.ivAvatar.setOnClickListener(openUser); holder.tvUserName.setOnClickListener(openUser);

        Glide.with(holder.itemView.getContext())
                .load(comment.getAvatarUrl())
                .transform(new CircleCrop())
                .placeholder(MdiIcons.drawable(holder.itemView.getContext(), "account"))
                .error(MdiIcons.drawable(holder.itemView.getContext(), "account"))
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
