package com.fimtale;

import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.internal.bytecode.InstrumentationConfiguration;

/** Use one real JNI bridge across Robolectric's isolated Android class loaders. */
public final class NativeRobolectricTestRunner extends RobolectricTestRunner {
    public NativeRobolectricTestRunner(Class<?> testClass) throws InitializationError {
        super(testClass);
    }

    @Override protected InstrumentationConfiguration createClassLoaderConfig(FrameworkMethod method) {
        return new InstrumentationConfiguration.Builder(super.createClassLoaderConfig(method))
                .doNotAcquireClass("com.fimtale.editor.BbCodeNative")
                .build();
    }
}
