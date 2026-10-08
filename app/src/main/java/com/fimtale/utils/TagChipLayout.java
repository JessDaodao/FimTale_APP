package com.fimtale.utils;

import android.view.View;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

public final class TagChipLayout {
    private TagChipLayout() {}

    public static void alignHeights(ChipGroup group) {
        // ChipGroup uses the last chip's height for each row, so shorter labels can clip taller ones.
        int measureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        int height = 0;
        for (int i = 0; i < group.getChildCount(); i++) {
            View chip = group.getChildAt(i);
            chip.measure(measureSpec, measureSpec);
            height = Math.max(height, chip.getMeasuredHeight());
        }
        for (int i = 0; i < group.getChildCount(); i++) {
            ((Chip) group.getChildAt(i)).setMinHeight(height);
        }
    }
}
