package com.fimtale.ui.icons;

import android.content.Context;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatImageButton;
import com.fimtale.R;
import com.mikepenz.iconics.IconicsDrawable;

public class MdiImageButton extends AppCompatImageButton {
    public MdiImageButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        IconicsDrawable icon = MdiViewAttributes.read(getContext(), attrs, R.attr.mdiIcon);
        if (icon != null) setImageDrawable(icon);
    }
}
