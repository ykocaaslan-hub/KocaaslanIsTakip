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
        assertEquals(3,backup.getInt("version"));assertEquals(original.syncId,row.getString("syncId"));assertEquals(original.updatedAt,row.getLong("updatedAt"));
        db.add("Yavuz Kocaaslan","Gelir",30,"Satış","keep",2000);
        java.lang.reflect.Method restore=MainActivity.class.getDeclaredMethod("restoreBackup",android.net.Uri.class);restore.setAccessible(true);
        for(int i=0;i<2;i++){
            restore.invoke(activity,uri);
            org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
        }
        assertEquals(2,db.all().size());assertEquals(original.syncId,db.byId(id).syncId);
    }
    private android.net.Uri backupFile(String name,int version,Transaction... rows)throws Exception{
        org.json.JSONArray values=new org.json.JSONArray();
        for(Transaction t:rows){
            org.json.JSONObject row=new org.json.JSONObject();row.put("business",t.business);row.put("type",t.type);row.put("amount",t.amount);row.put("category",t.category);row.put("note",t.note);row.put("date",t.date);
            if(version==2){row.put("syncId",t.syncId);row.put("updatedAt",t.updatedAt);}values.put(row);
        }
        org.json.JSONObject backup=new org.json.JSONObject();backup.put("app","Kocaaslan İş Takip");backup.put("version",version);backup.put("transactions",values);
        java.io.File file=new java.io.File(activity.getCacheDir(),name);
        java.nio.file.Files.write(file.toPath(),backup.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));return android.net.Uri.fromFile(file);
    }
    private android.app.AlertDialog importBackup(android.net.Uri uri)throws Exception{
        java.lang.reflect.Method restore=MainActivity.class.getDeclaredMethod("restoreBackup",android.net.Uri.class);restore.setAccessible(true);restore.invoke(activity,uri);
        android.app.AlertDialog confirm=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();confirm.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        android.app.AlertDialog next=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        // These fixture imports are intentionally separate; explicitly acknowledge the legacy warning.
        if("Ayrı işlemler olarak ekle".equals(next.getButton(android.app.AlertDialog.BUTTON_POSITIVE).getText().toString()))next.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();return org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    }
    private String dialogMessage(android.app.AlertDialog dialog){return ((TextView)dialog.findViewById(android.R.id.message)).getText().toString();}
    @Test public void oldBackupBecomesVisibleAfterRestoreWithSearchClearedAndCorrectTotals()throws Exception{
        EditText search=field("search");search.setText("does not match anything");
        android.net.Uri uri=backupFile("legacy.json",1,
                new Transaction(0,"Yavuz Kocaaslan","Gelir",100,"Satış","old",1000),
                new Transaction(0,"Yavuz Kocaaslan","Gider",25,"Kira","old",2000));
        android.app.AlertDialog result=importBackup(uri);
        TransactionAdapter adapter=field("adapter");assertEquals(2,adapter.getCount());assertEquals(0,((Spinner)field("filter")).getSelectedItemPosition());assertEquals("",((EditText)field("search")).getText().toString());assertEquals(3,(int)field("dashboardPeriod"));
        java.text.NumberFormat money=field("money");assertEquals(money.format(100),((TextView)field("dayIncome")).getText().toString());assertEquals(money.format(25),((TextView)field("dayExpense")).getText().toString());assertEquals(money.format(75),((TextView)field("dayNet")).getText().toString());
        assertTrue(dialogMessage(result).contains("Yeni eklenen: 2"));assertTrue(((TextView)field("recordsStatus")).getText().toString().contains("2 / 2"));
        LinearLayout root=field("root");LinearLayout frame=(LinearLayout)((ScrollView)root.getParent()).getParent();
        frame.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY));frame.layout(0,0,360,640);
        assertEquals(2,((ListView)field("list")).getChildCount());
        result.dismiss();importBackup(uri);assertEquals(2,((TransactionAdapter)field("adapter")).getCount());assertTrue(dialogMessage(org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()).contains("Yeni eklenen: 0"));
    }
    @Test public void restoreSelectsBackupBusinessAndRetainsOtherBusinessRecords()throws Exception{
        DbHelper db=field("db");long existing=db.add("Yavuz Kocaaslan","Gelir",70,"Satış","keep",1000);
        android.net.Uri uri=backupFile("canteen.json",1,new Transaction(0,"Kocaaslan Kantin","Gider",15,"Kira","old",2000));
        android.app.AlertDialog result=importBackup(uri);
        assertEquals("Kocaaslan Kantin",(String)field("business"));assertEquals(1,((TransactionAdapter)field("adapter")).getCount());assertNotNull(db.byId(existing));
        String message=dialogMessage(result);assertTrue(message.contains("Yavuz Kocaaslan: 1 kayıt"));assertTrue(message.contains("Kocaaslan Kantin: 1 kayıt"));
        result.dismiss();button(activity.getWindow().getDecorView(),"Genel Toplamlar").performClick();
        android.app.AlertDialog totals=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();java.text.NumberFormat money=field("money");
        assertTrue(viewText(totals.getWindow().getDecorView()).contains("Gider: "+money.format(15)));
        assertTrue(viewText(totals.getWindow().getDecorView()).contains("GENEL TOPLAMI — 1 kayıt"));
    }
    private String viewText(View v){
        StringBuilder text=new StringBuilder();if(v instanceof TextView)text.append(((TextView)v).getText()).append("\n");
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)text.append(viewText(((ViewGroup)v).getChildAt(i)));return text.toString();
    }
    @Test public void emptyFilteredListExplainsHiddenRecordsAndProvidesAllRecordsAction()throws Exception{
        DbHelper db=field("db");db.add("Yavuz Kocaaslan","Gider",12,"Kira","old",1000);controller.pause().resume();
        assertEquals(0,((TransactionAdapter)field("adapter")).getCount());assertTrue(((TextView)field("recordsStatus")).getText().toString().contains("0 / 1"));
        Button show=field("allRecordsButton");assertEquals(View.VISIBLE,show.getVisibility());show.performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals(1,((TransactionAdapter)field("adapter")).getCount());assertEquals(View.GONE,((Button)field("allRecordsButton")).getVisibility());
    }
    @Test public void restoredRecordsRemainVisibleAfterActivityRecreation()throws Exception{
        importBackup(backupFile("rotation.json",1,new Transaction(0,"Kocaaslan Kantin","Gider",15,"Kira","old",2000)));
        controller.recreate();activity=controller.get();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals("Kocaaslan Kantin",(String)field("business"));assertEquals(0,((Spinner)field("filter")).getSelectedItemPosition());assertEquals(1,((TransactionAdapter)field("adapter")).getCount());
    }
    @Test public void restoreReportsSuccessLocallyEvenWhenNetworkStartupFails()throws Exception{
        cloud.when(CloudSync::signedIn).thenThrow(new IllegalStateException("Firebase unavailable"));
        android.app.AlertDialog result=importBackup(backupFile("offline.json",1,new Transaction(0,"Yavuz Kocaaslan","Gider",15,"Kira","old",2000)));
        assertTrue(dialogMessage(result).contains("Yeni eklenen: 1"));assertEquals(1,((TransactionAdapter)field("adapter")).getCount());assertEquals(1,((DbHelper)field("db")).pending().size());
    }
    @Test public void legacyBackupDoesNotDoubleExistingCloudRecordWithoutExplicitConfirmation()throws Exception{
        DbHelper db=field("db");db.upsertFromCloud(new Transaction(0,"Yavuz Kocaaslan","Gider",15,"Kira","same",2000,"cloud-id",12));
        android.net.Uri uri=backupFile("overlap.json",1,new Transaction(0,"Yavuz Kocaaslan","Gider",15,"Kira","same",2000));
        java.lang.reflect.Method restore=MainActivity.class.getDeclaredMethod("restoreBackup",android.net.Uri.class);restore.setAccessible(true);restore.invoke(activity,uri);
        org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        android.app.AlertDialog warning=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertTrue(dialogMessage(warning).contains("ikinci" )||dialogMessage(warning).contains("yeniden eklenip"));
        assertEquals("Ayrı işlemler olarak ekle",warning.getButton(android.app.AlertDialog.BUTTON_POSITIVE).getText().toString());assertEquals(1,db.all().size());assertEquals(15,db.totalStats("Yavuz Kocaaslan")[1],0);assertTrue(db.pending().isEmpty());
        warning.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals(1,db.all().size());assertEquals("cloud-id",db.all().get(0).syncId);
    }
    @Test public void bottomNavigationRemainsOutsideScrollableContent()throws Exception{
        LinearLayout root=field("root");assertTrue(root.getParent() instanceof ScrollView);
        LinearLayout frame=(LinearLayout)((ScrollView)root.getParent()).getParent();assertEquals(2,frame.getChildCount());
        View bottom=frame.getChildAt(1);assertNotNull(button(bottom,"Kayıtlar"));assertNotNull(button(bottom,"Raporlar"));
        frame.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY));frame.layout(0,0,360,640);
        assertTrue(bottom.getHeight()>0);assertTrue(bottom.getBottom()<=640);
    }
    private android.app.AlertDialog previewRepair(android.net.Uri uri)throws Exception {
        java.lang.reflect.Method method=MainActivity.class.getDeclaredMethod("repairBackup",android.net.Uri.class);method.setAccessible(true);method.invoke(activity,uri);
        return org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    }
    private android.net.Uri doubledFixture()throws Exception {
        android.net.Uri uri=backupFile("repair-source.json",1,new Transaction(0,"Yavuz Kocaaslan","Gider",15,"Kira","same",2000));
        BackupData original=BackupData.read(java.nio.file.Files.readAllBytes(new java.io.File(uri.getPath()).toPath()));
        DbHelper db=field("db");db.upsertFromCloud(new Transaction(0,"Yavuz Kocaaslan","Gider",15,"Kira","same",2000,"original",12));db.upsertFromCloud(original.rows.get(0));
        return uri;
    }
    @Test public void repairPreviewCancelLeavesAllRowsTotalsAndOutboxUnchanged()throws Exception {
        android.app.AlertDialog preview=previewRepair(doubledFixture());DbHelper db=field("db");
        assertTrue(dialogMessage(preview).contains("Arşivlenecek kopya: 1"));assertEquals(30,db.totalStats("Yavuz Kocaaslan")[1],0);assertEquals(0,db.archivedCount());
        preview.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals(2,db.all().size());assertEquals(0,db.archivedCount());assertTrue(db.pending().isEmpty());
    }
    @Test public void repairArchivesWithoutDeletingAndBackupAndUndoRemainAvailable()throws Exception {
        android.app.AlertDialog preview=previewRepair(doubledFixture());DbHelper db=field("db");
        preview.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals(2,db.all().size());assertEquals(1,db.archivedCount());assertEquals(15,db.totalStats("Yavuz Kocaaslan")[1],0);assertEquals(1,((TransactionAdapter)field("adapter")).getCount());
        assertTrue(dialogMessage(org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()).contains("silinmeden"));
        java.io.File[] snapshots=activity.getFilesDir().listFiles((dir,name)->name.startsWith("Arsivleme_Oncesi_"));assertTrue(snapshots.length>0);
        boolean fullSnapshot=false;for(java.io.File f:snapshots){BackupData snap=BackupData.read(java.nio.file.Files.readAllBytes(f.toPath()));if(snap.rows.size()==2&&snap.rows.stream().noneMatch(t->t.archived))fullSnapshot=true;}assertTrue(fullSnapshot);
        java.lang.reflect.Method archive=MainActivity.class.getDeclaredMethod("showArchive");archive.setAccessible(true);archive.invoke(activity);
        org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals(1,db.archivedCount());
        org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals(0,db.archivedCount());assertEquals(2,db.all().size());assertEquals(30,db.totalStats("Yavuz Kocaaslan")[1],0);
    }
    @Test public void changedRecordAfterPreviewStopsRepairWithoutPartialChanges()throws Exception {
        android.app.AlertDialog preview=previewRepair(doubledFixture());DbHelper db=field("db");Transaction original=db.all().stream().filter(t->t.syncId.equals("original")).findFirst().get();original.amount=16;db.update(original);
        preview.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals(2,db.all().size());assertEquals(0,db.archivedCount());assertEquals(31,db.totalStats("Yavuz Kocaaslan")[1],0);
        assertTrue(dialogMessage(org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()).contains("önizlemeden sonra değişti"));
    }
    @Test public void repairButtonUsesSeparateDocumentPickerInsteadOfRestoring()throws Exception {
        Button repair=button(activity.getWindow().getDecorView(),"Çift kayıtları düzelt");assertNotNull(repair);repair.performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertTrue(dialogMessage(dialog).contains("geri yüklenmeyecek"));
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        org.robolectric.shadows.ShadowActivity.IntentForResult request=Shadows.shadowOf(activity).getNextStartedActivityForResult();assertEquals(1004,request.requestCode);assertEquals("android.intent.action.OPEN_DOCUMENT",request.intent.getAction());
    }

    private EditText input(View view,String hint){
        if(view instanceof EditText&&hint.equals(String.valueOf(((EditText)view).getHint())))return (EditText)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){EditText found=input(((ViewGroup)view).getChildAt(i),hint);if(found!=null)return found;}return null;
    }
    private android.app.AlertDialog newForm()throws Exception {
        java.lang.reflect.Method method=MainActivity.class.getDeclaredMethod("showForm",String.class,Transaction.class);method.setAccessible(true);method.invoke(activity,"Gelir",null);
        // AlertDialog delivers OnShow on the main queue before a user can tap Save.
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();input(dialog.getWindow().getDecorView(),"Tutar (₺)").setText("2");return dialog;
    }
    @Test public void successfulLocalSaveClosesFormEvenWhenSyncStartupThrows()throws Exception {
        cloud.when(CloudSync::signedIn).thenThrow(new IllegalStateException("Network unavailable"));
        android.app.AlertDialog dialog=newForm();Button save=dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE);save.performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        DbHelper db=field("db");assertEquals(1,db.all().size());assertEquals(1,db.pending().size());assertFalse(dialog.isShowing());
        save.performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals(1,db.all().size());assertEquals(2,db.totalStats("Yavuz Kocaaslan")[0],0);
        assertTrue(org.robolectric.shadows.ShadowToast.getTextOfLatestToast().contains("cihazda kaydedildi"));
    }
    @Test public void repeatedClickOnSameSaveFormCannotInsertAnotherId()throws Exception {
        android.app.AlertDialog dialog=newForm();Button save=dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE);save.performClick();save.performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals(1,((DbHelper)field("db")).all().size());
        newForm().getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals(2,((DbHelper)field("db")).all().size());
    }
    @Test public void invalidSaveMayBeCorrectedWithoutPreventingFirstValidCommit()throws Exception {
        android.app.AlertDialog dialog=newForm();EditText amount=input(dialog.getWindow().getDecorView(),"Tutar (₺)");amount.setText("bad");Button save=dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE);save.performClick();assertTrue(dialog.isShowing());assertEquals(0,((DbHelper)field("db")).all().size());
        amount.setText("2");save.performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals(1,((DbHelper)field("db")).all().size());
    }
    @Test public void backupDiagnosticsKeepStableDeviceIdAndPendingIdsWithoutChangingRecords()throws Exception {
        DbHelper db=field("db");long id=db.add("Yavuz Kocaaslan","Gelir",2,"Satış","test",1000);
        java.lang.reflect.Method diagnostic=MainActivity.class.getDeclaredMethod("diagnosticBackup");diagnostic.setAccessible(true);
        org.json.JSONObject a=new org.json.JSONObject(new String((byte[])diagnostic.invoke(activity),java.nio.charset.StandardCharsets.UTF_8)),b=new org.json.JSONObject(new String((byte[])diagnostic.invoke(activity),java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(a.getJSONObject("diagnostics").getString("installationId"),b.getJSONObject("diagnostics").getString("installationId"));assertEquals(BuildConfig.BUILD_REVISION,a.getJSONObject("diagnostics").getString("buildRevision"));assertEquals(db.byId(id).syncId,a.getJSONObject("diagnostics").getJSONArray("pendingSyncIds").getString(0));assertEquals(1,BackupData.read(a.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).rows.size());assertEquals(1,db.all().size());
    }

}
