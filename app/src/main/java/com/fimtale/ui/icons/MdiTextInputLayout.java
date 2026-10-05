package com.fimtale.ui.icons;

import android.content.Context;
import android.util.AttributeSet;
import com.fimtale.utils.MdiIcons;
import com.google.android.material.textfield.TextInputLayout;

public class MdiTextInputLayout extends TextInputLayout {
    public MdiTextInputLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        setErrorIconDrawable(MdiIcons.drawable(getContext(), "alert-circle"));
        if (getEndIconMode() == END_ICON_CLEAR_TEXT) {
            setEndIconDrawable(MdiIcons.drawable(getContext(), "close-circle"));
        }
    }
}
