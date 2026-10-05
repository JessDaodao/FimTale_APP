package com.fimtale.review;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import com.fimtale.R;
import com.fimtale.model.UserAuth;
import com.fimtale.network.ApiErrors;
import com.fimtale.network.FimTaleApiService;
import com.fimtale.network.RetrofitClient;
import com.fimtale.utils.UserPreferences;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Retains filters, dialog drafts and in-flight actions across configuration changes. */
public class ReviewQueueViewModel extends AndroidViewModel {
    public enum Mode { ASSIGNED, PENDING, HISTORY }
    public enum Action { NONE, PASS, REJECT, ASSIGN }
    public static final int PER_PAGE = 20;
    public final MutableLiveData<Integer> changes = new MutableLiveData<>(0);
    public final List<ReviewEntry> reviews = new ArrayList<>();
    public final List<ReviewEntry.Reviewer> reviewers = new ArrayList<>();
    public final Map<Integer, String> names = new LinkedHashMap<>();
    public final Set<Integer> selectedReviewers = new LinkedHashSet<>();
    public Mode mode = Mode.ASSIGNED;
    public Action action = Action.NONE;
    public Integer statusFilter, workFilter, reviewFilter;
    public int page = 1;
    public boolean initialized, loading, loadingReviewers, mutating, uncertain, hasNext, needsLogin;
    public String error = "", dialogError = "", reason = "", notice;
    public Long resubmitAfter;
    public ReviewEntry selected;
    public UserAuth auth;
    private String session = "";
    private boolean authorized, reviewersLoaded;
    private int requestedPage = 1;
    private final FimTaleApiService api = RetrofitClient.getInstance();
    private Call<?> authCall, listCall, mutationCall, teamCall;
    private final Map<Integer, Call<String>> nameCalls = new LinkedHashMap<>();

    public ReviewQueueViewModel(@NonNull Application application) { super(application); }
    public boolean canReview() { return authorized && auth != null && auth.canReview(); }
    public boolean canAssign() { return canReview() && auth.canManageReviews(); }
    public boolean canAct(ReviewEntry item) {
        return canReview() && !loading && !mutating && !uncertain && mode != Mode.HISTORY
                && item != null && item.status == ReviewEntry.PENDING;
    }
    private boolean current() { return !session.isEmpty() && session.equals(UserPreferences.getToken(getApplication())); }
    private void changed() { changes.setValue(changes.getValue() + 1); }

    public void connect() {
        String token = UserPreferences.getToken(getApplication());
        if (!token.equals(session)) {
            boolean switchedAccount = !session.isEmpty();
            cancelAll(); session = token; authorized = false; auth = null;
            reviews.clear(); reviewers.clear(); names.clear(); reviewersLoaded = false;
            action = Action.NONE; selected = null; selectedReviewers.clear();
            mutating = false; uncertain = false; loading = false; loadingReviewers = false; hasNext = false;
            if (switchedAccount) page = 1;
        }
        needsLogin = token.isEmpty();
        if (needsLogin) { error = getApplication().getString(R.string.review_login_required); changed(); return; }
        if (authCall != null || canReview()) return;
        loading = true; error = ""; changed();
        Call<UserAuth> request = api.getUserAuth(session); authCall = request;
        request.enqueue(new Callback<UserAuth>() {
            @Override public void onResponse(Call<UserAuth> call, Response<UserAuth> response) {
                if (authCall != call) return;
                if (!current()) { connect(); return; }
                authCall = null; loading = false;
                if (response.isSuccessful() && response.body() != null && response.body().canReview()) {
                    auth = response.body(); authorized = true; load(page);
                } else {
                    authorized = false;
                    if (response.code() == 401) needsLogin = true;
                    error = response.isSuccessful() ? getApplication().getString(R.string.review_no_permission) : ApiErrors.message(response);
                    changed();
                }
            }
            @Override public void onFailure(Call<UserAuth> call, Throwable t) {
                if (authCall != call) return;
                if (!current()) { connect(); return; }
                authCall = null; loading = false; error = getApplication().getString(R.string.review_load_failed); changed();
            }
        });
    }

