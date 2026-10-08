package com.fimtale.editor;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.fimtale.R;
import com.fimtale.model.UserDetailResponse;
import com.fimtale.network.ApiErrors;
import com.fimtale.network.RetrofitClient;
import com.fimtale.ui.icons.MdiTextInputLayout;
import com.fimtale.utils.BbCodeRendering;
import com.fimtale.utils.MdiIcons;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.textfield.TextInputEditText;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Native format palette and parameter forms; source selection survives keyboard focus and rotation. */
public class EditorFormatDialog extends BottomSheetDialogFragment {
    public static final String TAG = "editor_formats";
    public interface Host {
        BbCodeEditText formatBody();
        int formatVersion();
        boolean canInsertFormat();
        void applyFormat(BbCodeInsertion.Edit edit);
    }
    private final List<EditText> inputs = new ArrayList<>();
    private View root;
    private LinearLayout content;
    private EditorFormat format;
    private String[] restoredValues;
    private Spinner choice;
    private MaterialCheckBox headerRow;
    private int restoredChoice;
    private boolean restoredHeader = true;
    private Call<UserDetailResponse> lookup;
    private boolean applied;

    public static EditorFormatDialog create(Host host) {
        EditorFormatDialog dialog = new EditorFormatDialog();
        BbCodeEditText body = host.formatBody(); Bundle args = new Bundle();
        int start = Math.max(0, Math.min(body.getSelectionStart(), body.getSelectionEnd()));
        int end = Math.max(start, Math.max(body.getSelectionStart(), body.getSelectionEnd()));
        args.putInt("start", start); args.putInt("end", end); args.putInt("version", host.formatVersion());
        dialog.setArguments(args); return dialog;
    }
    public static EditorFormatDialog editLink(Host host, BbCodeSyntax.Node link) {
        EditorFormatDialog dialog = create(host);
        String source = host.formatBody().getText().toString();
        Bundle args = dialog.requireArguments();
        args.putInt("start", link.start); args.putInt("end", link.end);
        args.putString("link_source", source.substring(link.start, link.end));
        String content = source.substring(link.contentStart, link.contentEnd);
        args.putString("link_content", content);
        args.putString("link_text", VisualEditing.decodeEntities(VisualEditing.selectedText(content, 0, content.length())));
        args.putString("link_url", link.argument.isEmpty() ? args.getString("link_text") : VisualEditing.decodeEntities(link.argument));
        return dialog;
    }
    private boolean editingLink() { return requireArguments().containsKey("link_source"); }
    private Host host() { return (Host) requireActivity(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @NonNull @Override public Dialog onCreateDialog(Bundle state) {
        if (editingLink()) format = EditorFormat.LINK;
        if (state != null) {
            String name = state.getString("format");
            if (name != null) try { format = EditorFormat.valueOf(name); } catch (IllegalArgumentException ignored) {}
            restoredValues = state.getStringArray("values"); restoredChoice = state.getInt("choice");
            restoredHeader = state.getBoolean("header", true);
        }
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        root = LayoutInflater.from(dialog.getContext()).inflate(R.layout.dialog_editor_formats, null);
        content = root.findViewById(R.id.editorFormatContent);
        root.findViewById(R.id.editorFormatBack).setOnClickListener(v -> {
            cancelLookup(); format = null; restoredValues = null; render();
        });
        root.findViewById(R.id.editorFormatInsert).setOnClickListener(v -> insert());
        dialog.setContentView(root);
        if (dialog.getWindow() != null) dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        return dialog;
    }
    @Override public void onStart() {
        super.onStart();
        BottomSheetDialog dialog = (BottomSheetDialog) requireDialog();
        View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet != null) {
            sheet.getLayoutParams().height = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
            sheet.requestLayout();
        }
        dialog.getBehavior().setMaxHeight(Math.round(getResources().getDisplayMetrics().heightPixels * 0.85f));
        dialog.getBehavior().setSkipCollapsed(true); dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        if (content.getChildCount() == 0) render();
    }
    private String selected() {
        BbCodeEditText body = host().formatBody();
        int start = requireArguments().getInt("start"), end = requireArguments().getInt("end");
        if (body == null || end > body.length() || start > end) return "";
        return body.getText().subSequence(start, end).toString();
    }
    private void render() {
        if (root == null) return;
        content.removeAllViews(); inputs.clear(); choice = null; headerRow = null; error("");
        root.findViewById(R.id.editorFormatBack).setVisibility(format == null || editingLink() ? View.GONE : View.VISIBLE);
        root.findViewById(R.id.editorFormatInsert).setVisibility(format == null ? View.GONE : View.VISIBLE);
        ((TextView) root.findViewById(R.id.editorFormatInsert)).setText(editingLink() ? R.string.editor_format_save : R.string.editor_format_insert);
        ((TextView) root.findViewById(R.id.editorFormatTitle)).setText(editingLink() ? getString(R.string.editor_edit_link)
                : format == null ? getString(R.string.editor_more_formats) : format.label);
        if (format == null) palette(); else form();
        if (restoredValues != null) {
            for (int i = 0; i < inputs.size() && i < restoredValues.length; i++) inputs.get(i).setText(restoredValues[i]);
            restoredValues = null;
        }
        ((androidx.core.widget.NestedScrollView) root.findViewById(R.id.editorFormatScroll)).scrollTo(0, 0);
    }
    private void palette() {
        String[] groups = {"文字样式", "标题与段落", "插入内容"};
        for (int group = 0; group < groups.length; group++) {
            TextView heading = new TextView(content.getContext()); heading.setText(groups[group]);
            heading.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
            heading.setPadding(0, dp(16), 0, dp(8)); heading.setAccessibilityHeading(true); content.addView(heading);
            LinearLayout row = null; int column = 0;
            for (EditorFormat item : EditorFormat.values()) if (item.group == group) {
                if (column == 0) { row = new LinearLayout(content.getContext()); content.addView(row); }
                TextView button = new TextView(content.getContext());
                button.setText(item.label); button.setTextSize(12); button.setGravity(android.view.Gravity.CENTER);
                button.setTextColor(MaterialColors.getColor(button, com.google.android.material.R.attr.colorOnSurface));
                button.setCompoundDrawablesWithIntrinsicBounds(null, MdiIcons.drawable(button.getContext(), item.icon), null, null);
                button.setCompoundDrawablePadding(dp(6)); button.setPadding(dp(2), dp(10), dp(2), dp(8));
                TypedValue ripple = new TypedValue(); button.getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
                button.setBackgroundResource(ripple.resourceId); button.setFocusable(true); button.setContentDescription(item.label);
                button.setTag(item.name()); button.setOnClickListener(v -> choose(item));
                row.addView(button, new LinearLayout.LayoutParams(0, dp(80), 1)); column = (column + 1) % 4;
            }
            if (column != 0) for (; column < 4; column++) row.addView(new View(content.getContext()), new LinearLayout.LayoutParams(0, dp(80), 1));
        }
    }
    private void choose(EditorFormat next) {
        if (next.immediate()) { apply(next.fragment(selected())); return; }
        format = next; restoredValues = null; restoredChoice = 0; restoredHeader = true; render();
    }
    private void field(String label, String value, int inputType) {
        Context context = content.getContext();
        MdiTextInputLayout layout = new MdiTextInputLayout(context, null); layout.setHint(label);
        TextInputEditText input = new TextInputEditText(layout.getContext()); input.setText(value); input.setInputType(inputType);
        input.setId(new int[]{R.id.editorFormatInput0, R.id.editorFormatInput1, R.id.editorFormatInput2, R.id.editorFormatInput3}[inputs.size()]);
        if ((inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0) { input.setMinLines(2); input.setMaxLines(5); }
        else input.setSingleLine(true);
        layout.addView(input); inputs.add(input);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(12);
        content.addView(layout, params);
    }
    private void options(String... labels) {
        choice = new Spinner(content.getContext()); choice.setId(R.id.editorFormatChoice);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(content.getContext(), android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); choice.setAdapter(adapter); choice.setSelection(restoredChoice);
        content.addView(choice, new LinearLayout.LayoutParams(-1, dp(56)));
    }
    private void form() {
        int text = InputType.TYPE_CLASS_TEXT, url = text | InputType.TYPE_TEXT_VARIATION_URI;
        switch (format) {
            case LINK:
                field("链接网址", editingLink() ? requireArguments().getString("link_url") : "https://", url);
                field("链接文字（可选）", editingLink() ? requireArguments().getString("link_text") : selected(), text);
                if (editingLink()) {
                    com.google.android.material.button.MaterialButton remove = new com.google.android.material.button.MaterialButton(content.getContext());
                    remove.setId(R.id.editorRemoveLink); remove.setText(R.string.editor_remove_link);
                    remove.setOnClickListener(v -> apply(BbCodeInsertion.atom(requireArguments().getString("link_content"), false)));
                    content.addView(remove);
                }
                break;
            case IMAGE:
                field("图片网址", "https://", url); field("宽度（可选，像素）", "", InputType.TYPE_CLASS_NUMBER);
                field("高度（可选，像素）", "", InputType.TYPE_CLASS_NUMBER); field("图片说明（可选）", "", text); break;
            case COLOR: case BACKGROUND:
                field("颜色", format == EditorFormat.COLOR ? "#E53935" : "#FFF59D", text);
                ChipGroup colors = new ChipGroup(content.getContext());
                String[] names = {"红", "橙", "黄", "绿", "蓝", "紫", "黑", "白"};
                String[] values = {"#E53935", "#F57C00", "#FFF59D", "#388E3C", "#1976D2", "#8E24AA", "#000000", "#FFFFFF"};
                for (int i = 0; i < names.length; i++) {
                    Chip color = new Chip(content.getContext()); color.setText(names[i]); color.setChipStrokeWidth(0);
                    color.setChipIcon(MdiIcons.drawable(color.getContext(), "circle"));
                    color.setChipIconTint(ColorStateList.valueOf(android.graphics.Color.parseColor(values[i])));
                    color.setChipIconVisible(true); final String value = values[i];
                    color.setOnClickListener(v -> inputs.get(0).setText(value)); colors.addView(color);
                }
                content.addView(colors); break;
            case HASH: field("话题名称", selected(), text); break;
            case MENTION: field("完整用户名", selected().replaceFirst("^@", ""), text); break;
            case COLLAPSE: field("折叠区标题", "展开内容", text); break;
            case REFERENCE:
                options("作品", "章节", "评论", "频道", "用户");
                field("引用 ID", "", InputType.TYPE_CLASS_NUMBER); field("说明（可选）", selected(), text); break;
            case TABLE:
                field("行数（1–20）", "3", InputType.TYPE_CLASS_NUMBER); field("列数（1–10）", "3", InputType.TYPE_CLASS_NUMBER);
                headerRow = new MaterialCheckBox(content.getContext()); headerRow.setId(R.id.editorFormatHeaderRow);
                headerRow.setText("首行为表头"); headerRow.setChecked(restoredHeader); content.addView(headerRow);
                TextView dimensions = new TextView(content.getContext()); dimensions.setId(R.id.editorTableDimensions);
                dimensions.setText("3 行 × 3 列"); dimensions.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
                content.addView(dimensions);
                TableSizePicker picker = new TableSizePicker(content.getContext()); picker.setId(R.id.editorTableSizePicker);
                content.addView(picker, new LinearLayout.LayoutParams(-1, -2));
                picker.setListener((rows, columns) -> { inputs.get(0).setText(String.valueOf(rows)); inputs.get(1).setText(String.valueOf(columns)); });
                android.text.TextWatcher resize = new android.text.TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                        try {
                            int rows = Integer.parseInt(value(0)), columns = Integer.parseInt(value(1));
                            if (rows >= 1 && rows <= 20 && columns >= 1 && columns <= 10) {
                                picker.setSelection(rows, columns); dimensions.setText(rows + " 行 × " + columns + " 列");
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                    @Override public void afterTextChanged(android.text.Editable s) {}
                };
                inputs.get(0).addTextChangedListener(resize); inputs.get(1).addTextChangedListener(resize);
                break;
            default: break;
        }
    }
    private String value(int index) { return inputs.get(index).getText().toString(); }
    private void insert() {
        if (lookup != null) return;
        try {
            switch (format) {
                case LINK:
                    String label = value(1);
                    if (editingLink()) label = label.equals(requireArguments().getString("link_text"))
                            ? requireArguments().getString("link_content") : BbCodeInsertion.literal(label);
                    apply(BbCodeInsertion.link(value(0), label)); break;
                case IMAGE: apply(BbCodeInsertion.image(value(0), value(1), value(2), value(3))); break;
                case COLOR: case BACKGROUND:
                    String color = value(0).trim();
                    if (BbCodeRendering.cssColor(color) == null) throw new IllegalArgumentException("请输入有效颜色，例如 #E53935");
                    apply(BbCodeInsertion.wrap((format == EditorFormat.COLOR ? "color=" : "bg-color=") + BbCodeInsertion.attribute(color), selected(), false)); break;
                case HASH:
                    String name = value(0).trim().replaceAll("^#+|#+$", "");
                    if (name.isEmpty()) throw new IllegalArgumentException("请填写话题名称");
                    apply(BbCodeInsertion.atom("[hash]" + BbCodeInsertion.literal(name) + "[/hash]", false)); break;
                case COLLAPSE:
                    String title = value(0).trim();
                    if (title.isEmpty()) throw new IllegalArgumentException("请填写折叠区标题");
                    apply(BbCodeInsertion.wrap("collapse=" + BbCodeInsertion.attribute(title), selected(), true)); break;
                case REFERENCE:
                    apply(BbCodeInsertion.reference(new int[]{1, 3, 4, 5, 7}[choice.getSelectedItemPosition()], Integer.parseInt(value(0)), value(1))); break;
                case TABLE: apply(BbCodeInsertion.table(Integer.parseInt(value(0)), Integer.parseInt(value(1)), headerRow.isChecked(), selected())); break;
                case MENTION: mention(); break;
                default: break;
            }
        } catch (NumberFormatException e) { error("请填写有效的整数"); }
        catch (IllegalArgumentException e) { error(e.getMessage()); }
    }
    private void mention() {
        String username = value(0).trim().replaceFirst("^@", "");
        if (username.isEmpty()) { error("请填写完整用户名"); return; }
        error(""); loading(true);
        Call<UserDetailResponse> request = RetrofitClient.getInstance().getUserDetail(username); lookup = request;
        request.enqueue(new Callback<UserDetailResponse>() {
            @Override public void onResponse(Call<UserDetailResponse> call, Response<UserDetailResponse> response) {
                if (lookup != call || !isAdded() || root == null) return;
                lookup = null; loading(false);
                UserDetailResponse user = response.body();
                if (response.isSuccessful() && user != null && user.getId() > 0 && user.getUserName() != null)
                    apply(BbCodeInsertion.mention(user.getId(), user.getUserName()));
                else error(response.isSuccessful() ? "未找到此用户" : ApiErrors.message(response));
            }
            @Override public void onFailure(Call<UserDetailResponse> call, Throwable t) {
                if (lookup != call || !isAdded() || root == null) return;
                lookup = null; loading(false); error("无法查询用户，请重试");
            }
        });
    }
    private void apply(BbCodeInsertion.Fragment fragment) {
        if (applied) return;
        if (!host().canInsertFormat() || host().formatVersion() != requireArguments().getInt("version")) {
            error("正文状态已变化，请关闭面板后重新选择"); return;
        }
        BbCodeEditText body = host().formatBody();
        int start = requireArguments().getInt("start"), end = requireArguments().getInt("end");
        if (end > body.length()) { error("正文已变化，请重新选择插入位置"); return; }
        if (editingLink() && !body.getText().subSequence(start, end).toString().equals(requireArguments().getString("link_source"))) {
            error("链接已变化，请关闭面板后重新选择"); return;
        }
        applied = true;
        host().applyFormat(BbCodeInsertion.at(body.getText().toString(), start, end, fragment)); dismiss();
    }
    private void error(String message) {
        TextView view = root.findViewById(R.id.editorFormatError);
        view.setText(message); view.setVisibility(message == null || message.isEmpty() ? View.GONE : View.VISIBLE);
    }
    private void loading(boolean loading) {
        root.findViewById(R.id.editorFormatProgress).setVisibility(loading ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.editorFormatInsert).setEnabled(!loading);
        for (EditText input : inputs) input.setEnabled(!loading);
    }
    private void cancelLookup() { if (lookup != null) lookup.cancel(); lookup = null; if (root != null) loading(false); }
    @Override public void onSaveInstanceState(@NonNull Bundle out) {
        if (format != null) out.putString("format", format.name());
        String[] values = new String[inputs.size()]; for (int i = 0; i < inputs.size(); i++) values[i] = value(i);
        out.putStringArray("values", values); out.putInt("choice", choice == null ? 0 : choice.getSelectedItemPosition());
        out.putBoolean("header", headerRow == null || headerRow.isChecked()); super.onSaveInstanceState(out);
    }
    @Override public void onDestroyView() { cancelLookup(); root = null; content = null; inputs.clear(); super.onDestroyView(); }
}
