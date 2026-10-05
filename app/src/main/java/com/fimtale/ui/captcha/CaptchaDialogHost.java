package com.fimtale.ui.captcha;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

/** Connect a retained screen state to the shared dialog; call sync after rendering and onPostResume. */
public final class CaptchaDialogHost {
    private final FragmentActivity activity;
    private final String requestKey, tag;
    private final BooleanSupplier requested;
    private boolean showPending;

    public CaptchaDialogHost(FragmentActivity activity, String requestKey, BooleanSupplier requested,
            BiConsumer<String, String> callback) {
        this.activity = activity;
        this.requestKey = requestKey;
        this.tag = CaptchaDialogFragment.RESULT_KEY.equals(requestKey) ? CaptchaDialogFragment.TAG : requestKey + ".dialog";
        this.requested = requested;
        activity.getSupportFragmentManager().setFragmentResultListener(requestKey, activity,
                (key, result) -> callback.accept(result.getString(CaptchaDialogFragment.TOKEN),
                        result.getString(CaptchaDialogFragment.PROVIDER)));
    }

    public void sync() {
        FragmentManager fragments = activity.getSupportFragmentManager();
        if (activity.isFinishing() || activity.isDestroyed() || fragments.isStateSaved()) return;
        Fragment existing = fragments.findFragmentByTag(tag);
        if (requested.getAsBoolean() && existing == null && !showPending) {
            showPending = true;
            CaptchaDialogFragment.newInstance(requestKey).show(fragments.beginTransaction()
                    .runOnCommit(() -> { showPending = false; sync(); }), tag);
        } else if (!requested.getAsBoolean() && existing instanceof CaptchaDialogFragment) {
            ((CaptchaDialogFragment) existing).dismiss();
        }
    }
}
