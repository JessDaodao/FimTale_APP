package com.fimtale.adapter;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.fimtale.R;
import com.fimtale.ReaderActivity;
import com.fimtale.SiteActivity;
import com.fimtale.TopicDetailActivity;
import com.fimtale.UserDetailActivity;
import com.fimtale.model.AuthorInfo;
import com.fimtale.model.TimelineItem;
import com.fimtale.network.SiteUrls;
import com.fimtale.utils.BbCodeRendering;
import com.fimtale.utils.MdiIcons;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import io.noties.markwon.Markwon;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public final class TimelineAdapter extends RecyclerView.Adapter<TimelineAdapter.Holder> {
    private final List<TimelineItem> items;
    private final Set<String> reposting;
    private final Consumer<TimelineItem> onRepost;
    private final Markwon renderer;

    public TimelineAdapter(Context context, List<TimelineItem> items, Set<String> reposting,
                           Consumer<TimelineItem> onRepost) {
        this.items = items; this.reposting = reposting; this.onRepost = onRepost;
        renderer = BbCodeRendering.create(context);
        setStateRestorationPolicy(StateRestorationPolicy.PREVENT_WHEN_EMPTY);
    }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_timeline, parent, false));
    }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        TimelineItem item = items.get(position);
        Context context = holder.itemView.getContext();
        AuthorInfo user = item.fromUser;
        boolean hasUser = user != null && !TextUtils.isEmpty(user.getUserName());
        holder.author.setText(hasUser ? user.getUserName() : "频道动态");
        holder.action.setText(item.actionText());
        holder.time.setText(time(item.createdAt));
        View.OnClickListener openUser = view -> openUser(context, user);
        holder.author.setOnClickListener(hasUser ? openUser : null);
        holder.avatar.setOnClickListener(hasUser ? openUser : null);
        holder.avatar.setContentDescription(hasUser ? "查看 " + user.getUserName() + " 的个人资料" : "频道动态");
        Glide.with(context).load(hasUser ? user.getAvatar() : null).circleCrop()
                .placeholder(MdiIcons.drawable(context, hasUser ? "account" : "wifi"))
                .error(MdiIcons.drawable(context, hasUser ? "account" : "wifi")).into(holder.avatar);

        boolean available = item.isAvailable();
        TimelineItem.Entity entity = item.entity;
        boolean work = available && item.isWork();
        setOptional(holder.title, available ? entity.title : null);
        AuthorInfo workAuthor = work ? entity.user : null;
        setOptional(holder.workAuthor, workAuthor == null ? null : workAuthor.getUserName());
        holder.workAuthor.setOnClickListener(view -> openUser(context, workAuthor));
        holder.body.setText(null);
        BbCodeRendering.setText(renderer, holder.body, item.body());
        String cover = work ? SiteUrls.media(entity.cover) : null;
        holder.cover.setVisibility(TextUtils.isEmpty(cover) ? View.GONE : View.VISIBLE);
        Glide.with(context).clear(holder.cover);
        if (!TextUtils.isEmpty(cover)) Glide.with(context).load(cover)
                .placeholder(MdiIcons.coverPlaceholder(context)).error(MdiIcons.coverPlaceholder(context)).into(holder.cover);
        String stats = work ? entity.characters + " 字 · " + entity.views + " 阅读 · " + entity.comments
                + " 评论 · " + entity.favorites + " 收藏"
                : available && item.type == TimelineItem.CHAPTER && entity.characters > 0 ? entity.characters + " 字" : null;
        setOptional(holder.stats, stats);

        View.OnClickListener open = view -> openItem(context, item);
        holder.card.setOnClickListener(item.path() == null ? null : open);
        holder.title.setOnClickListener(item.path() == null ? null : open);
        holder.open.setVisibility(item.path() == null ? View.GONE : View.VISIBLE);
        holder.open.setText(item.type == TimelineItem.CHAPTER ? "阅读章节"
                : item.type == TimelineItem.COMMENT || item.type == TimelineItem.CHANNEL_COMMENT ? "查看评论" : "查看作品");
        holder.open.setOnClickListener(open);
        String contextPath = item.contextPath();
        holder.context.setVisibility(contextPath == null ? View.GONE : View.VISIBLE);
        holder.context.setText(TextUtils.isEmpty(item.contextLabel()) ? "查看原文" : item.contextLabel());
        holder.context.setOnClickListener(view -> openSite(context, contextPath));
        holder.repost.setVisibility(item.canHighlight() ? View.VISIBLE : View.GONE);
        holder.repost.setEnabled(!reposting.contains(item.key()));
        holder.repost.setText(reposting.contains(item.key()) ? "转发中…" : "转发");
        holder.repost.setOnClickListener(view -> onRepost.accept(item));
    }
    private static void setOptional(TextView view, String text) {
        view.setText(text); view.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
    }
    private static CharSequence time(String iso) {
        if (TextUtils.isEmpty(iso)) return "";
        try {
            long stamp = OffsetDateTime.parse(iso).toInstant().toEpochMilli();
            return DateUtils.getRelativeTimeSpanString(stamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
        } catch (RuntimeException ignored) { return iso; }
    }
    private static void openUser(Context context, AuthorInfo user) {
        if (user != null && !TextUtils.isEmpty(user.getUserName())) context.startActivity(
                new Intent(context, UserDetailActivity.class).putExtra(UserDetailActivity.EXTRA_USERNAME, user.getUserName()));
    }
    private static void openItem(Context context, TimelineItem item) {
        if (item.path() == null) return;
        if (item.isWork()) context.startActivity(new Intent(context, TopicDetailActivity.class)
                .putExtra(TopicDetailActivity.EXTRA_TOPIC_ID, item.entity.id));
        else if (item.type == TimelineItem.CHAPTER) context.startActivity(new Intent(context, ReaderActivity.class)
                .putExtra(ReaderActivity.EXTRA_WORK_ID, item.entity.workId)
                .putExtra(ReaderActivity.EXTRA_CHAPTER_ID, item.entity.id));
        else openSite(context, item.path());
    }
    private static void openSite(Context context, String path) {
        if (path != null) context.startActivity(new Intent(context, SiteActivity.class).putExtra(SiteActivity.EXTRA_PATH, path));
    }
    @Override public int getItemCount() { return items.size(); }
    @Override public void onViewRecycled(@NonNull Holder holder) {
        Glide.with(holder.itemView.getContext().getApplicationContext()).clear(holder.avatar);
        Glide.with(holder.itemView.getContext().getApplicationContext()).clear(holder.cover);
        holder.body.setText(null);
        super.onViewRecycled(holder);
    }
    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView avatar, cover;
        final TextView author, action, time, title, workAuthor, body, stats;
        final View card;
        final Chip context;
        final MaterialButton open, repost;
        Holder(View view) {
            super(view);
            avatar = view.findViewById(R.id.timelineAvatar); cover = view.findViewById(R.id.timelineCover);
            author = view.findViewById(R.id.timelineAuthor); action = view.findViewById(R.id.timelineAction);
            time = view.findViewById(R.id.timelineTime); title = view.findViewById(R.id.timelineTitle);
            workAuthor = view.findViewById(R.id.timelineWorkAuthor); body = view.findViewById(R.id.timelineBody);
            stats = view.findViewById(R.id.timelineStats); card = view.findViewById(R.id.timelineCard);
            context = view.findViewById(R.id.timelineContext); open = view.findViewById(R.id.timelineOpen);
            repost = view.findViewById(R.id.timelineRepost);
        }
    }
}
