package com.fimtale;

import com.fimtale.utils.MdiIcons;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.fimtale.model.BlockUserRequest;
import com.fimtale.model.BlockedUser;
import com.fimtale.model.ContentFilterDef;
import com.fimtale.model.FilterNode;
import com.fimtale.model.NamedContentFilter;
import com.fimtale.model.SetContentFilterRequest;
import com.fimtale.model.UserMaterial;
import com.fimtale.model.CurrentUser;
import com.fimtale.network.ApiErrors;
import com.fimtale.network.RetrofitClient;
import com.fimtale.utils.UserPreferences;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Native content-filter editor backed by the current user API. */
public class ContentFiltersActivity extends AppCompatActivity {
    private static final String[] RATING_LABELS = {"不限制", "仅 Everyone（全年龄）", "Everyone + Teen（隐藏限制级）"};
    private static final int[] RATING_VALUES = {0, 1, 2};
    private CircularProgressIndicator progress;
    private TextView currentLabel, expressionSummary, presetEmpty, blockedEmpty;
    private Spinner rating;
    private RadioGroup operator;
    private MaterialSwitch invert;
    private TextInputEditText tagInput;
    private ChipGroup tags;
    private LinearLayout presets, blocked;
    private CurrentUser user;
    private ContentFilterDef defaultFilter;
    private ContentFilterDef activeFilter;
    private final List<NamedContentFilter> filterPresets = new ArrayList<>();
    private final Set<String> editorTags = new LinkedHashSet<>();
    private List<BlockedUser> blockedUsers = new ArrayList<>();
    private Call<CurrentUser> userCall;
    private Call<ContentFilterDef> defaultCall;
    private Call<UserMaterial> saveCall;
    private Call<List<BlockedUser>> blockCall;
    private ValueAnimator headerAnimator;
    private boolean headerRaised;
    private boolean closed;
    private boolean suppressRating;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_content_filters);
        com.fimtale.utils.EditorWindowStyle.apply(this);
        MaterialToolbar toolbar = findViewById(R.id.filterToolbar);
        toolbar.setTitle("");
        toolbar.setBackground(null);
        ((TextView) findViewById(R.id.filterToolbarTitle)).setText("内容过滤");
        toolbar.setNavigationOnClickListener(v -> finish());
        ScrollView scroll = findViewById(R.id.filterScroll);
        View titleCard = findViewById(R.id.filterToolbarContainer);
        titleCard.addOnLayoutChangeListener((v, l, t, r, b, oldLeft, oldTop, oldRight, oldBottom) -> {
            int top = b + dp(16);
            if (scroll.getPaddingTop() != top) {
                scroll.setPadding(scroll.getPaddingLeft(), top, scroll.getPaddingRight(), scroll.getPaddingBottom());
            }
        });
        scroll.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) ->
                animateHeader(scroll.canScrollVertically(-1)));
        progress = findViewById(R.id.filterProgress);
        currentLabel = findViewById(R.id.filterCurrent);
        expressionSummary = findViewById(R.id.filterExpressionSummary);
        presetEmpty = findViewById(R.id.filterPresetEmpty);
        blockedEmpty = findViewById(R.id.filterBlockedEmpty);
        rating = findViewById(R.id.filterRating);
        operator = findViewById(R.id.filterOperator);
        invert = findViewById(R.id.filterInvert);
        tagInput = findViewById(R.id.filterTagInput);
        tags = findViewById(R.id.filterTags);
        presets = findViewById(R.id.filterPresets);
        blocked = findViewById(R.id.filterBlocked);
        rating.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, RATING_LABELS));
        rating.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                if (!suppressRating) renderEditorSummary();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        findViewById(R.id.filterAddTag).setOnClickListener(v -> addTagFromInput());
        tagInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) { addTagFromInput(); return true; }
            return false;
        });
        operator.setOnCheckedChangeListener((group, checkedId) -> renderEditorSummary());
        invert.setOnCheckedChangeListener((button, checked) -> renderEditorSummary());
        findViewById(R.id.filterDefault).setOnClickListener(v -> saveFilter(null, filterPresets, "已恢复默认过滤"));
        findViewById(R.id.filterNone).setOnClickListener(v -> saveFilter(new ContentFilterDef(0, null), filterPresets, "已关闭内容过滤"));
        findViewById(R.id.filterApply).setOnClickListener(v -> saveFilter(editorDefinition(), filterPresets, "过滤设置已应用"));
        findViewById(R.id.filterSavePreset).setOnClickListener(v -> showSavePresetDialog());
        if (!UserPreferences.isLoggedIn(this)) {
            startActivity(new Intent(this, LoginActivity.class)); finish(); return;
        }
        load();
    }

    private void load() {
        if (closed) return;
        setLoading(true);
        String token = UserPreferences.getToken(this);
        userCall = RetrofitClient.getInstance().getCurrentUser(token);
        userCall.enqueue(new Callback<CurrentUser>() {
            @Override public void onResponse(@NonNull Call<CurrentUser> call, @NonNull Response<CurrentUser> response) {
                if (!valid(call, userCall)) return;
                userCall = null;
                if (!response.isSuccessful() || response.body() == null) { setLoading(false); showError(ApiErrors.message(response)); return; }
                user = response.body();
                UserMaterial material = user.material == null ? new UserMaterial() : user.material;
                activeFilter = material.contentFilter == null ? null : material.contentFilter.copy();
                filterPresets.clear(); filterPresets.addAll(material.presets());
                seedEditor(activeFilter);
                renderAll();
                loadDefault(token);
                loadBlocked(token);
            }
            @Override public void onFailure(@NonNull Call<CurrentUser> call, @NonNull Throwable error) {
                if (!valid(call, userCall)) return;
                userCall = null; setLoading(false); showError("过滤设置加载失败，请重试");
            }
        });
    }

    private void loadDefault(String token) {
        defaultCall = RetrofitClient.getInstance().getDefaultContentFilter(token);
        defaultCall.enqueue(new Callback<ContentFilterDef>() {
            @Override public void onResponse(@NonNull Call<ContentFilterDef> call, @NonNull Response<ContentFilterDef> response) {
                if (!valid(call, defaultCall)) return;
                defaultCall = null; if (response.isSuccessful()) defaultFilter = response.body();
                if (activeFilter == null && defaultFilter != null) { seedEditor(defaultFilter); renderAll(); }
                setLoading(false);
            }
            @Override public void onFailure(@NonNull Call<ContentFilterDef> call, @NonNull Throwable error) {
                if (!valid(call, defaultCall)) return;
                defaultCall = null; setLoading(false);
            }
        });
    }

    private void loadBlocked(String token) {
        blockCall = RetrofitClient.getInstance().updateBlocklist(token, new BlockUserRequest());
        blockCall.enqueue(new Callback<List<BlockedUser>>() {
            @Override public void onResponse(@NonNull Call<List<BlockedUser>> call, @NonNull Response<List<BlockedUser>> response) {
                if (!valid(call, blockCall)) return;
                blockCall = null; if (response.isSuccessful()) { blockedUsers = response.body() == null ? new ArrayList<>() : response.body(); renderBlocked(); }
            }
            @Override public void onFailure(@NonNull Call<List<BlockedUser>> call, @NonNull Throwable error) {
                if (valid(call, blockCall)) blockCall = null;
            }
        });
    }

    private void seedEditor(ContentFilterDef definition) {
        editorTags.clear(); invert.setChecked(false);
        if (definition == null) { setRating(0); operator.check(R.id.filterAny); renderEditorSummary(); return; }
        setRating(definition.ratingCap);
        FilterNode node = definition.hiddenExpr;
        if (node != null && "not".equals(node.op) && node.children != null && !node.children.isEmpty()) {
            invert.setChecked(true); node = node.children.get(0);
        }
        if (node != null && "and".equals(node.op)) operator.check(R.id.filterAll); else operator.check(R.id.filterAny);
        if (node != null) {
            List<String> values = new ArrayList<>();
            node.collectTags(values);
            editorTags.addAll(values);
        }
        renderTags(); renderEditorSummary();
    }

    private void renderAll() { renderCurrent(); renderTags(); renderPresets(); renderBlocked(); renderEditorSummary(); }
    private void renderCurrent() {
        String label = activeFilter == null ? "默认" : isZero(activeFilter) ? "无过滤" : presetName(activeFilter);
        currentLabel.setText("当前过滤：" + label);
    }
    private String presetName(ContentFilterDef definition) {
        for (NamedContentFilter preset : filterPresets) if (same(definition, preset)) return preset.name == null ? "自定义" : preset.name;
        return "自定义";
    }
    private boolean isZero(ContentFilterDef definition) { return definition != null && definition.ratingCap == 0 && definition.hiddenExpr == null; }
    private boolean same(ContentFilterDef a, ContentFilterDef b) {
        if (a == null || b == null) return a == b;
        return a.ratingCap == b.ratingCap && TextUtils.equals(a.hiddenExpr == null ? "" : a.hiddenExpr.summary(), b.hiddenExpr == null ? "" : b.hiddenExpr.summary());
    }
    private void renderTags() {
        tags.removeAllViews();
        for (String value : editorTags) {
            Chip chip = new Chip(this); chip.setText(value); chip.setCloseIcon(MdiIcons.drawable(this, "close")); chip.setCloseIconVisible(true);
            chip.setOnCloseIconClickListener(v -> { editorTags.remove(value); renderTags(); renderEditorSummary(); }); tags.addView(chip);
        }
    }
    private void renderEditorSummary() {
        StringBuilder text = new StringBuilder("当前编辑：").append(RATING_LABELS[Math.max(0, Math.min(RATING_LABELS.length - 1, selectedRating()))]);
        if (!editorTags.isEmpty()) text.append(" · 屏蔽 ").append(editorTags.size()).append(" 个标签（").append(operator.getCheckedRadioButtonId() == R.id.filterAll ? "全部" : "任一").append(invert.isChecked() ? "，反选" : "").append("）");
        expressionSummary.setText(text.toString());
    }
    private void addTagFromInput() {
        String value = tagInput.getText() == null ? "" : tagInput.getText().toString().trim();
        if (value.isEmpty()) return;
        editorTags.add(value); tagInput.setText(""); renderTags(); renderEditorSummary();
    }
    private int selectedRating() { int position = rating.getSelectedItemPosition(); return position < 0 ? 0 : RATING_VALUES[position]; }
    private void setRating(int value) { int position = 0; for (int i = 0; i < RATING_VALUES.length; i++) if (RATING_VALUES[i] == value) position = i; suppressRating = true; rating.setSelection(position); suppressRating = false; }
    private ContentFilterDef editorDefinition() {
        List<FilterNode> children = new ArrayList<>(); for (String tag : editorTags) children.add(FilterNode.tag(tag));
        FilterNode node = children.isEmpty() ? null : FilterNode.group(operator.getCheckedRadioButtonId() == R.id.filterAll ? "and" : "or", children);
        if (node != null && invert.isChecked()) node = FilterNode.not(node);
        return new ContentFilterDef(selectedRating(), node);
    }

    private void saveFilter(ContentFilterDef definition, List<NamedContentFilter> presetsToSave, String success) {
        if (saveCall != null) return;
        setLoading(true);
        String token = UserPreferences.getToken(this);
        saveCall = RetrofitClient.getInstance().setContentFilter(token, new SetContentFilterRequest(definition, presetsToSave));
        saveCall.enqueue(new Callback<UserMaterial>() {
            @Override public void onResponse(@NonNull Call<UserMaterial> call, @NonNull Response<UserMaterial> response) {
                if (!valid(call, saveCall)) return;
                saveCall = null; setLoading(false);
                if (response.isSuccessful()) {
                    UserMaterial material = response.body(); if (material != null) { activeFilter = material.contentFilter == null ? null : material.contentFilter.copy(); filterPresets.clear(); filterPresets.addAll(material.presets()); }
                    if (definition != null) seedEditor(definition);
                    else if (defaultFilter != null) seedEditor(defaultFilter);
                    renderAll(); Toast.makeText(ContentFiltersActivity.this, success, Toast.LENGTH_SHORT).show();
                } else showError(ApiErrors.message(response));
            }
            @Override public void onFailure(@NonNull Call<UserMaterial> call, @NonNull Throwable error) {
                if (!valid(call, saveCall)) return;
                saveCall = null; setLoading(false); showError("保存失败，请重试");
            }
        });
    }

    private void showSavePresetDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        android.view.View content = android.view.LayoutInflater.from(builder.getContext()).inflate(R.layout.dialog_text_input, null);
        com.google.android.material.textfield.TextInputLayout field = content.findViewById(R.id.dialogTextInputLayout);
        field.setHint("预设名称");
        EditText input = content.findViewById(R.id.dialogTextInput);
        builder.setTitle("保存为预设").setView(content)
                .setNegativeButton("取消", null).setPositiveButton("保存", (dialog, which) -> {
                    String name = input.getText().toString().trim(); if (name.isEmpty()) { showError("请输入预设名称"); return; }
                    List<NamedContentFilter> next = new ArrayList<>(); for (NamedContentFilter item : filterPresets) if (!name.equals(item.name)) next.add(item);
                    next.add(new NamedContentFilter(name, editorDefinition())); saveFilter(activeFilter, next, "预设已保存");
                }).show();
    }

    private void renderPresets() {
        presets.removeAllViews(); presetEmpty.setVisibility(filterPresets.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
        for (NamedContentFilter preset : filterPresets) {
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(0, dp(6), 0, dp(6));
            TextView name = new TextView(this); name.setText((preset.name == null ? "未命名" : preset.name) + "  ·  " + summary(preset));
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            MaterialButton edit = new MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle); edit.setText("编辑"); edit.setOnClickListener(v -> { seedEditor(preset); }); row.addView(edit);
            MaterialButton apply = new MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle); apply.setText("应用"); apply.setOnClickListener(v -> saveFilter(preset, filterPresets, "预设已应用")); row.addView(apply);
            MaterialButton remove = new MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle); remove.setText("删除"); remove.setOnClickListener(v -> deletePreset(preset)); row.addView(remove);
            presets.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
    }
    private String summary(ContentFilterDef definition) { return RATING_LABELS[Math.max(0, Math.min(RATING_LABELS.length - 1, definition.ratingCap))] + (definition.hiddenExpr == null ? "" : " · " + definition.hiddenExpr.summary()); }
    private void deletePreset(NamedContentFilter preset) { List<NamedContentFilter> next = new ArrayList<>(filterPresets); next.remove(preset); saveFilter(activeFilter, next, "预设已删除"); }
    private void renderBlocked() {
        blocked.removeAllViews(); blockedEmpty.setVisibility(blockedUsers.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
        for (BlockedUser item : blockedUsers) {
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = new TextView(this); name.setText("@" + item.username); row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            MaterialButton remove = new MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle); remove.setText("取消屏蔽"); remove.setOnClickListener(v -> toggleBlock(item.userId)); row.addView(remove);
            blocked.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
    }
    private void toggleBlock(int userId) {
        if (blockCall != null) return;
        String token = UserPreferences.getToken(this); blockCall = RetrofitClient.getInstance().updateBlocklist(token, new BlockUserRequest(userId));
        blockCall.enqueue(new Callback<List<BlockedUser>>() {
            @Override public void onResponse(@NonNull Call<List<BlockedUser>> call, @NonNull Response<List<BlockedUser>> response) { if (!valid(call, blockCall)) return; blockCall = null; if (response.isSuccessful()) { blockedUsers = response.body() == null ? new ArrayList<>() : response.body(); renderBlocked(); } else showError(ApiErrors.message(response)); }
            @Override public void onFailure(@NonNull Call<List<BlockedUser>> call, @NonNull Throwable error) { if (valid(call, blockCall)) { blockCall = null; showError("更新屏蔽用户失败"); } }
        });
    }
    private boolean valid(Call<?> call, Call<?> current) { return !closed && !isFinishing() && !isDestroyed() && !call.isCanceled() && call == current; }
    private void setLoading(boolean value) { progress.setVisibility(value ? android.view.View.VISIBLE : android.view.View.GONE); findViewById(R.id.filterApply).setEnabled(!value); findViewById(R.id.filterDefault).setEnabled(!value); findViewById(R.id.filterNone).setEnabled(!value); findViewById(R.id.filterSavePreset).setEnabled(!value); }
    private void animateHeader(boolean raised) {
        if (headerRaised == raised) return;
        headerRaised = raised;
        if (headerAnimator != null) headerAnimator.cancel();
        View surface = findViewById(R.id.filterHeaderSurface);
        headerAnimator = ValueAnimator.ofFloat(surface.getAlpha(), raised ? 1f : 0f);
        headerAnimator.setDuration(200);
        headerAnimator.addUpdateListener(animation -> surface.setAlpha((float) animation.getAnimatedValue()));
        headerAnimator.start();
    }
    private void showError(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override protected void onDestroy() { closed = true; if (userCall != null) userCall.cancel(); if (defaultCall != null) defaultCall.cancel(); if (saveCall != null) saveCall.cancel(); if (blockCall != null) blockCall.cancel(); if (headerAnimator != null) headerAnimator.cancel(); super.onDestroy(); }
}
