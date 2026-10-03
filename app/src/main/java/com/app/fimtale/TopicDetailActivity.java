package com.app.fimtale;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.text.Html;
import android.os.Bundle;
import android.text.TextUtils;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.Pair;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.animation.ObjectAnimator;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.fimtale.adapter.ChapterAdapter;
import com.app.fimtale.model.AuthorInfo;
import com.app.fimtale.model.ChapterMenuItem;
import com.app.fimtale.model.TopicDetailResponse;
import com.app.fimtale.model.TopicInfo;
import com.app.fimtale.model.TopicTags;
import com.app.fimtale.network.RetrofitClient;
import com.app.fimtale.utils.UserPreferences;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.target.Target;

import java.security.MessageDigest;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.gson.internal.LinkedTreeMap;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.Context;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View.MeasureSpec;
import androidx.annotation.Nullable;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.noties.markwon.Markwon;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.image.AsyncDrawable;
import io.noties.markwon.image.glide.GlideImagesPlugin;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TopicDetailActivity extends AppCompatActivity {

    public static final String EXTRA_TOPIC_ID = "topic_id";

    private AppBarLayout appBarLayout;
    private CollapsingToolbarLayout collapsingToolbarLayout;
    private MaterialToolbar toolbar;
    private MaterialCardView toolbarContainer;

    private MaterialCardView imageContainer;
    private ImageView coverImageView;
    private View authorDivider;

    private TextView contentTextView, authorNameTextView;
    private TextView wordCountTextView, viewCountTextView, commentCountTextView, favoriteCountTextView;
    private TextView tagType, tagSource, tagLength, tagRate;
    private ShapeableImageView authorAvatarImageView;
    private LinearLayout authorLayout;
    private ChipGroup tagChipGroup;
    private ProgressBar progressBar;
    private NestedScrollView scrollView;
    private Button startReadingButton;
    private RecyclerView rvChapters;
    private LinearLayout chapterListContainer;
    private ChapterAdapter chapterAdapter;

    private Markwon markwon;
    private int currentTopicId;
    private AuthorInfo currentAuthor;
    
    private String currentTopicTitle;
    private String currentTopicIntro;
    private String currentTopicCoverUrl;
    private TopicTags currentTopicTags;
    private int currentWordCount;
    private int currentViewCount;
    private int currentCommentCount;
    private int currentFavoriteCount;
    
    private boolean isToolbarElevated = false;
    private ObjectAnimator elevationAnimator;
    private int firstChapterId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_topic_detail);

        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.colorBackground, typedValue, true);
        getWindow().setStatusBarColor(typedValue.data);

        setupViews();

        int cornerRadius = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
        int verticalPadding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());

        markwon = Markwon.builder(this)
                .usePlugin(HtmlPlugin.create())
                .usePlugin(GlideImagesPlugin.create(new GlideImagesPlugin.GlideStore() {
                    @NonNull
                    @Override
                    public RequestBuilder<Drawable> load(@NonNull AsyncDrawable drawable) {
                        RequestBuilder<Drawable> builder;
                        if (isDestroyed() || isFinishing()) {
                            builder = Glide.with(getApplicationContext()).load(drawable.getDestination());
                        } else {
                            builder = Glide.with(TopicDetailActivity.this).load(drawable.getDestination());
                        }
                        return builder.transform(new RoundedCorners(cornerRadius), new VerticalPaddingTransformation(verticalPadding));
                    }

                    @Override
                    public void cancel(@NonNull Target<?> target) {
                        if (!isDestroyed() && !isFinishing()) {
                            Glide.with(TopicDetailActivity.this).clear(target);
                        }
                    }
                }))
                .build();

        currentTopicId = getIntent().getIntExtra(EXTRA_TOPIC_ID, -1);
        if (currentTopicId != -1) {
            fetchTopicDetail(currentTopicId);
        } else {
            finish();
        }

        setupClickListeners();
    }

    private void setupViews() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        appBarLayout = findViewById(R.id.app_bar);
        collapsingToolbarLayout = findViewById(R.id.toolbar_layout);
        toolbarContainer = findViewById(R.id.toolbarContainer);

        imageContainer = findViewById(R.id.imageContainer);
        coverImageView = findViewById(R.id.detailCoverImageView);

        contentTextView = findViewById(R.id.detailContentTextView);

        authorLayout = findViewById(R.id.authorLayout);
        authorDivider = findViewById(R.id.authorDivider);
        authorAvatarImageView = findViewById(R.id.authorAvatarImageView);
        authorNameTextView = findViewById(R.id.authorNameTextView);
        
        wordCountTextView = findViewById(R.id.wordCountTextView);
        viewCountTextView = findViewById(R.id.viewCountTextView);
        commentCountTextView = findViewById(R.id.commentCountTextView);
        favoriteCountTextView = findViewById(R.id.favoriteCountTextView);

        tagType = findViewById(R.id.tagType);
        tagSource = findViewById(R.id.tagSource);
        tagLength = findViewById(R.id.tagLength);
        tagRate = findViewById(R.id.tagRate);

        tagChipGroup = findViewById(R.id.tagChipGroup);

        progressBar = findViewById(R.id.detailProgressBar);
        scrollView = findViewById(R.id.scrollView);
        startReadingButton = findViewById(R.id.startReadingButton);
        
        chapterListContainer = findViewById(R.id.chapterListContainer);
        rvChapters = findViewById(R.id.rvChapters);
        rvChapters.setLayoutManager(new LinearLayoutManager(this));
        rvChapters.setNestedScrollingEnabled(false);

        float targetElevation = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics());
        scrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            boolean shouldElevate = scrollY > 0;
            
            if (shouldElevate != isToolbarElevated) {
                isToolbarElevated = shouldElevate;
                
                if (elevationAnimator != null && elevationAnimator.isRunning()) {
                    elevationAnimator.cancel();
                }
                
                float start = toolbarContainer.getCardElevation();
                float end = shouldElevate ? targetElevation : 0;
                
                elevationAnimator = ObjectAnimator.ofFloat(toolbarContainer, "cardElevation", start, end);
                elevationAnimator.setDuration(200);
                elevationAnimator.start();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_topic_detail, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_more) {
            showMoreMenu();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showMoreMenu() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_topic_more, null);
        
        View parent = (View) view.getParent();
        if (parent != null) {
            parent.setBackgroundColor(Color.TRANSPARENT);
        }
        
        TextView btnCopyLink = view.findViewById(R.id.btnCopyLink);
        btnCopyLink.setOnClickListener(v -> {
            String link = com.app.fimtale.network.SiteUrls.work(currentTopicId);
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("FimTale Link", link);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "链接已复制", Toast.LENGTH_SHORT).show();
            }
            bottomSheetDialog.dismiss();
        });

        view.findViewById(R.id.btnOpenSite).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            com.app.fimtale.utils.DialogHelper.openSite(this, "/work/" + currentTopicId);
        });

        TextView btnSaveShareImage = view.findViewById(R.id.btnSaveShareImage);
        btnSaveShareImage.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            generateAndSaveShareImage();
        });
        
        bottomSheetDialog.setContentView(view);
        
        View bottomSheet = (View) view.getParent();
        if (bottomSheet != null) {
            bottomSheet.setBackgroundColor(Color.TRANSPARENT);
        }
        
        bottomSheetDialog.show();
    }

    private void generateAndSaveShareImage() {
        View shareView = LayoutInflater.from(this).inflate(R.layout.layout_share_image, null);
        
        ImageView coverImage = shareView.findViewById(R.id.shareCoverImage);
        TextView titleText = shareView.findViewById(R.id.shareTitleText);
        com.app.fimtale.ui.FadingTextView introText = shareView.findViewById(R.id.shareIntroText);
        ChipGroup tagChipGroup = shareView.findViewById(R.id.shareTagChipGroup);
        TextView wordCountText = shareView.findViewById(R.id.shareWordCount);
        TextView viewCountText = shareView.findViewById(R.id.shareViewCount);
        TextView commentCountText = shareView.findViewById(R.id.shareCommentCount);
        TextView favoriteCountText = shareView.findViewById(R.id.shareFavoriteCount);
        ImageView qrCodeImage = shareView.findViewById(R.id.shareQrCodeImage);
        ImageView logoImage = shareView.findViewById(R.id.shareLogoImage);

        try {
            java.io.InputStream is = getAssets().open("img/icon-full.png");
            android.graphics.drawable.Drawable d = android.graphics.drawable.Drawable.createFromStream(is, null);
            logoImage.setImageDrawable(d);
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }

        TextView tagType = shareView.findViewById(R.id.shareTagType);
        TextView tagSource = shareView.findViewById(R.id.shareTagSource);
        TextView tagLength = shareView.findViewById(R.id.shareTagLength);
        TextView tagRate = shareView.findViewById(R.id.shareTagRate);

        if (currentTopicTags != null) {
            if (!TextUtils.isEmpty(currentTopicTags.getType())) {
                tagType.setVisibility(View.VISIBLE);
                tagType.setText(currentTopicTags.getType());
            }
            if (!TextUtils.isEmpty(currentTopicTags.getSource())) {
                tagSource.setVisibility(View.VISIBLE);
                tagSource.setText(currentTopicTags.getSource());
            }
            if (!TextUtils.isEmpty(currentTopicTags.getLength())) {
                tagLength.setVisibility(View.VISIBLE);
                tagLength.setText(currentTopicTags.getLength());
            }
            if (!TextUtils.isEmpty(currentTopicTags.getRating())) {
                tagRate.setVisibility(View.VISIBLE);
                String rating = currentTopicTags.getRating();
                tagRate.setText(rating);

                int backgroundColor = 0x80000000;
                if (rating.equalsIgnoreCase("Everyone") || rating.equalsIgnoreCase("E")) {
                    backgroundColor = 0xFF4CAF50;
                } else if (rating.equalsIgnoreCase("Teen") || rating.equalsIgnoreCase("T")) {
                    backgroundColor = 0xFFFFC107;
                } else if (rating.equalsIgnoreCase("Restricted") || rating.equalsIgnoreCase("Mature") || rating.equalsIgnoreCase("M")) {
                    backgroundColor = 0xFFF44336;
                }

                Drawable background = tagRate.getBackground();
                if (background instanceof android.graphics.drawable.GradientDrawable) {
                    ((android.graphics.drawable.GradientDrawable) background.mutate()).setColor(backgroundColor);
                }
            }
            
            if (!TextUtils.isEmpty(currentTopicTags.getStatus())) {
                addShareTagChip(tagChipGroup, currentTopicTags.getStatus(), true);
            }
            if (currentTopicTags.getOtherTags() != null) {
                for (String tag : currentTopicTags.getOtherTags()) {
                    addShareTagChip(tagChipGroup, tag, false);
                }
            }
        }

        titleText.setText(currentTopicTitle != null ? currentTopicTitle : "");
        
        String plainIntro = "";
        if (currentTopicIntro != null) {
            plainIntro = Html.fromHtml(currentTopicIntro, Html.FROM_HTML_MODE_LEGACY).toString().trim();
        }
        introText.setText(plainIntro);
        
        wordCountText.setText(String.valueOf(currentWordCount));
        viewCountText.setText(String.valueOf(currentViewCount));
        commentCountText.setText(String.valueOf(currentCommentCount));
        favoriteCountText.setText(String.valueOf(currentFavoriteCount));

        String link = com.app.fimtale.network.SiteUrls.work(currentTopicId);
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(link, BarcodeFormat.QR_CODE, 400, 400);
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            Bitmap qrBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    qrBitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            qrCodeImage.setImageBitmap(qrBitmap);
        } catch (WriterException e) {
            e.printStackTrace();
        }

        if (currentTopicCoverUrl != null) {
            int cornerRadius = 32;
            Glide.with(this)
                .asBitmap()
                .load(currentTopicCoverUrl)
                .override(984, 600)
                .transform(new CenterCrop(), new RoundedCorners(cornerRadius))
                .into(new com.bumptech.glide.request.target.CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, @Nullable com.bumptech.glide.request.transition.Transition<? super Bitmap> transition) {
                        coverImage.setImageBitmap(resource);
                        renderAndSaveView(shareView);
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                    }
                    
                    @Override
                    public void onLoadFailed(@Nullable Drawable errorDrawable) {
                        renderAndSaveView(shareView);
                    }
                });
        } else {
            renderAndSaveView(shareView);
        }
    }

    private void renderAndSaveView(View view) {
        int widthSpec = MeasureSpec.makeMeasureSpec(1080, MeasureSpec.EXACTLY);
        int heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        view.measure(widthSpec, heightSpec);
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());

        Bitmap bitmap = Bitmap.createBitmap(view.getMeasuredWidth(), view.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);
        view.draw(canvas);

        showSharePreviewDialog(bitmap);
    }

    private void showSharePreviewDialog(Bitmap bitmap) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_share_preview, null);
        ImageView previewImageView = dialogView.findViewById(R.id.previewImageView);
        previewImageView.setImageBitmap(bitmap);

        new MaterialAlertDialogBuilder(this)
                .setTitle("分享图预览")
                .setView(dialogView)
                .setNegativeButton("取消", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("保存", (dialog, which) -> {
                    saveBitmapToGallery(bitmap);
                    dialog.dismiss();
                })
                .show();
    }

    private void saveBitmapToGallery(Bitmap bitmap) {
        String fileName = "FimTale_Share_" + System.currentTimeMillis() + ".png";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/FimTale");

        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri != null) {
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                runOnUiThread(() -> Toast.makeText(this, "分享图已保存至相册", Toast.LENGTH_SHORT).show());
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show());
            }
        } else {
            runOnUiThread(() -> Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show());
        }
    }

    private void fetchTopicDetail(int topicId) {
        progressBar.setVisibility(View.VISIBLE);
        scrollView.setVisibility(View.INVISIBLE);
        appBarLayout.setVisibility(View.INVISIBLE);
        startReadingButton.setVisibility(View.INVISIBLE);

        RetrofitClient.getInstance().getWork(topicId).enqueue(new Callback<TopicDetailResponse>() {
            @Override
            public void onResponse(Call<TopicDetailResponse> call, Response<TopicDetailResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    TopicDetailResponse data = response.body();
                    
                    progressBar.setVisibility(View.GONE);
                    scrollView.setVisibility(View.VISIBLE);
                    appBarLayout.setVisibility(View.VISIBLE);
                    
                    scrollView.setAlpha(0f);
                    appBarLayout.setAlpha(0f);
                    
                    scrollView.animate().alpha(1f).setDuration(300).start();
                    appBarLayout.animate().alpha(1f).setDuration(300).start();
                    
                    updateUI(data);
                } else {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(TopicDetailActivity.this, com.app.fimtale.network.ApiErrors.message(response), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<TopicDetailResponse> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(TopicDetailActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateUI(TopicDetailResponse data) {
        TopicInfo topic = data.getTopicInfo();
        AuthorInfo author = data.getAuthorInfo();
        this.currentAuthor = author;
        List<ChapterMenuItem> chapters = data.getMenu();

        currentTopicTitle = topic.getTitle();
        currentWordCount = topic.getWordCount();
        currentViewCount = topic.getViewCount();
        currentCommentCount = topic.getCommentCount();
        currentFavoriteCount = topic.getFavoriteCount();
        toolbar.setTitle(topic.getTitle());

        Pair<String, String> processedContent = preprocessHtmlContent(topic.getContent());
        String cleanedHtml = processedContent.first;
        String extractedImageUrl = processedContent.second;

        boolean isIntroPage = true;
        String finalCoverUrl = extractedImageUrl;
        if (finalCoverUrl == null && !TextUtils.isEmpty(topic.getBackground())) {
            finalCoverUrl = topic.getBackground();
        }
        currentTopicCoverUrl = finalCoverUrl;

        if (isIntroPage || finalCoverUrl != null) {
            imageContainer.setVisibility(View.VISIBLE);
            if (finalCoverUrl != null) {
                Glide.with(this)
                        .load(finalCoverUrl)
                        .placeholder(R.drawable.ic_default_article_cover)
                        .error(R.drawable.ic_default_article_cover)
                        .into(coverImageView);
            } else {
                coverImageView.setImageResource(R.drawable.ic_default_article_cover);
            }
        } else {
            imageContainer.setVisibility(View.GONE);
        }

        if (isIntroPage && author != null) {
            authorLayout.setVisibility(View.VISIBLE);
            authorDivider.setVisibility(View.VISIBLE);
            findViewById(R.id.statsLayout).setVisibility(View.VISIBLE);

            authorNameTextView.setText(author.getUserName());
            
            String authorAvatarUrl = author.getAvatar();
            Glide.with(this)
                    .load(authorAvatarUrl)
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .into(authorAvatarImageView);

            wordCountTextView.setText(String.valueOf(topic.getWordCount()));
            viewCountTextView.setText(String.valueOf(topic.getViewCount()));
            commentCountTextView.setText(String.valueOf(topic.getCommentCount()));
            favoriteCountTextView.setText(String.valueOf(topic.getFavoriteCount()));

            tagChipGroup.removeAllViews();
            tagChipGroup.setVisibility(View.VISIBLE);
            
            TopicTags tags = topic.getTags();
            currentTopicTags = tags;
            if (tags != null) {
                if (!TextUtils.isEmpty(tags.getStatus())) {
                    addTagChip(tags.getStatus(), true);
                }
                
                if (tags.getOtherTags() != null) {
                    for (String tag : tags.getOtherTags()) {
                        addTagChip(tag, false);
                    }
                }

                updateCoverTags(tags);
            }
        } else {
            authorLayout.setVisibility(View.GONE);
            authorDivider.setVisibility(View.GONE);
            findViewById(R.id.statsLayout).setVisibility(View.GONE);
            tagChipGroup.setVisibility(View.GONE);
        }

        String intro = topic.getIntro();
        if (TextUtils.isEmpty(intro)) {
            if (cleanedHtml.length() > 100) {
                intro = cleanedHtml.substring(0, 100) + "...";
            } else {
                intro = cleanedHtml;
            }
        }
        if (intro != null) {
            intro = intro.replaceAll("(!\\[.*?\\]\\(.*?\\))", "\n\n$1\n\n");
        }
        currentTopicIntro = intro;
        markwon.setMarkdown(contentTextView, com.app.fimtale.utils.BbCode.toMarkdown(
                (intro == null ? "" : intro) + "\n\n" + (topic.getContent() == null ? "" : topic.getContent())));
        
        // Preface belongs to the work; every directory entry is a real chapter.
        java.util.List<TopicDetailResponse.ChapterEdge> roots = com.app.fimtale.model.ChapterNavigation.choices(data, 0);
        firstChapterId = roots.size() == 1 && roots.get(0).to != null ? roots.get(0).to : 0;
        startReadingButton.setVisibility(View.VISIBLE);
        startReadingButton.setAlpha(1f);
        chapterListContainer.setVisibility(chapters.isEmpty() ? View.GONE : View.VISIBLE);
        chapterAdapter = new ChapterAdapter(chapters, item -> {
            Intent intent = new Intent(this, ReaderActivity.class);
            intent.putExtra(ReaderActivity.EXTRA_WORK_ID, currentTopicId);
            intent.putExtra(ReaderActivity.EXTRA_CHAPTER_ID, item.getId());
            startActivity(intent);
        });
        rvChapters.setAdapter(chapterAdapter);
    }

    private void updateCoverTags(TopicTags tags) {
        if (tags == null) return;

        if (!TextUtils.isEmpty(tags.getType())) {
            tagType.setVisibility(View.VISIBLE);
            tagType.setText(tags.getType());
        } else {
            tagType.setVisibility(View.GONE);
        }

        if (!TextUtils.isEmpty(tags.getSource())) {
            tagSource.setVisibility(View.VISIBLE);
            tagSource.setText(tags.getSource());
        } else {
            tagSource.setVisibility(View.GONE);
        }

        if (!TextUtils.isEmpty(tags.getLength())) {
            tagLength.setVisibility(View.VISIBLE);
            tagLength.setText(tags.getLength());
        } else {
            tagLength.setVisibility(View.GONE);
        }

        if (!TextUtils.isEmpty(tags.getRating())) {
            tagRate.setVisibility(View.VISIBLE);
            String rating = tags.getRating();
            tagRate.setText(rating);

            int backgroundColor = 0x80000000;
            if (rating != null) {
                if (rating.equalsIgnoreCase("Everyone") || rating.equalsIgnoreCase("E")) {
                    backgroundColor = 0xFF4CAF50;
                } else if (rating.equalsIgnoreCase("Teen") || rating.equalsIgnoreCase("T")) {
                    backgroundColor = 0xFFFFC107;
                } else if (rating.equalsIgnoreCase("Restricted") || rating.equalsIgnoreCase("Mature") || rating.equalsIgnoreCase("M")) {
                    backgroundColor = 0xFFF44336;
                }
            }

            Drawable background = tagRate.getBackground();
            if (background instanceof android.graphics.drawable.GradientDrawable) {
                ((android.graphics.drawable.GradientDrawable) background.mutate()).setColor(backgroundColor);
            }
        } else {
            tagRate.setVisibility(View.GONE);
        }
    }

    private void addShareTagChip(ChipGroup chipGroup, String text, boolean isStatus) {
        Chip chip = new Chip(this);
        chip.setText(text);
        chip.setCheckable(false);
        chip.setClickable(false);
        chip.setChipStrokeWidth(0);
        chip.setEnsureMinTouchTargetSize(false);
        
        chip.setChipStartPadding(12f);
        chip.setChipEndPadding(12f);
        chip.setChipMinHeight(24f);
        
        if (isStatus) {
            chip.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#FFEBEE")));
            chip.setTextColor(Color.parseColor("#D32F2F"));
        } else {
            chip.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#FFF5F5")));
            chip.setTextColor(Color.parseColor("#D32F2F"));
        }
        
        chipGroup.addView(chip);
    }

    private void addTagChip(String text, boolean isStatus) {
        Chip chip = new Chip(this);
        chip.setText(text);
        chip.setCheckable(false);
        chip.setClickable(false);
        chip.setChipStrokeWidth(0);
        chip.setEnsureMinTouchTargetSize(false);
        
        chip.setChipStartPadding(12f);
        chip.setChipEndPadding(12f);
        chip.setChipMinHeight(24f);
        
        if (isStatus) {
            TypedValue typedValue = new TypedValue();
            getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimaryContainer, typedValue, true);
            chip.setChipBackgroundColor(ColorStateList.valueOf(typedValue.data));
            
            getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnPrimaryContainer, typedValue, true);
            chip.setTextColor(typedValue.data);
        } else {
            TypedValue typedValue = new TypedValue();
            getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurfaceVariant, typedValue, true);
            chip.setChipBackgroundColor(ColorStateList.valueOf(typedValue.data));
            
            getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true);
            chip.setTextColor(typedValue.data);
        }

        if (!isStatus) chip.setOnClickListener(v -> {
            Intent intent = new Intent(this, TagArticlesActivity.class);
            intent.putExtra(TagArticlesActivity.EXTRA_TAG_NAME, text);
            startActivity(intent);
        });
        
        tagChipGroup.addView(chip);
    }

    private Pair<String, String> preprocessHtmlContent(String html) {
        if (html == null) return new Pair<>("", null);
        String imageUrl = null;
        String cleanedHtml = html;
        Pattern imgPattern = Pattern.compile("<img[^>]+src\\s*=\\s*['\"]([^'\"]+)['\"][^>]*>");
        Matcher imgMatcher = imgPattern.matcher(cleanedHtml);
        if (imgMatcher.find()) {
            imageUrl = imgMatcher.group(1);
            cleanedHtml = imgMatcher.replaceFirst("");
        }
        cleanedHtml = cleanedHtml.replaceAll("(?i)<h[1-6][^>]*>.*?</h[1-6]>", "");
        return new Pair<>(cleanedHtml.trim(), imageUrl);
    }

    private static class VerticalPaddingTransformation extends BitmapTransformation {
        private final int padding;

        public VerticalPaddingTransformation(int padding) {
            this.padding = padding;
        }

        @Override
        protected Bitmap transform(@NonNull BitmapPool pool, @NonNull Bitmap toTransform, int outWidth, int outHeight) {
            int width = toTransform.getWidth();
            int height = toTransform.getHeight();
            Bitmap result = pool.get(width, height + 2 * padding, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(result);
            canvas.drawColor(Color.TRANSPARENT);
            canvas.drawBitmap(toTransform, 0, padding, null);
            return result;
        }

        @Override
        public void updateDiskCacheKey(@NonNull MessageDigest messageDigest) {
            messageDigest.update(("VerticalPaddingTransformation" + padding).getBytes(CHARSET));
        }
    }

    private void setupClickListeners() {
        startReadingButton.setOnClickListener(v -> {
            if (firstChapterId != -1) {
                Intent intent = new Intent(this, ReaderActivity.class);
                intent.putExtra(ReaderActivity.EXTRA_WORK_ID, currentTopicId);
                intent.putExtra(ReaderActivity.EXTRA_CHAPTER_ID, firstChapterId);
                startActivity(intent);
            }
        });

        authorLayout.setOnClickListener(v -> {
            if (currentAuthor != null && !TextUtils.isEmpty(currentAuthor.getUserName())) {
                Intent intent = new Intent(this, UserDetailActivity.class);
                intent.putExtra(UserDetailActivity.EXTRA_USERNAME, currentAuthor.getUserName());
                startActivity(intent);
            }
        });
    }

}
