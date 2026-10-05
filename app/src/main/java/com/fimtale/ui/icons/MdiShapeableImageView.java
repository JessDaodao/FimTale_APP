package com.fimtale.ui.icons;

import android.content.Context;
import android.util.AttributeSet;
import com.fimtale.R;
import com.google.android.material.imageview.ShapeableImageView;
import com.mikepenz.iconics.IconicsDrawable;

public class MdiShapeableImageView extends ShapeableImageView {
    public MdiShapeableImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        IconicsDrawable icon = MdiViewAttributes.read(getContext(), attrs, R.attr.mdiIcon);
        if (icon != null) setImageDrawable(icon);
    }
}
