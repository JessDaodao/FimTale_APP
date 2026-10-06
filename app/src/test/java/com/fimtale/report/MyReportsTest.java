package com.fimtale.report;

import android.app.Application;
import androidx.lifecycle.SavedStateHandle;
import com.fimtale.model.CurrentUser;
import com.fimtale.network.FimTaleApiService;
import com.fimtale.utils.UserPreferences;
import com.google.gson.Gson;
import java.lang.reflect.Proxy;
import java.util.*;
import okhttp3.Request;
import okio.Timeout;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import retrofit2.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34, application=Application.class)
public class MyReportsTest {
    private Application app;
    private MyReportsViewModel model;
    private SavedStateHandle saved;
    private FimTaleApiService api;
    private final List<Pending<?>> calls=new ArrayList<>();
    @Before public void setup() {
        app=RuntimeEnvironment.getApplication(); UserPreferences.saveToken(app,"first-session");
        saved=new SavedStateHandle();
        api=(FimTaleApiService)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{FimTaleApiService.class},(proxy,method,args)->{
            Pending<Object> call=new Pending<>(method.getName(),args); calls.add(call); return call;
        });
        model=new MyReportsViewModel(app,saved,api); model.initialize(0); model.connect();
    }
    @After public void cleanup() { model.onCleared(); }
    @SuppressWarnings("unchecked") private <T> Pending<T> last(String name) {
        for(int i=calls.size()-1;i>=0;i--)if(calls.get(i).name.equals(name))return(Pending<T>)calls.get(i);
        throw new AssertionError(name);
    }
    private void identify(int id) { CurrentUser user=new CurrentUser();user.id=id;this.<CurrentUser>last("getCurrentUser").reply(user); }
    private MyReport report(long id,int status,long owner) { MyReport r=new MyReport();r.id=id;r.status=status;r.source_user_id=owner;return r; }
    private void page(MyReport... items) { MyReport.Page page=new MyReport.Page();page.items=Arrays.asList(items);this.<MyReport.Page>last("getMyReports").reply(page); }
    @Test public void everyListRequestIsScopedToVerifiedUserIncludingAllStatus() {
        assertEquals(1,calls.size()); identify(9);
        Pending<?> call=last("getMyReports"); assertEquals("first-session",call.args[0]);assertEquals(9L,call.args[1]);assertNull(call.args[2]);assertEquals(20,call.args[5]);
        page(report(1,1,9),report(2,1,10));assertEquals(1,model.reports.size());
        model.filter(2);assertEquals(Collections.singletonList(2),last("getMyReports").args[2]);assertEquals(9L,last("getMyReports").args[1]);
    }
    @Test public void failedIdentityNeverIssuesAnUnscopedReportRequest() {
        this.<CurrentUser>last("getCurrentUser").fail();assertEquals(1,calls.size());assertFalse(model.loading);assertFalse(model.error.isEmpty());
        model.retry();assertEquals("getCurrentUser",calls.get(1).name);
    }
    @Test public void staleFilterResponseCannotReplaceNewFilter() {
        identify(9);Pending<MyReport.Page> old=last("getMyReports");model.filter(3);assertTrue(old.canceled);
        MyReport.Page stale=new MyReport.Page();stale.items=Arrays.asList(report(1,1,9));old.reply(stale);assertTrue(model.reports.isEmpty());
        page(report(2,3,9));assertEquals(2,model.reports.get(0).id);assertEquals(3,model.status);
    }
    @Test public void failedPaginationRetriesRequestedPageAndFilterResetsToOne() {
        identify(9);MyReport[] first=new MyReport[20];for(int i=0;i<20;i++)first[i]=report(i+1,1,9);page(first);
        assertTrue(model.hasNext());model.goToPage(2);this.<MyReport.Page>last("getMyReports").fail();assertEquals(1,model.page);
        model.retry();assertEquals(2,last("getMyReports").args[4]);page(report(21,1,9));assertEquals(2,model.page);assertFalse(model.hasNext());
        model.filter(1);assertEquals(1,last("getMyReports").args[4]);
    }
    @Test public void deepLinkLoadsOnlyRequestedReportAndCanReturnToAll() {
        model.onCleared();saved=new SavedStateHandle();model=new MyReportsViewModel(app,saved,api);model.initialize(42);model.connect();identify(9);
        assertEquals(42L,last("getMyReports").args[3]);page(report(42,1,9));assertTrue(model.detail);assertEquals(42,model.selected.id);
        model.showAll();assertNull(last("getMyReports").args[3]);assertFalse(model.detail);
    }
    @Test public void closedReportAndEmptyReplyCannotSend() {
        identify(9);MyReport closed=report(1,2,9);page(closed);model.open(closed);model.setDraft("test");assertFalse(model.canReply());
        int count=calls.size();model.send();assertEquals(count,calls.size());
        closed.status=1;model.setDraft("\u3000 \n");model.send();assertEquals(count,calls.size());assertFalse(model.replyError.isEmpty());
    }
    @Test public void replyFailurePreservesDraftAndRequiresRefreshBeforeRetry() {
        identify(9);MyReport r=report(42,1,9);page(r);model.open(r);model.setDraft(" [b]补充说明[/b] ");model.send();
        MyReport.Reply reply=(MyReport.Reply)last("appendReportMessage").args[1];assertEquals(42,reply.report_id);assertEquals("[b]补充说明[/b]",reply.content);
        this.<MyReport>last("appendReportMessage").fail();assertTrue(model.uncertain);assertFalse(model.canReply());assertTrue(model.draft().contains("补充说明"));
        model.refresh();page(r);assertTrue(model.canReply());assertEquals("",model.replyError);
        model.send();MyReport updated=report(42,1,9);this.<MyReport>last("appendReportMessage").reply(updated);assertEquals("",model.draft());assertSame(updated,model.selected);
    }
    @Test public void accountChangeClearsReportsDraftsAndIgnoresOldReplies() {
        identify(9);MyReport r=report(42,1,9);page(r);model.open(r);model.setDraft("private");model.send();Pending<MyReport> old=last("appendReportMessage");
        UserPreferences.saveToken(app,"second-session");model.connect();assertTrue(old.canceled);old.reply(r);
        assertNull(model.selected);assertTrue(model.reports.isEmpty());assertEquals("",model.draft());
        identify(10);assertEquals(10L,last("getMyReports").args[1]);
    }
    @Test public void processRestoreKeepsSelectionAndDraftButDoesNotResubmit() {
        identify(9);MyReport r=report(42,1,9);page(r);model.open(r);model.setDraft("draft");
        model.onCleared();model=new MyReportsViewModel(app,saved,api);model.connect();identify(9);page(r);
        assertTrue(model.detail);assertEquals("draft",model.draft());assertEquals(42,model.selected.id);
        for(Pending<?> call:calls)assertNotEquals("appendReportMessage",call.name);
    }
    @Test public void wireModelsPreserveSnapshotsMessagesAndSafeTargetLinks() {
        MyReport r=new Gson().fromJson("{\"id\":42,\"status\":3,\"target_type\":1,\"target_id\":9,\"payload\":{\"kind\":\"report\",\"content_snapshot\":\"[b]内容[/b]\",\"messages\":[{\"content\":\"已处理\",\"user_id\":2}],\"debug_context\":{\"failures\":[{\"status\":503,\"rid\":\"req-1\"}],\"exceptions\":[{\"stack\":\"not for this page\"}]}}}",MyReport.class);
        assertEquals("已驳回",r.statusLabel());assertEquals("内容报告",r.kindLabel());assertEquals("/work/9",r.targetPath());assertEquals("[b]内容[/b]",r.payload.content_snapshot);
        assertEquals(1,r.messages().size());assertEquals("req-1",r.failures().get(0).rid);
        r.target_type=4;assertNull(r.targetPath());r.target_type=0;assertEquals("站点",r.targetLabel());
    }
    @Test public void standaloneDetailRetainsItsTargetAcrossAccountChanges() {
        model.onCleared();saved=new SavedStateHandle();model=new MyReportsViewModel(app,saved,api);
        model.initializeDetail(42);model.connect();identify(9);page(report(42,1,9));
        UserPreferences.saveToken(app,"second-session");model.connect();identify(10);
        assertEquals(42L,last("getMyReports").args[3]);assertEquals(10L,last("getMyReports").args[1]);
        page(report(99,1,10));assertTrue(model.reports.isEmpty());assertNull(model.selected);
    }
    private static final class Pending<T> implements Call<T> {
        final String name;final Object[] args;Callback<T> callback;boolean canceled;
        Pending(String name,Object[] args){this.name=name;this.args=args;}
        void reply(T data){callback.onResponse(this,Response.success(data));}void fail(){callback.onFailure(this,new java.io.IOException("fixture"));}
        @Override public void enqueue(Callback<T> c){callback=c;}@Override public Response<T> execute(){throw new UnsupportedOperationException();}
        @Override public boolean isExecuted(){return callback!=null;}@Override public void cancel(){canceled=true;}@Override public boolean isCanceled(){return canceled;}
        @Override public Call<T> clone(){return new Pending<>(name,args);}@Override public Request request(){return new Request.Builder().url("https://example.invalid/").build();}
        @Override public Timeout timeout(){return Timeout.NONE;}
    }
}
