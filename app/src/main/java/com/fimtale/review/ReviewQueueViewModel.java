package com.fimtale.review;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import com.fimtale.R;
import com.fimtale.network.ApiErrors;
import com.fimtale.network.FimTaleApiService;
import com.fimtale.network.RetrofitClient;
import com.fimtale.utils.UserPreferences;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** My reviews: the authenticated author endpoint determines ownership, without an editor-role gate. */
public class ReviewQueueViewModel extends AndroidViewModel {
    public enum Section { READY, PENDING, COMPLETED }
    public final MutableLiveData<Integer> changes = new MutableLiveData<>(0);
    public final List<ReviewEntry> reviews = new ArrayList<>();
    public boolean loading, mutating, uncertain, needsLogin, loaded;
    public String error = "", dialogError = "", notice;
    public ReviewEntry selected;
    private String session = "";
    private final FimTaleApiService api = RetrofitClient.getInstance();
    private Call<List<ReviewEntry>> listCall;
    private Call<ReviewEntry> submissionCall;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable eligibilityUpdate = () -> { changed(); scheduleEligibility(); };

    public ReviewQueueViewModel(@NonNull Application application) { super(application); }
    public static Section section(ReviewEntry entry, long now) {
        if (entry.status == ReviewEntry.PENDING) return Section.PENDING;
        if (entry.status == ReviewEntry.PASSED) return Section.COMPLETED;
        // Rejected works stay with submissions while their resubmission deadline is pending.
        return Section.READY;
    }
    private boolean current() { return !session.isEmpty() && session.equals(UserPreferences.getToken(getApplication())); }
    private void changed() { changes.setValue(changes.getValue() + 1); }

    public void connect() {
        String token = UserPreferences.getToken(getApplication());
        if (!token.equals(session)) {
            cancelAll(); session = token;
            reviews.clear(); selected = null; loaded = false;
            loading = false; mutating = false; uncertain = false; dialogError = ""; notice = null;
        }
        needsLogin = token.isEmpty();
        if (needsLogin) {
            error = getApplication().getString(R.string.review_login_required); changed(); return;
        }
        if (!loaded && listCall == null && !mutating) load();
        else { changed(); scheduleEligibility(); }
    }
    public void refresh() {
        if (mutating) return;
        if (!current()) { connect(); return; }
        selected = null; dialogError = ""; load();
    }
    public boolean canSubmit(ReviewEntry entry) {
        return current() && !needsLogin && !loading && !mutating && !uncertain
                && entry != null && reviews.contains(entry) && entry.workId > 0 && entry.canSubmit(System.currentTimeMillis());
    }
    private void load() {
        if (!current()) { connect(); return; }
        if (listCall != null) listCall.cancel();
        loading = true; error = ""; changed();
        Call<List<ReviewEntry>> request = api.getReviewEntries(session); listCall = request;
        request.enqueue(new Callback<List<ReviewEntry>>() {
            @Override public void onResponse(Call<List<ReviewEntry>> call, Response<List<ReviewEntry>> response) {
                if (listCall != call) return;
                if (!current()) { connect(); return; }
                listCall = null; loading = false;
                if (response.code() == 401) { expire(); return; }
                if (response.isSuccessful()) {
                    reviews.clear();
                    if (response.body() != null) for (ReviewEntry entry : response.body())
                        if (entry != null && entry.workId > 0) reviews.add(entry);
                    loaded = true; uncertain = false; scheduleEligibility();
                } else {
                    if (response.code() == 403) reviews.clear();
                    error = ApiErrors.message(response);
                }
                changed();
            }
            @Override public void onFailure(Call<List<ReviewEntry>> call, Throwable t) {
                if (listCall != call) return;
                if (!current()) { connect(); return; }
                listCall = null; loading = false; error = getApplication().getString(R.string.review_load_failed); changed();
            }
        });
    }
    public void open(ReviewEntry entry) {
        if (selected != null || !canSubmit(entry)) return;
        selected = entry; dialogError = ""; changed();
    }
    public void closeDialog() { if (!mutating) { selected = null; dialogError = ""; changed(); } }
    public boolean canConfirm() { return canSubmit(selected); }
    public void confirm() {
        if (!canConfirm()) return;
        ReviewEntry submitting = selected;
        mutating = true; dialogError = ""; changed();
        Call<ReviewEntry> request = api.submitReview(session, new ReviewEntry.Submit(submitting.workId));
        submissionCall = request;
        request.enqueue(new Callback<ReviewEntry>() {
            @Override public void onResponse(Call<ReviewEntry> call, Response<ReviewEntry> response) {
                if (submissionCall != call) return;
                if (!current()) { connect(); return; }
                submissionCall = null; mutating = false;
                if (response.code() == 401) { expire(); return; }
                if (response.isSuccessful()) {
                    // A failed refresh must not allow the stale row to submit an already queued work again.
                    for (ReviewEntry entry : reviews) if (entry.workId == submitting.workId) entry.status = ReviewEntry.PENDING;
                    selected = null; notice = getApplication().getString(R.string.review_submitted_notice);
                    load();
                } else { dialogError = ApiErrors.message(response); changed(); }
            }
            @Override public void onFailure(Call<ReviewEntry> call, Throwable t) {
                if (submissionCall != call) return;
                if (!current()) { connect(); return; }
                submissionCall = null; mutating = false; uncertain = true;
                dialogError = getApplication().getString(R.string.review_action_uncertain); changed();
            }
        });
    }
    private void expire() { UserPreferences.clearSession(getApplication()); connect(); }
    private void scheduleEligibility() {
        handler.removeCallbacks(eligibilityUpdate);
        long now = System.currentTimeMillis(), next = Long.MAX_VALUE;
        for (ReviewEntry entry : reviews) {
            Long after = entry.resubmitTime();
            if (entry.status == ReviewEntry.REJECTED && after != null && after >= now && after < next) next = after;
        }
        if (next != Long.MAX_VALUE) handler.postDelayed(eligibilityUpdate, Math.min(next - now + 1, 86_400_000L));
    }
    private void cancelAll() {
        if (listCall != null) listCall.cancel();
        if (submissionCall != null) submissionCall.cancel();
        listCall = null; submissionCall = null;
        handler.removeCallbacks(eligibilityUpdate);
    }
    @Override protected void onCleared() { cancelAll(); }
}
