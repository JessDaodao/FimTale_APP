package com.fimtale.ui.icons;

import android.content.Context;
import android.util.AttributeSet;
import com.fimtale.R;
import com.fimtale.utils.MdiIcons;
import com.google.android.material.appbar.MaterialToolbar;
import com.mikepenz.iconics.IconicsDrawable;

public class MdiToolbar extends MaterialToolbar {
    public MdiToolbar(Context context) {
        this(context, null);
    }

    public MdiToolbar(Context context, AttributeSet attrs) {
        super(context, attrs);
        IconicsDrawable navigation = MdiViewAttributes.read(getContext(), attrs, R.attr.mdiNavigationIcon);
        if (navigation != null) setNavigationIcon(navigation);
        setOverflowIcon(MdiIcons.drawable(getContext(), "dots-vertical"));
    }

    @Override public void inflateMenu(int menuRes) {
        super.inflateMenu(menuRes);
        MdiIcons.applyMenu(getContext(), getMenu(), menuRes);
    }
}
