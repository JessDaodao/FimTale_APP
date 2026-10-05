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
        } else if (getEndIconMode() == END_ICON_PASSWORD_TOGGLE) {
            android.graphics.drawable.StateListDrawable visibility = new android.graphics.drawable.StateListDrawable();
            visibility.addState(new int[]{android.R.attr.state_checked}, MdiIcons.drawable(getContext(), "eye"));
            visibility.addState(new int[]{}, MdiIcons.drawable(getContext(), "eye-off"));
            setEndIconDrawable(visibility);
        }
    }
}
