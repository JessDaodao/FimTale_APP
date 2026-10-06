package com.fimtale.ui;

import android.app.Activity;
import android.app.Application;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import java.time.Duration;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34, application=Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class BannerAutoScrollTest {
    private ActivityController<Activity> activity;
    private ViewPager2 pager;
    private BannerAutoScroll scroll;
    private int count=3;
    @Before public void setup() {
        activity=Robolectric.buildActivity(Activity.class).setup().visible();
        pager=new ViewPager2(activity.get()); activity.get().setContentView(pager);
        pager.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @Override public int getItemCount(){return count;}
            @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent,int type) {
                TextView text=new TextView(parent.getContext());text.setLayoutParams(new ViewGroup.LayoutParams(-1,-1));return new RecyclerView.ViewHolder(text){};
            }
            @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder,int position){((TextView)holder.itemView).setText("Banner "+position);}
        });
        pager.measure(View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY));pager.layout(0,0,600,400);
        advance(100); // Finish the activity's first layout before starting the carousel clock.
        scroll=new BannerAutoScroll(pager);
    }
    private void advance(long millis){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis));}
    @After public void cleanup(){scroll.close();activity.pause().stop().destroy();}
    @Test public void waitsFiveSecondsThenAdvancesOnePage() {
        scroll.start();advance(4999);assertEquals(0,pager.getCurrentItem());advance(1);assertEquals(1,pager.getCurrentItem());
        advance(5000);assertEquals(2,pager.getCurrentItem());advance(5000);assertEquals(0,pager.getCurrentItem());
    }
    @Test public void manualSelectionRestartsTheFiveSecondCountdown() {
        final long[] selectedAt = {0};
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int p) { if (p == 2) selectedAt[0] = android.os.SystemClock.uptimeMillis(); }
        });
        scroll.start();advance(4000);pager.setCurrentItem(2,false);
        // Count from onPageSelected, as EYPA does; layout work can advance Robolectric's clock.
        advance(selectedAt[0] + 4999 - android.os.SystemClock.uptimeMillis());
        assertEquals(2,pager.getCurrentItem());advance(1);assertEquals(0,pager.getCurrentItem());
    }
    @Test public void stopCancelsPendingAdvanceAndRestartDoesNotDuplicateIt() {
        scroll.start();advance(4000);scroll.stop();advance(10000);assertEquals(0,pager.getCurrentItem());
        scroll.start();scroll.start();advance(5000);assertEquals(1,pager.getCurrentItem());advance(5000);assertEquals(2,pager.getCurrentItem());
    }
    @Test public void emptyOrSingleBannerNeverAttemptsToAdvance() {
        count=1;pager.getAdapter().notifyDataSetChanged();scroll.start();advance(15000);assertEquals(0,pager.getCurrentItem());
        count=0;pager.getAdapter().notifyDataSetChanged();scroll.start();advance(15000);assertEquals(0,pager.getCurrentItem());
    }
}