    public void refresh() { if (mutating) return; if (canReview()) load(page); else connect(); }
    public void retry() { if (canReview()) load(requestedPage); else connect(); }
    public void chooseMode(Mode next) {
        if (mutating || mode == next) return;
        mode = next; resetAndLoad();
    }
    public void chooseStatus(Integer next) {
        if (mutating || java.util.Objects.equals(next, statusFilter)) return;
        statusFilter = next; resetAndLoad();
    }
    public void clearFilter() { if (!mutating) { workFilter = null; reviewFilter = null; resetAndLoad(); } }
    private void resetAndLoad() { page = 1; reviews.clear(); hasNext = false; load(1); }
    public void previousPage() { if (!loading && !mutating && page > 1) load(page - 1); }
    public void nextPage() { if (!loading && !mutating && hasNext) load(page + 1); }

    private void load(int targetPage) {
        if (!current()) { connect(); return; }
        if (!canReview() || mutating) return;
        if (listCall != null) listCall.cancel();
        loading = true; error = ""; requestedPage = targetPage; changed();
        Integer status = null;
        if (reviewFilter == null) status = mode == Mode.HISTORY ? statusFilter : Integer.valueOf(ReviewEntry.PENDING);
        Integer assignee = reviewFilter == null && mode == Mode.ASSIGNED ? auth.userId : null;
        Call<List<ReviewEntry>> request = api.getReviews(session, targetPage, PER_PAGE, status, assignee,
                reviewFilter == null ? workFilter : null, reviewFilter);
        listCall = request;
        request.enqueue(new Callback<List<ReviewEntry>>() {
            @Override public void onResponse(Call<List<ReviewEntry>> call, Response<List<ReviewEntry>> response) {
                if (listCall != call) return;
                if (!current()) { connect(); return; }
                listCall = null; loading = false;
                if (response.isSuccessful()) {
                    reviews.clear();
                    if (response.body() != null) for (ReviewEntry item : response.body()) if (item != null && item.id > 0) reviews.add(item);
                    page = targetPage; hasNext = response.body() != null && response.body().size() == PER_PAGE;
                    uncertain = false; changed(); hydrateNames();
                } else { denied(response); error = ApiErrors.message(response); changed(); }
            }
            @Override public void onFailure(Call<List<ReviewEntry>> call, Throwable t) {
                if (listCall != call) return;
                if (!current()) { connect(); return; }
                listCall = null; loading = false; error = getApplication().getString(R.string.review_load_failed); changed();
            }
        });
    }

