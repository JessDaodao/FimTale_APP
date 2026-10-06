package com.fimtale.editor;

import com.fimtale.utils.MdiIcons;
import com.fimtale.ui.icons.MdiTextInputLayout;
import com.google.android.material.textfield.TextInputEditText;

import android.content.Context;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import com.fimtale.model.TagGroup;
import com.fimtale.model.TagInfo;
import com.fimtale.network.ApiErrors;
import com.fimtale.network.RetrofitClient;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Paged server-side tag search; selections are independent of the visible page. */
public final class TagPickerDialog {
    private final Context context;
    private final com.fimtale.ui.PageErrorView pageError;
    private final Map<Integer, String> selected;
    private final Map<Integer, String> groups;
    private final Runnable changed;
    private final AlertDialog dialog;
    private final EditText search;
    private final Spinner category;
    private final ChipGroup tags;
    private final TextView status;
    private final MaterialButton previous, next;
    private int page = 1, generation;
    private Call<List<TagGroup>> call;
    public TagPickerDialog(Context context, Map<Integer, String> selected, Map<Integer, String> groups, Runnable changed) {
        this.context = context; this.selected = selected; this.groups = groups; this.changed = changed;
        LinearLayout layout = new LinearLayout(context); layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * context.getResources().getDisplayMetrics().density); layout.setPadding(pad, 0, pad, pad);
        category = new Spinner(context);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"题材", "读者注意", "历史标签", "角色", "其他标签"});
        category.setAdapter(adapter); layout.addView(category, new LinearLayout.LayoutParams(-1, 48 * pad / 20));
        MdiTextInputLayout searchLayout = new MdiTextInputLayout(context, null);
        searchLayout.setHint("输入名称搜索");
        search = new TextInputEditText(searchLayout.getContext()); search.setSingleLine(true);
        searchLayout.addView(search, new LinearLayout.LayoutParams(-1, -2));
        layout.addView(searchLayout, new LinearLayout.LayoutParams(-1, -2));
        MaterialButton find = new MaterialButton(context); find.setText("搜索"); layout.addView(find);
        status = new TextView(context); layout.addView(status);
        android.widget.ScrollView scroll = new android.widget.ScrollView(context);
        tags = new ChipGroup(context); scroll.addView(tags); layout.addView(scroll, new LinearLayout.LayoutParams(-1, 240 * pad / 20));
        pageError = com.fimtale.ui.PageErrorView.wrap(scroll);
        LinearLayout paging = new LinearLayout(context);
        previous = new MaterialButton(context); previous.setText("上一页"); paging.addView(previous, new LinearLayout.LayoutParams(0, -2, 1));
        next = new MaterialButton(context); next.setText("下一页"); paging.addView(next, new LinearLayout.LayoutParams(0, -2, 1)); layout.addView(paging);
        dialog = new MaterialAlertDialogBuilder(context).setTitle("选择标签与角色").setView(layout).setPositiveButton("完成", null).create();
        dialog.setOnDismissListener(d -> { generation++; if (call != null) call.cancel(); });
        find.setOnClickListener(v -> { page = 1; load(); });
        search.setOnEditorActionListener((v, action, event) -> { page = 1; load(); return true; });
        previous.setOnClickListener(v -> { page--; load(); }); next.setOnClickListener(v -> { page++; load(); });
        category.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) { page = 1; load(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
    }
    public void show() { dialog.show(); }
    public void dismiss() { dialog.dismiss(); }
    private void load() {
        pageError.hide();
        if (call != null) call.cancel();
        int current = ++generation;
        tags.removeAllViews(); status.setText("正在加载…"); previous.setEnabled(false); next.setEnabled(false);
        call = RetrofitClient.getInstance().getEditorTags(page, 30, search.getText().toString().trim(),
                Collections.singletonList(category.getSelectedItem().toString()));
        call.enqueue(new Callback<List<TagGroup>>() {
            @Override public void onResponse(Call<List<TagGroup>> c, Response<List<TagGroup>> response) {
                if (current != generation || !dialog.isShowing()) return;
                previous.setEnabled(page > 1);
                if (!response.isSuccessful() || response.body() == null) { status.setText(""); pageError.show(ApiErrors.message(response), TagPickerDialog.this::load); return; }
                int count = 0;
                for (TagGroup group : response.body()) if (group.tags != null) for (TagInfo tag : group.tags) {
                    count++;
                    Chip chip = new Chip(context); chip.setText(tag.getName()); chip.setCheckedIcon(MdiIcons.drawable(context, "check")); chip.setCheckable(true); chip.setChecked(selected.containsKey(tag.getId()));
                    chip.setOnCheckedChangeListener((button, checked) -> {
                        if (checked) { selected.put(tag.getId(), tag.getName()); groups.put(tag.getId(), group.name); }
                        else { selected.remove(tag.getId()); groups.remove(tag.getId()); } changed.run();
                    }); tags.addView(chip);
                }
                status.setText(count == 0 ? "本页没有标签，可修改关键词或返回上一页" : "第 " + page + " 页，点击选择或取消");
                next.setEnabled(count >= 30);
            }
            @Override public void onFailure(Call<List<TagGroup>> c, Throwable t) {
                if (current == generation && dialog.isShowing() && !c.isCanceled()) {
                    status.setText(""); previous.setEnabled(page > 1);
                    pageError.show(null, TagPickerDialog.this::load);
                }
            }
        });
    }
}
