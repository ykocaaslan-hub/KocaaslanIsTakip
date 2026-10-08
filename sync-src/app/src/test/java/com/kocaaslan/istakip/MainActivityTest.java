package com.kocaaslan.istakip;

import android.view.*;
import android.widget.*;
import android.os.Bundle;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import java.lang.reflect.Field;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class MainActivityTest {
    private MockedStatic<CloudSync> cloud;private ActivityController<MainActivity> controller;private MainActivity activity;
    @Before public void setup(){
        RuntimeEnvironment.getApplication().deleteDatabase("kocaaslan_is_takip.db");
        cloud=mockStatic(CloudSync.class);cloud.when(CloudSync::signedIn).thenReturn(true);
        controller=Robolectric.buildActivity(MainActivity.class).setup();activity=controller.get();
    }
    @After public void close(){controller.pause().stop().destroy();cloud.close();}
    @SuppressWarnings("unchecked") private <T>T field(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return (T)f.get(activity);}
    private Button button(View view,String text){
        if(view instanceof Button&&((Button)view).getText().toString().contains(text))return (Button)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){Button b=button(((ViewGroup)view).getChildAt(i),text);if(b!=null)return b;}
        return null;
    }
    @Test public void dayWeekMonthAllTabsKeepListAndDisplayedTotalsInSameRange()throws Exception{
        DbHelper db=field("db");Calendar today=Calendar.getInstance();today.set(Calendar.HOUR_OF_DAY,12);
        Calendar old=(Calendar)today.clone();old.add(Calendar.MONTH,-2);
        db.add("Yavuz Kocaaslan","Gelir",100,"Satış","",today.getTimeInMillis());db.add("Yavuz Kocaaslan","Gider",20,"Kira","",today.getTimeInMillis());
        db.add("Yavuz Kocaaslan","Gider",40,"Kira","",old.getTimeInMillis());
        View decor=activity.getWindow().getDecorView();String[] tabs={"Bugün","Bu Hafta","Bu Ay","Tümü"};
        for(int i=0;i<tabs.length;i++){
            assertTrue(button(decor,tabs[i]).performClick());Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            Spinner filter=field("filter");assertEquals(i==3?0:i+1,filter.getSelectedItemPosition());
            TransactionAdapter adapter=field("adapter");assertEquals(i==3?3:2,adapter.getCount());
            java.text.NumberFormat money=field("money");TextView expense=field("dayExpense"),income=field("dayIncome"),net=field("dayNet");
            assertEquals(money.format(i==3?60:20),expense.getText().toString());assertEquals(money.format(100),income.getText().toString());assertEquals(money.format(i==3?40:80),net.getText().toString());
        }
        Bundle state=new Bundle();activity.onSaveInstanceState(state);assertEquals(3,state.getInt("dashboardPeriod"));assertEquals(0,state.getInt("filter"));
    }
    @Test public void backupPreservesIdsAndRepeatedRestoreKeepsCurrentRows()throws Exception{
        DbHelper db=field("db");long id=db.add("Yavuz Kocaaslan","Gider",20,"Kira","",1000);
        Transaction original=db.byId(id);
        java.io.File file=new java.io.File(activity.getCacheDir(),"backup.json");android.net.Uri uri=android.net.Uri.fromFile(file);
        java.lang.reflect.Method write=MainActivity.class.getDeclaredMethod("writeBackup",android.net.Uri.class);write.setAccessible(true);write.invoke(activity,uri);
        org.json.JSONObject backup=new org.json.JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath()),java.nio.charset.StandardCharsets.UTF_8));
        org.json.JSONObject row=backup.getJSONArray("transactions").getJSONObject(0);
        assertEquals(2,backup.getInt("version"));assertEquals(original.syncId,row.getString("syncId"));assertEquals(original.updatedAt,row.getLong("updatedAt"));
        db.add("Yavuz Kocaaslan","Gelir",30,"Satış","keep",2000);
        java.lang.reflect.Method restore=MainActivity.class.getDeclaredMethod("restoreBackup",android.net.Uri.class);restore.setAccessible(true);
        for(int i=0;i<2;i++){
            restore.invoke(activity,uri);
            org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
        }
        assertEquals(2,db.all().size());assertEquals(original.syncId,db.byId(id).syncId);
    }
    @Test public void bottomNavigationRemainsOutsideScrollableContent()throws Exception{
        LinearLayout root=field("root");assertTrue(root.getParent() instanceof ScrollView);
        LinearLayout frame=(LinearLayout)((ScrollView)root.getParent()).getParent();assertEquals(2,frame.getChildCount());
        View bottom=frame.getChildAt(1);assertNotNull(button(bottom,"Kayıtlar"));assertNotNull(button(bottom,"Raporlar"));
        frame.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY));frame.layout(0,0,360,640);
        assertTrue(bottom.getHeight()>0);assertTrue(bottom.getBottom()<=640);
    }
}
