package com.app.fimtale;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Guards against unbounded feed measurement. No activity, API calls or session changes. */
@RunWith(AndroidJUnit4.class)
public class HomeLayoutTest {
    @Test public void headerAndFooterCanInflateBeforeAnAdapterIsInstalled() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = new ContextThemeWrapper(InstrumentationRegistry.getInstrumentation().getTargetContext(), R.style.Theme_Fimtale);
            LayoutInflater inflater = LayoutInflater.from(context);
            View page = inflater.inflate(R.layout.fragment_home, null, false);
            RecyclerView list = page.findViewById(R.id.homeList);
            assertNotNull("The LayoutManager must exist before HomeFragment inflates rows", list.getLayoutManager());
            assertNull(list.getAdapter());
            View header = inflater.inflate(R.layout.item_home_header, list, false);
            View footer = inflater.inflate(R.layout.item_home_footer, list, false);
            assertTrue(header.getLayoutParams() instanceof RecyclerView.LayoutParams);
            assertTrue(footer.getLayoutParams() instanceof RecyclerView.LayoutParams);
            assertNotNull(header.findViewById(R.id.bannerViewPager));
            assertNotNull(footer.findViewById(R.id.viewMoreButton));
        });
    }

    @Test public void firstLayoutDoesNotInflateTheWholeFeed() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = new ContextThemeWrapper(InstrumentationRegistry.getInstrumentation().getTargetContext(), R.style.Theme_Fimtale);
            LayoutInflater inflater = LayoutInflater.from(context);
            View page = inflater.inflate(R.layout.fragment_home, null, false);
            RecyclerView list = page.findViewById(R.id.homeList);
            list.setVisibility(View.VISIBLE);
            page.findViewById(R.id.loadingSkeleton).setVisibility(View.GONE);
            assertNotNull(list.getLayoutManager());
            AtomicInteger created = new AtomicInteger();
            list.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
                    created.incrementAndGet();
                    return new RecyclerView.ViewHolder(inflater.inflate(R.layout.grid_item_topic, parent, false)) {};
                }
                @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {}
                @Override public int getItemCount() { return 100; }
            });
            float density = context.getResources().getDisplayMetrics().density;
            int width = Math.round(360 * density), height = Math.round(800 * density);
            page.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            page.layout(0, 0, width, height);
            assertTrue("Feed must stay within its viewport", list.getMeasuredHeight() <= height);
            assertTrue("Visible cards should be created", created.get() > 0);
            assertTrue("Screen-off cards must be recycled instead of inflated up front", created.get() < 10);
            list.setAdapter(null);
        });
    }
}
