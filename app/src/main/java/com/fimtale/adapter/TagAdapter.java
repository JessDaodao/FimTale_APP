package com.fimtale.adapter;

import android.content.Context;
import android.content.Intent;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.fimtale.R;
import com.fimtale.TagArticlesActivity;
import com.fimtale.model.TagInfo;
import java.util.List;

public class TagAdapter extends RecyclerView.Adapter<TagAdapter.TagViewHolder> {

    private List<TagInfo> tagList;
    private Context context;

    public TagAdapter(List<TagInfo> tagList, Context context) {
        this.tagList = tagList;
        this.context = context;
    }

    @NonNull
    @Override
    public TagViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_tag_list, parent, false);
        return new TagViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TagViewHolder holder, int position) {
        TagInfo tag = tagList.get(position);
        holder.tvTagName.setText(tag.getName());
        
        if (tag.getIntro() != null && !tag.getIntro().trim().isEmpty()) {
            holder.tvTagIntro.setText(Html.fromHtml(tag.getIntro(), Html.FROM_HTML_MODE_COMPACT));
            holder.tvTagIntro.setVisibility(View.VISIBLE);
        } else {
            holder.tvTagIntro.setText("");
            holder.tvTagIntro.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, TagArticlesActivity.class);
            intent.putExtra(TagArticlesActivity.EXTRA_TAG_NAME, tag.getName());
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return tagList == null ? 0 : tagList.size();
    }

    static class TagViewHolder extends RecyclerView.ViewHolder {
        TextView tvTagName;
        TextView tvTagIntro;

        public TagViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTagName = itemView.findViewById(R.id.tvTagName);
            tvTagIntro = itemView.findViewById(R.id.tvTagIntro);
        }
    }
}
