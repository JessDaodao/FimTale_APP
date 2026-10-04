package com.fimtale.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fimtale.R;
import com.fimtale.network.SiteUrls;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

import java.util.function.Consumer;

/** Shared native picker for the site's :ftemoji_name: placeholders. */
public final class FtemojiPicker {
    private FtemojiPicker() {}

    public static final String[] NAMES = {
            "abwut", "ajsup", "applehorror", "appleroll", "celestiahappy", "celestiahurt",
            "cocosympathy", "cozyglow", "dice", "dice_1", "dice_2", "dice_3", "dice_4",
            "dice_5", "dice_6", "discordgreen", "facehoof", "fluttercutie", "flutterfear",
            "flutterhay", "flutteryay", "gallusbeg", "grannyshocked", "joy", "lunagasp",
            "lunagrump", "lunateehee", "lunawait", "lyra", "noooo", "ocelluspray", "octyhey",
            "ohcomeon", "pinkamina", "pinkiecry", "pinkiesad", "pinkiesugar", "rarishock",
            "raritydaww", "raritynews", "rarityyell", "rdscared", "redheartshocked", "sgpopcorn",
            "sgsneaky", "silverkiddingme", "silverstream", "smolderpissed", "soawesome",
            "songbirdpose", "spikepushy", "starlightrage", "sunsetgrump", "sunspicious",
            "tempestgaze", "trixiecute", "trixiesad", "twicrazy", "twieek", "twievil",
            "twisheepish", "wahaha"
    };

    public static void show(Context context, Consumer<String> onSelected) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(context, 12);
        content.setPadding(padding, padding, padding, dp(context, 20));

        TextView title = new TextView(context);
        title.setText("插入表情");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTextColor(resolveColor(context, com.google.android.material.R.attr.colorOnSurface));
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(context, 4), 0, dp(context, 4), dp(context, 8));
        content.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 40)));

        RecyclerView grid = new RecyclerView(context);
        grid.setLayoutManager(new GridLayoutManager(context, 6));
        grid.setAdapter(new EmojiAdapter(context, name -> {
            onSelected.accept(name);
            dialog.dismiss();
        }));
        content.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 320)));
        dialog.setContentView(content);
        dialog.show();
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static int resolveColor(Context context, int attr) {
        TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(attr, value, true);
        return value.data == 0 ? Color.BLACK : value.data;
    }

    private static final class EmojiAdapter extends RecyclerView.Adapter<EmojiAdapter.Holder> {
        private final Context context;
        private final Consumer<String> onSelected;

        EmojiAdapter(Context context, Consumer<String> onSelected) {
            this.context = context;
            this.onSelected = onSelected;
        }

        @Override public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            MaterialButton button = new MaterialButton(context);
            button.setText("");
            button.setIconTint(null);
            button.setIconSize(dp(context, 32));
            button.setIconPadding(0);
            button.setGravity(Gravity.CENTER);
            button.setInsetTop(0);
            button.setInsetBottom(0);
            button.setStrokeWidth(0);
            button.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
            button.setRippleColor(ColorStateList.valueOf(resolveColor(context, com.google.android.material.R.attr.colorSecondaryContainer)));
            button.setPadding(dp(context, 6), dp(context, 6), dp(context, 6), dp(context, 6));
            button.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 56)));
            return new Holder(button);
        }

        @Override public void onBindViewHolder(Holder holder, int position) {
            String name = NAMES[position];
            holder.button.setContentDescription("插入表情 " + name);
            holder.button.setOnClickListener(v -> onSelected.accept(name));
            if (holder.target != null) Glide.with(context).clear(holder.target);
            holder.button.setIcon(ContextCompat.getDrawable(context, R.drawable.placeholder_image));
            holder.target = new CustomTarget<Drawable>() {
                @Override public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                    holder.button.setIcon(resource);
                }
                @Override public void onLoadCleared(@Nullable Drawable placeholder) {
                    holder.button.setIcon(placeholder);
                }
            };
            Glide.with(context).load(SiteUrls.media("/img/ftemoji/" + name + ".png"))
                    .into(holder.target);
        }

        @Override public int getItemCount() { return NAMES.length; }

        @Override public void onViewRecycled(@NonNull Holder holder) {
            if (holder.target != null) Glide.with(context).clear(holder.target);
            holder.target = null;
            super.onViewRecycled(holder);
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final MaterialButton button;
            CustomTarget<Drawable> target;
            Holder(MaterialButton button) {
                super(button);
                this.button = button;
            }
        }
    }
}
