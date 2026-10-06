package com.fimtale.report;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import com.fimtale.model.CurrentUser;
import com.fimtale.network.*;
import com.fimtale.utils.UserPreferences;
import java.util.*;
import retrofit2.*;

/** Only the authenticated reporter's records; requests never omit the owner filter. */
public final class MyReportsViewModel extends AndroidViewModel {
    public static final int PER_PAGE = 20;
    public final MutableLiveData<Integer> changes = new MutableLiveData<>(0);
    public final List<MyReport> reports = new ArrayList<>();
    public boolean loading, submitting, loaded, needsLogin, uncertain;
    public String error = "", replyError = "", notice;
    public int status, page = 1;
    public long reportId;
    public MyReport selected;
    public boolean detail;
    private long owner;
    private String session = "";
    private final SavedStateHandle saved;
    private final FimTaleApiService api;
    private Call<?> readCall;
    private Call<MyReport> replyCall;
    private int requestedPage = 1;

    public MyReportsViewModel(@NonNull Application app, SavedStateHandle saved) { this(app, saved, RetrofitClient.getInstance()); }
    MyReportsViewModel(Application app, SavedStateHandle saved, FimTaleApiService api) {
        super(app); this.saved = saved; this.api = api;
        Integer filter = saved.get("status"), currentPage = saved.get("page");
        status = filter == null ? 0 : Math.max(0, Math.min(3, filter));
        page = currentPage == null ? 1 : Math.max(1, currentPage); requestedPage = page;
        Long id = saved.get("reportId"); reportId = id == null ? 0 : id;
        detail = Boolean.TRUE.equals(saved.get("detail"));
        if (Boolean.TRUE.equals(saved.get("submitting"))) { uncertain = true; replyError = "发送结果未确认，请刷新报告核对消息记录"; }
        saved.set("submitting", false);
    }
    public void initialize(long id) {
        if (Boolean.TRUE.equals(saved.get("initialized"))) return;
        saved.set("initialized", true);
        if (id > 0) { reportId = id; detail = true; persist(); }
    }
    public void initializeDetail(long id) {
        if (id <= 0) return;
        saved.set("fixedReportId", id); initialize(id); restoreDetailTarget();
    }
    private void restoreDetailTarget() {
        Long id = saved.get("fixedReportId");
        if (id != null && id > 0) { reportId = id; detail = true; persist(); }
    }
    private void changed() { changes.setValue(changes.getValue() + 1); }
    private void persist() { saved.set("status", status); saved.set("page", page); saved.set("reportId", reportId); saved.set("detail", detail); }
    private boolean current() { return !session.isEmpty() && session.equals(UserPreferences.getToken(getApplication())); }
    public void connect() {
        String next = UserPreferences.getToken(getApplication());
        if (!next.equals(session)) {
            boolean hadSession = !session.isEmpty(); cancel(); session = next; owner = 0;
            reports.clear(); selected = null; loaded = false; error = "";
            if (hadSession) { saved.remove("draft"); saved.remove("draftReport"); detail = false; reportId = 0; page = 1; requestedPage = 1; replyError = ""; uncertain = false; persist(); }
        }
        restoreDetailTarget();
        needsLogin = session.isEmpty();
        if (needsLogin) { changed(); return; }
        if (owner == 0 && readCall == null) identify();
        else if (!loaded && readCall == null) load(page);
        else changed();
    }
    private void identify() {
        loading = true; error = ""; changed();
        Call<CurrentUser> call = api.getCurrentUser(session); readCall = call;
        call.enqueue(new Callback<CurrentUser>() {
            @Override public void onResponse(Call<CurrentUser> c, Response<CurrentUser> response) {
                if (!acceptRead(c)) return;
                if (response.code() == 401) { expire(); return; }
                if (response.isSuccessful() && response.body() != null && response.body().id > 0) {
                    owner = response.body().id;
                    Long previousOwner = saved.get("owner");
                    if (previousOwner != null && previousOwner != owner) {
                        saved.remove("draft"); saved.remove("draftReport"); saved.remove("selectedId");
                        detail = false; reportId = 0; page = 1; requestedPage = 1; replyError = ""; uncertain = false; persist();
                    }
                    saved.set("owner", owner); restoreDetailTarget(); load(page);
                } else { loading = false; error = response.isSuccessful() ? "无法确认当前账户，请重新登录" : ApiErrors.message(response); changed(); }
            }
            @Override public void onFailure(Call<CurrentUser> c, Throwable t) { if (acceptRead(c)) failRead("账户加载失败，请重试"); }
        });
    }
    private boolean acceptRead(Call<?> call) {
        if (readCall != call) return false;
        readCall = null;
        if (!current()) { connect(); return false; }
        return true;
    }
    public void refresh() { if (!submitting) { if (!current() || owner == 0) connect(); else load(page); } }
    public void retry() { if (!submitting) { if (!current() || owner == 0) connect(); else load(requestedPage); } }
    public void filter(int value) {
        if (submitting || value == status) return;
        status = Math.max(0, Math.min(3, value)); page = 1; requestedPage = 1; detail = false; selected = null;
        saved.remove("selectedId"); reports.clear(); loaded = false; persist(); refresh();
    }
    public void showAll() {
        if (submitting) return;
        reportId = 0; detail = false; selected = null; page = 1; saved.remove("selectedId"); persist(); refresh();
    }
    public boolean hasNext() { return reports.size() == PER_PAGE; }
    public void goToPage(int value) { if (!loading && !submitting && value >= 1 && (value <= page || hasNext())) load(value); }
    private void load(int nextPage) {
        if (!current() || owner == 0) { connect(); return; }
        if (readCall != null) readCall.cancel();
        requestedPage = nextPage; loading = true; error = ""; changed();
        Call<MyReport.Page> call = api.getMyReports(session, owner, status == 0 ? null : Collections.singletonList(status),
                reportId > 0 ? reportId : null, nextPage, PER_PAGE); readCall = call;
        call.enqueue(new Callback<MyReport.Page>() {
            @Override public void onResponse(Call<MyReport.Page> c, Response<MyReport.Page> response) {
                if (!acceptRead(c)) return;
                if (response.code() == 401) { expire(); return; }
                loading = false;
                if (response.isSuccessful() && response.body() != null) {
                    reports.clear();
                    if (response.body().items != null) for (MyReport report : response.body().items)
                        if (report != null && report.id > 0 && report.source_user_id == owner && (reportId <= 0 || report.id == reportId)) reports.add(report);
                    Long selection = saved.get("selectedId");
                    long selectedId = selected != null ? selected.id : selection == null ? reportId : selection;
                    selected = null; for (MyReport report : reports) if (report.id == selectedId) selected = report;
                    if (detail && selected == null && reportId > 0 && !reports.isEmpty()) selected = reports.get(0);
                    if (selected == null) detail = false;
                    page = nextPage; loaded = true; uncertain = false; replyError = ""; persist();
                } else {
                    if (response.code() == 403) { reports.clear(); selected = null; detail = false; }
                    error = response.isSuccessful() ? "报告数据异常，请重试" : ApiErrors.message(response);
                }
                changed();
            }
            @Override public void onFailure(Call<MyReport.Page> c, Throwable t) { if (acceptRead(c)) failRead("报告加载失败，请重试"); }
        });
    }
    private void failRead(String message) { loading = false; error = message; changed(); }
    public void open(MyReport report) {
        if (loading || submitting || !reports.contains(report)) return;
        selected = report; detail = true; replyError = ""; saved.set("selectedId", report.id); persist(); changed();
    }
    public void back() { if (!submitting) { detail = false; persist(); changed(); } }
    public String draft() {
        Long id = saved.get("draftReport"); String value = saved.get("draft");
        return selected != null && id != null && id == selected.id && value != null ? value : "";
    }
    public void setDraft(String value) { if (selected != null) { saved.set("draftReport", selected.id); saved.set("draft", value); } }
    public boolean canReply() { return current() && owner > 0 && selected != null && selected.source_user_id == owner && selected.status == MyReport.PENDING && !loading && !submitting && !uncertain; }
    public void send() {
        if (!canReply()) return;
        String content = ReportRequest.trim(draft());
        if (content.isEmpty()) { replyError = "请填写消息内容"; changed(); return; }
        long id = selected.id; submitting = true; replyError = ""; saved.set("submitting", true); changed();
        Call<MyReport> call = api.appendReportMessage(session, new MyReport.Reply(id, content)); replyCall = call;
        call.enqueue(new Callback<MyReport>() {
            private boolean accept(Call<MyReport> c) {
                if (replyCall != c) return false;
                replyCall = null; submitting = false; saved.set("submitting", false);
                if (!current()) { connect(); return false; } return true;
            }
            @Override public void onResponse(Call<MyReport> c, Response<MyReport> response) {
                if (!accept(c)) return;
                if (response.code() == 401) { expire(); return; }
                MyReport updated = response.body();
                if (response.isSuccessful() && updated != null && updated.id == id && updated.source_user_id == owner) {
                    for (int i = 0; i < reports.size(); i++) if (reports.get(i).id == id) reports.set(i, updated);
                    selected = updated; saved.remove("draft"); saved.remove("draftReport"); notice = "消息已发送";
                } else if (response.isSuccessful()) { uncertain = true; replyError = "发送结果未确认，请刷新报告核对消息记录"; }
                else replyError = ApiErrors.message(response);
                changed();
            }
            @Override public void onFailure(Call<MyReport> c, Throwable t) {
                if (!accept(c)) return;
                uncertain = true; replyError = "发送结果未确认，请刷新报告核对消息记录"; changed();
            }
        });
    }
    private void expire() { UserPreferences.clearSession(getApplication()); connect(); }
    private void cancel() {
        if (readCall != null) readCall.cancel(); if (replyCall != null) replyCall.cancel();
        readCall = null; replyCall = null; loading = false; submitting = false; saved.set("submitting", false);
    }
    @Override protected void onCleared() { cancel(); }
}
