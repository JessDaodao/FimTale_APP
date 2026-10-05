package com.fimtale.ui.icons;

import android.content.Context;
import android.util.AttributeSet;
import com.fimtale.R;
import com.google.android.material.button.MaterialButton;
import com.mikepenz.iconics.IconicsDrawable;

public class MdiButton extends MaterialButton {
    public MdiButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        IconicsDrawable icon = MdiViewAttributes.read(getContext(), attrs, R.attr.mdiIcon);
        if (icon != null) setIcon(icon);
    }
}
