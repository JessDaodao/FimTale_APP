package com.fimtale.ui;


import com.fimtale.utils.MdiIcons;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
        dialog.setContentView(R.layout.dialog_ftemoji_picker);
        RecyclerView grid = dialog.findViewById(R.id.ftemojiGrid);
        grid.setLayoutManager(new GridLayoutManager(dialog.getContext(), 6));
        grid.setAdapter(new EmojiAdapter(dialog.getContext(), name -> {
            onSelected.accept(name);
            dialog.dismiss();
        }));
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
            holder.button.setContentDescription(context.getString(R.string.emoji_insert_description, name));
            holder.button.setOnClickListener(v -> onSelected.accept(name));
            if (holder.target != null) Glide.with(context).clear(holder.target);
            holder.button.setIcon(MdiIcons.drawable(context, "emoticon-outline"));
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
