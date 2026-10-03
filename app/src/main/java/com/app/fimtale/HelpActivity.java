package com.app.fimtale;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.ShapeAppearanceModel;
import java.io.IOException;
import java.io.InputStream;

public class HelpActivity extends AppCompatActivity {

    private LinearLayout container;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        container = findViewById(R.id.container);

        addText("浏览与阅读\n\n首页、搜索、作品详情和阅读器可以直接使用。\n\n" +
                "登录与同步\n\n在个人页登录 FimTale 账户后，可查看收藏和阅读历史，并同步阅读进度。\n" +
                "内容过滤可在设置中管理，与网站账户保持一致。\n\n" +
                "网站：" + com.app.fimtale.network.SiteUrls.SITE);
    }

    private void addText(String text) {
        TextView textView = new TextView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textView.setLayoutParams(params);
        textView.setText(text);
        textView.setAutoLinkMask(Linkify.WEB_URLS);
        textView.setLinksClickable(true);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
        textView.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        textView.setLineSpacing(0, 1.2f);
        container.addView(textView);
    }

}
