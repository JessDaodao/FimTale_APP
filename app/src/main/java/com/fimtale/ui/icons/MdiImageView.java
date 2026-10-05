package com.fimtale.ui.icons;

import android.content.Context;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatImageView;
import com.fimtale.R;
import com.mikepenz.iconics.IconicsDrawable;

public class MdiImageView extends AppCompatImageView {
    public MdiImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        IconicsDrawable icon = MdiViewAttributes.read(getContext(), attrs, R.attr.mdiIcon);
        if (icon != null) setImageDrawable(icon);
    }
}
