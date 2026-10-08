package com.kocaaslan.istakip;

import android.content.res.Configuration;
import android.os.Looper;
import android.text.Layout;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.*;
import java.lang.reflect.Field;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ResponsiveLayoutTest {
    private MockedStatic<CloudSync> cloud;private ActivityController<MainActivity> controller;private MainActivity activity;
    private Configuration originalConfig;private DisplayMetrics originalMetrics;
    @Before public void setup(){
        RuntimeEnvironment.getApplication().deleteDatabase("kocaaslan_is_takip.db");
        originalConfig=new Configuration(RuntimeEnvironment.getApplication().getResources().getConfiguration());
        originalMetrics=new DisplayMetrics();originalMetrics.setTo(RuntimeEnvironment.getApplication().getResources().getDisplayMetrics());
        cloud=mockStatic(CloudSync.class);cloud.when(CloudSync::signedIn).thenReturn(true);
    }
    @After public void close(){
        if(controller!=null)controller.pause().stop().destroy();cloud.close();
        RuntimeEnvironment.getApplication().getResources().updateConfiguration(originalConfig,originalMetrics);
    }
    @SuppressWarnings("unchecked") private <T>T field(String name)throws Exception {Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return (T)f.get(activity);}
    private Button button(View v,String label){
        if(v instanceof Button&&((Button)v).getText().toString().contains(label))return (Button)v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){Button b=button(((ViewGroup)v).getChildAt(i),label);if(b!=null)return b;}return null;
    }
    private LinearLayout start(float scale)throws Exception {
        Configuration config=new Configuration(originalConfig);config.fontScale=scale;
        DisplayMetrics metrics=new DisplayMetrics();metrics.setTo(originalMetrics);metrics.scaledDensity=metrics.density*scale;
        RuntimeEnvironment.getApplication().getResources().updateConfiguration(config,metrics);
        controller=Robolectric.buildActivity(MainActivity.class).setup();activity=controller.get();
        DbHelper db=field("db");db.add("Yavuz Kocaaslan","Gelir",1259400.25,"Satış","Uzun açıklama ve ayrı kayıt",1000);db.add("Yavuz Kocaaslan","Gider",838000.50,"Diğer Gider","Dar ekranda tutarı koru",1000);
        button(activity.getWindow().getDecorView(),"Tümü").performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();
        LinearLayout root=field("root");return (LinearLayout)((ScrollView)root.getParent()).getParent();
    }
    private void measure(LinearLayout frame,int widthDp,int heightDp){
        float density=activity.getResources().getDisplayMetrics().density;int width=Math.round(widthDp*density),height=Math.round(heightDp*density);
        frame.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));frame.layout(0,0,width,height);
    }
    private int assertReadable(View v){
        if(v.getVisibility()!=View.VISIBLE)return 0;
        int checked=0;
        if(v instanceof TextView&&!(v instanceof EditText)) {
            TextView text=(TextView)v;
            if(text.getText().length()>0){
                Layout layout=text.getLayout();String label=text.getText().toString();assertNotNull(label,layout);assertTrue(label,text.getWidth()>0);
                assertEquals(label,text.getText().length(),layout.getLineEnd(layout.getLineCount()-1));
                int usableHeight=text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom();assertTrue(label+" height "+usableHeight+" < "+layout.getHeight(),usableHeight>=layout.getHeight());
                int usableWidth=text.getWidth()-text.getCompoundPaddingLeft()-text.getCompoundPaddingRight();
                for(int line=0;line<layout.getLineCount();line++)assertTrue(label+" exceeds width "+usableWidth,layout.getLineMax(line)<=usableWidth+2);
                checked++;
            }
        }
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)checked+=assertReadable(((ViewGroup)v).getChildAt(i));
        return checked;
    }
    private void assertContentAndNavigation(LinearLayout frame,int heightDp)throws Exception {
        assertTrue(assertReadable(frame)>20);
        TextView income=field("dayIncome"),expense=field("dayExpense"),net=field("dayNet");
        assertSame(((View)income.getParent()).getParent(),((View)expense.getParent()).getParent());assertSame(((View)income.getParent()).getParent(),((View)net.getParent()).getParent());assertEquals(LinearLayout.HORIZONTAL,((LinearLayout)((View)income.getParent()).getParent()).getOrientation());
        assertSame(button(frame,"Bugün").getParent(),button(frame,"Tümü").getParent());assertEquals(4,((ViewGroup)button(frame,"Bugün").getParent()).getChildCount());
        java.text.NumberFormat money=field("money");assertEquals(money.format(1259400.25),((TextView)field("dayIncome")).getText().toString());assertEquals(money.format(838000.50),((TextView)field("dayExpense")).getText().toString());
        View footer=frame.getChildAt(1);assertTrue(footer.getHeight()>0);assertTrue(footer.getBottom()<=Math.round(heightDp*activity.getResources().getDisplayMetrics().density));assertTrue(frame.getChildAt(0).getHeight()>0);
        for(String label:new String[]{"Ana Sayfa","Kayıtlar","Raporlar","Genel Toplamlar"})assertNotNull(button(footer,label));
        assertNotNull(button(frame,"Çift kayıtları düzelt"));assertEquals(2,((TransactionAdapter)field("adapter")).getCount());
    }
    @Test @Config(qualifiers="w320dp-h640dp-mdpi") public void narrowPhoneAndLargeTextShowFullAmountsAndControls()throws Exception {
        LinearLayout frame=start(1.8f);measure(frame,320,640);assertContentAndNavigation(frame,640);
    }
    @Test @Config(qualifiers="w360dp-h640dp-mdpi") public void normalPhoneHasNoClippedLabelsOrAmounts()throws Exception {
        LinearLayout frame=start(1f);measure(frame,360,640);assertContentAndNavigation(frame,640);
    }
    @Test @Config(qualifiers="w800dp-h600dp-mdpi") public void tabletLayoutKeepsFullTextAndFixedNavigation()throws Exception {
        LinearLayout frame=start(1f);measure(frame,800,600);assertContentAndNavigation(frame,600);
    }
    @Test @Config(qualifiers="w640dp-h360dp-mdpi") public void shortLandscapeWindowLeavesUsableScrollableContent()throws Exception {
        LinearLayout frame=start(1.3f);measure(frame,640,360);assertContentAndNavigation(frame,360);
    }
    @Test @Config(qualifiers="w320dp-h640dp-mdpi") public void veryLargeAmountsAndTransactionRowsRemainReadable()throws Exception {
        LinearLayout frame=start(2f);DbHelper db=field("db");db.add("Yavuz Kocaaslan","Gider",123456789.12,"Diğer Gider","Uzun açıklama büyük tutarı veya Sil düğmesini gizlememeli",1000);
        controller.pause().resume();measure(frame,320,640);assertTrue(assertReadable(frame)>20);
        assertEquals(3,((TransactionAdapter)field("adapter")).getCount());
        View row=((TransactionAdapter)field("adapter")).getView(0,null,(ListView)field("list"));
        int width=284;row.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));row.layout(0,0,width,row.getMeasuredHeight());assertTrue(assertReadable(row)>=4);
        assertNotNull(button(row,"Sil"));
    }
}
