package com.fimtale.ui.icons;

import android.content.Context;
import android.util.AttributeSet;
import com.fimtale.R;
import com.google.android.material.textview.MaterialTextView;
import com.mikepenz.iconics.IconicsDrawable;

public class MdiTextView extends MaterialTextView {
    public MdiTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        IconicsDrawable start = MdiViewAttributes.read(getContext(), attrs, R.attr.mdiDrawableStart);
        IconicsDrawable top = MdiViewAttributes.read(getContext(), attrs, R.attr.mdiDrawableTop);
        if (start != null) start.setColorList(getTextColors());
        if (top != null) top.setColorList(getTextColors());
        setCompoundDrawablesRelativeWithIntrinsicBounds(start, top, null, null);
    }
}
