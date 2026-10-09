package com.fimtale.crash;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import com.fimtale.R;
import com.fimtale.network.ApiErrors;
import com.fimtale.network.FimTaleApiService;
import com.fimtale.network.RetrofitClient;
import com.fimtale.report.ReportRequest;
import com.fimtale.utils.UserPreferences;
import java.io.IOException;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** A single explicit submission survives rotation; an uncertain POST is never replayed automatically. */
public final class CrashFeedbackViewModel extends AndroidViewModel {
    public final MutableLiveData<Integer> changes = new MutableLiveData<>(0);
    public final CrashReport report;
    public boolean submitting, needsLogin, complete, sent, successAnnounced;
    public String error = "";
    private final SavedStateHandle saved;
    private final CrashStore store;
    private final FimTaleApiService api;
    private String session;
    private Call<ReportRequest.Result> request;

    public CrashFeedbackViewModel(@NonNull Application app, SavedStateHandle saved) {
        this(app, saved, new CrashStore(app), RetrofitClient.getInstance());
    }
    CrashFeedbackViewModel(Application app, SavedStateHandle saved, CrashStore store, FimTaleApiService api) {
        super(app); this.saved = saved; this.store = store; this.api = api;
        CrashReport pending = store.pending();
        report = pending != null && pending.id.equals(saved.get("crash_id")) ? pending : null;
        complete = report == null;
        if (report != null && report.deliveryUncertain) error = app.getString(R.string.crash_feedback_uncertain);
        session = UserPreferences.getToken(app); needsLogin = session.isEmpty();
    }
    public String description() { String text = saved.get("description"); return text == null ? "" : text; }
    public void setDescription(String text) { saved.set("description", CrashReport.limit(text, 2000)); }
    public boolean detailsVisible() { return Boolean.TRUE.equals(saved.get("details")); }
    public void toggleDetails() { saved.set("details", !detailsVisible()); changed(); }
    private void changed() { changes.setValue(changes.getValue() + 1); }
    private boolean current() { return session.equals(UserPreferences.getToken(getApplication())); }

    public void connect() {
        String next = UserPreferences.getToken(getApplication());
        if (!next.equals(session)) {
            boolean wasSubmitting = submitting; cancel(); session = next;
            if (wasSubmitting) error = getApplication().getString(R.string.crash_feedback_session_changed);
        }
        needsLogin = session.isEmpty(); changed();
    }
    public void decline() {
        if (submitting || complete) return;
        try { store.acknowledge(report.id); } catch (IOException | RuntimeException ignored) {}
        complete = true; changed();
    }
    public void submit() {
        if (submitting || complete) return;
        if (!current()) { connect(); return; }
        if (needsLogin) return;
        try { store.setDeliveryUncertain(report.id, true); }
        catch (IOException | RuntimeException failure) { error = getApplication().getString(R.string.crash_feedback_storage_error); changed(); return; }
        submitting = true; error = ""; changed();
        request = api.sendCrashFeedback(session, new CrashFeedbackRequest(report, description()));
        request.enqueue(new Callback<ReportRequest.Result>() {
            @Override public void onResponse(Call<ReportRequest.Result> call, Response<ReportRequest.Result> response) {
                if (request != call) return;
                if (!current()) { connect(); return; }
                request = null; submitting = false;
                if (!response.isSuccessful()) clearUncertainDelivery();
                if (response.code() == 401) {
                    UserPreferences.clearSession(getApplication()); connect(); return;
                }
                if (response.isSuccessful() && response.body() != null && response.body().id > 0) {
                    try { store.acknowledge(report.id); } catch (IOException | RuntimeException ignored) {}
                    sent = true; complete = true;
                } else error = response.isSuccessful() ? getApplication().getString(R.string.crash_feedback_uncertain) : ApiErrors.message(response);
                changed();
            }
            @Override public void onFailure(Call<ReportRequest.Result> call, Throwable failure) {
                if (request != call) return;
                if (!current()) { connect(); return; }
                request = null; submitting = false;
                error = getApplication().getString(R.string.crash_feedback_uncertain); changed();
            }
        });
    }
    private void clearUncertainDelivery() {
        try { store.setDeliveryUncertain(report.id, false); } catch (IOException | RuntimeException ignored) {}
    }
    private void cancel() {
        if (request != null) { request.cancel(); request = null; }
        submitting = false;
    }
    @Override protected void onCleared() { cancel(); }
}
