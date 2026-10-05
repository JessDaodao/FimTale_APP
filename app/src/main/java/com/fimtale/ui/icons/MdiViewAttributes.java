package com.fimtale.ui.icons;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import com.fimtale.R;
import com.fimtale.utils.MdiIcons;
import com.mikepenz.iconics.IconicsDrawable;

/** Reads font icon names, never vector paths, from layout attributes. */
final class MdiViewAttributes {
    private MdiViewAttributes() {}

    static IconicsDrawable read(Context context, AttributeSet attrs, int attribute) {
        TypedArray values = context.obtainStyledAttributes(attrs, new int[]{attribute, R.attr.mdiIconSize});
        try {
            String name = values.getString(0);
            if (name == null || name.isEmpty()) return null;
            IconicsDrawable icon = MdiIcons.drawable(context, name);
            int size = values.getDimensionPixelSize(1, icon.getIntrinsicWidth());
            icon.setSizeXPx(size);
            icon.setSizeYPx(size);
            return icon;
        } finally {
            values.recycle();
        }
    }
}
