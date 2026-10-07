package com.fimtale.editor;

/** JNI boundary shared by the Android editor and host tests. */
public final class BbCodeNative {
    static { System.loadLibrary("fimtale"); }
    private BbCodeNative() {}

    public static native int[] parse(String source);
}
