package com.fimtale.report;

import android.app.Application;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import com.fimtale.MyReportsActivity;
import com.fimtale.R;
import com.fimtale.utils.UserPreferences;
import com.google.android.material.tabs.TabLayout;
import com.google.gson.Gson;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,application = com.fimtale.ResourceApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MyReportsActivityTest {
    @Test public void listOpensDetailsWithSnapshotMessagesAndPendingOnlyComposer() {
        UserPreferences.saveToken(RuntimeEnvironment.getApplication(),"");
        try(ActivityController<MyReportsActivity> controller=Robolectric.buildActivity(MyReportsActivity.class).setup().visible()) {
            MyReportsActivity activity=controller.get();
            assertEquals(4,((TabLayout)activity.findViewById(R.id.myReportsTabs)).getTabCount());
            assertNotNull(activity.findViewById(R.id.toolbarContainer));
            MyReportsViewModel model=new ViewModelProvider(activity).get(MyReportsViewModel.class);
            MyReport report=new Gson().fromJson("{\"id\":42,\"status\":1,\"source_user_id\":9,\"target_type\":1,\"target_id\":12,\"payload\":{\"kind\":\"report\",\"content_snapshot\":\"[b]提交时的内容[/b]\",\"messages\":[{\"content\":\"[i]请补充信息[/i]\",\"user_id\":2,\"username\":\"管理员\"}]}}",MyReport.class);
            model.needsLogin=false;model.loaded=true;model.reports.add(report);model.changes.setValue(1);
            RecyclerView list=activity.findViewById(R.id.myReportsList);layout(list);
            list.findViewHolderForAdapterPosition(0).itemView.performClick();layout(list);
            android.content.Intent intent=Shadows.shadowOf(activity).getNextStartedActivity();
            assertEquals(com.fimtale.ReportDetailActivity.class.getName(),intent.getComponent().getClassName());
            assertEquals(42,intent.getLongExtra(MyReportsActivity.EXTRA_REPORT_ID,0));
            assertFalse(model.detail);
            assertEquals(View.VISIBLE,activity.findViewById(R.id.myReportsTabs).getVisibility());
            try(ActivityController<com.fimtale.ReportDetailActivity> detailController=Robolectric.buildActivity(com.fimtale.ReportDetailActivity.class,intent).setup().visible()) {
                com.fimtale.ReportDetailActivity detail=detailController.get();
                MyReportsViewModel detailModel=new ViewModelProvider(detail).get(MyReportsViewModel.class);
                assertNotSame(model,detailModel);
                detailModel.needsLogin=false;detailModel.loaded=true;detailModel.selected=report;detailModel.reports.add(report);detailModel.changes.setValue(1);
                RecyclerView detailList=detail.findViewById(R.id.myReportsList);layout(detailList);
                assertEquals(View.GONE,detail.findViewById(R.id.myReportsTabs).getVisibility());
                assertEquals(0,detail.findViewById(R.id.myReportsContent).getPaddingTop());assertTrue(detailList.getPaddingTop()>0);
                assertEquals(View.VISIBLE,detail.findViewById(R.id.myReportComposer).getVisibility());
                assertTrue(contains(detailList,"提交时内容快照"));assertTrue(contains(detailList,"请补充信息"));
                report.status=MyReport.RESOLVED;detailModel.changes.setValue(2);layout(detailList);
                assertEquals(View.GONE,detail.findViewById(R.id.myReportComposer).getVisibility());
                detail.getOnBackPressedDispatcher().onBackPressed();assertTrue(detail.isFinishing());
            }
            assertFalse(activity.isFinishing());

        }
    }
    private void layout(RecyclerView list) {
        list.measure(View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1500,View.MeasureSpec.EXACTLY));list.layout(0,0,1080,1500);
    }
    private boolean contains(View view,String value) {
        if(view instanceof TextView && ((TextView)view).getText().toString().contains(value))return true;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(contains(((ViewGroup)view).getChildAt(i),value))return true;
        return false;
    }
}
