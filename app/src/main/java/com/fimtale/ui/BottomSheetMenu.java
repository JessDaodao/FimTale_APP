package com.fimtale.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.MenuRes;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.view.ViewCompat;
import com.fimtale.R;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

/** Text-only actions using the same spacing and bottom surface as the work details menu. */
public final class BottomSheetMenu extends BottomSheetDialog {
    private final Menu menu;
    private final LinearLayout items;
    private final MenuItem.OnMenuItemClickListener listener;

    public BottomSheetMenu(Context context, @MenuRes int resource, MenuItem.OnMenuItemClickListener listener) {
        super(context);
        this.listener = listener;
        setContentView(R.layout.dialog_bottom_menu);
        items = findViewById(R.id.bottomMenuItems);
        // Use the platform menu model for XML visibility/enabled state; only the sheet is shown.
        PopupMenu definition = new PopupMenu(getContext(), items);
        definition.inflate(resource);
        menu = definition.getMenu();
        View surface = findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (surface != null) surface.setBackgroundResource(R.drawable.bg_bottom_sheet_rounded);
        ViewCompat.setAccessibilityPaneTitle(items, "更多");
        getBehavior().setSkipCollapsed(true);
    }

    public Menu getMenu() { return menu; }

    /** Hosts call this when permissions or loading state change while the menu is open. */
    public void refresh() {
        items.removeAllViews();
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (!item.isVisible()) continue;
            TextView row = (TextView) LayoutInflater.from(getContext()).inflate(R.layout.item_bottom_menu_action, items, false);
            row.setId(item.getItemId()); row.setText(item.getTitle());
            row.setEnabled(item.isEnabled()); row.setAlpha(item.isEnabled() ? 1f : .38f);
            row.setOnClickListener(view -> {
                if (!item.isVisible() || !item.isEnabled()) return;
                dismiss();
                listener.onMenuItemClick(item);
            });
            items.addView(row);
        }
    }

    @Override public void show() {
        if (isShowing()) return;
        refresh(); super.show();
        getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
    }
}