    public void open(Action next, ReviewEntry item) {
        if (!canAct(item) || action != Action.NONE || next == Action.NONE || (next == Action.ASSIGN && !canAssign())) return;
        selected = item; action = next; reason = ""; resubmitAfter = null; dialogError = ""; selectedReviewers.clear();
        if (item.assignments != null) for (ReviewEntry.Assignment a : item.assignments) if (a != null && a.reviewerId > 0) selectedReviewers.add(a.reviewerId);
        changed();
        if (next == Action.ASSIGN) fetchReviewers();
    }
    public void closeDialog() {
        if (mutating) return;
        action = Action.NONE; selected = null; dialogError = ""; changed();
    }
    public void setResubmitAfter(Long instant) { resubmitAfter = instant; changed(); }
    public void fetchReviewers() {
        if (!current() || !canAssign() || loadingReviewers || reviewersLoaded) return;
        loadingReviewers = true; dialogError = ""; changed();
        Call<List<ReviewEntry.Reviewer>> request = api.getReviewers(session); teamCall = request;
        request.enqueue(new Callback<List<ReviewEntry.Reviewer>>() {
            @Override public void onResponse(Call<List<ReviewEntry.Reviewer>> call, Response<List<ReviewEntry.Reviewer>> response) {
                if (teamCall != call) return;
                if (!current()) { connect(); return; }
                teamCall = null; loadingReviewers = false;
                if (response.isSuccessful()) {
                    reviewers.clear();
                    if (response.body() != null) for (ReviewEntry.Reviewer member : response.body())
                        if (member != null && member.userId > 0 && member.roleId >= 2) reviewers.add(member);
                    reviewersLoaded = true;
                } else { dialogError = ApiErrors.message(response); denied(response); }
                changed();
            }
            @Override public void onFailure(Call<List<ReviewEntry.Reviewer>> call, Throwable t) {
                if (teamCall != call) return;
                if (!current()) { connect(); return; }
                teamCall = null; loadingReviewers = false; dialogError = getApplication().getString(R.string.review_team_failed); changed();
            }
        });
    }
    public boolean canConfirm() {
        return canReview() && current() && selected != null && action != Action.NONE && !mutating && !uncertain
                && (action != Action.ASSIGN || (canAssign() && reviewersLoaded && !loadingReviewers));
    }
    public void confirm() {
        if (!canConfirm()) return;
        mutating = true; dialogError = ""; changed();
        if (action == Action.ASSIGN) {
            mutate(api.setReviewAssignments(session, new ReviewEntry.Assign(selected.id, new ArrayList<>(selectedReviewers))));
        } else {
            int status = action == Action.PASS ? ReviewEntry.PASSED : ReviewEntry.REJECTED;
            mutate(api.resolveReview(session, new ReviewEntry.Resolve(selected.id, status,
                    status == ReviewEntry.REJECTED ? reason.trim() : null,
                    status == ReviewEntry.REJECTED && resubmitAfter != null ? Instant.ofEpochMilli(resubmitAfter).toString() : null)));
        }
    }
    private <T> void mutate(Call<T> request) {
        mutationCall = request;
        request.enqueue(new Callback<T>() {
            @Override public void onResponse(Call<T> call, Response<T> response) {
                if (mutationCall != call) return;
                if (!current()) { connect(); return; }
                mutationCall = null; mutating = false;
                if (response.isSuccessful()) {
                    notice = getApplication().getString(action == Action.PASS ? R.string.review_passed_notice
                            : action == Action.REJECT ? R.string.review_rejected_notice : R.string.review_assigned_notice);
                    action = Action.NONE; selected = null; changed(); load(page);
                } else { dialogError = ApiErrors.message(response); denied(response); changed(); }
            }
            @Override public void onFailure(Call<T> call, Throwable t) {
                if (mutationCall != call) return;
                if (!current()) { connect(); return; }
                mutationCall = null; mutating = false; uncertain = true;
                dialogError = getApplication().getString(R.string.review_action_uncertain); changed();
            }
        });
    }
    private void denied(Response<?> response) {
        if (response.code() != 401 && response.code() != 403) return;
        authorized = false; reviews.clear(); action = Action.NONE; selected = null;
        needsLogin = response.code() == 401;
        error = getApplication().getString(needsLogin ? R.string.review_login_required : R.string.review_no_permission);
    }
    private void hydrateNames() {
        for (ReviewEntry item : reviews) if (item.assignments != null) for (ReviewEntry.Assignment assignment : item.assignments)
            if (assignment != null && assignment.reviewerName != null && !assignment.reviewerName.isEmpty()) names.put(assignment.reviewerId, assignment.reviewerName);
        for (ReviewEntry item : reviews) {
            int id = item.resolver();
            if (id <= 0 || item.status == ReviewEntry.PENDING || names.containsKey(id) || nameCalls.containsKey(id)) continue;
            Call<String> request = api.getUsernameById(session, id); nameCalls.put(id, request);
            request.enqueue(new Callback<String>() {
                @Override public void onResponse(Call<String> call, Response<String> response) {
                    if (nameCalls.get(id) != call) return;
                    if (!current()) { connect(); return; }
                    nameCalls.remove(id);
                    if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                        names.put(id, response.body()); changed();
                    }
                }
                @Override public void onFailure(Call<String> call, Throwable t) { if (nameCalls.get(id) == call) nameCalls.remove(id); }
            });
        }
    }
    private void cancelAll() {
        for (Call<?> call : new Call<?>[]{authCall, listCall, mutationCall, teamCall}) if (call != null) call.cancel();
        authCall = listCall = mutationCall = teamCall = null;
        for (Call<String> call : nameCalls.values()) call.cancel();
        nameCalls.clear();
    }
    @Override protected void onCleared() { cancelAll(); }
}
