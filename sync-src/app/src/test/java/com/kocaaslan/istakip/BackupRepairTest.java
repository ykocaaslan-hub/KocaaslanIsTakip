package com.kocaaslan.istakip;

import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import android.content.Context;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class BackupRepairTest {
    private Context context;private DbHelper db;
    @Before public void setup(){context=RuntimeEnvironment.getApplication();context.deleteDatabase("kocaaslan_is_takip.db");db=new DbHelper(context);}
    @After public void close(){db.close();}
    private Transaction row(String id,double amount){return new Transaction(0,"Yavuz Kocaaslan","Gider",amount,"Kira","same",1000,id,12);}
    private List<Transaction> legacy(double... amounts)throws Exception {
        org.json.JSONArray rows=new org.json.JSONArray();
        for(double amount:amounts){org.json.JSONObject o=new org.json.JSONObject();o.put("business","Yavuz Kocaaslan");o.put("type","Gider");o.put("amount",amount);o.put("category","Kira");o.put("note","same");o.put("date",1000);rows.put(o);}
        org.json.JSONObject file=new org.json.JSONObject();file.put("app","Kocaaslan İş Takip");file.put("version",1);file.put("transactions",rows);
        return BackupData.read(file.toString().getBytes(StandardCharsets.UTF_8)).rows;
    }
    @Test public void archivesExactly220ImportIdsRetainsEveryRowAndNewTransactions()throws Exception {
        double[] amounts=new double[220];for(int i=0;i<220;i++)amounts[i]=i+1;
        List<Transaction> reference=legacy(amounts);
        for(int i=0;i<220;i++){db.upsertFromCloud(row("original-"+i,amounts[i]));db.upsertFromCloud(reference.get(i));}
        long added=db.add("Yavuz Kocaaslan","Gelir",7,"Satış","new",2000);
        BackupRepair.Plan plan=db.previewRepair(reference);assertEquals(220,plan.targets.size());assertEquals(441,db.all().size());
        assertEquals(220,db.archiveRepair(plan));assertEquals(441,db.all().size());assertEquals(221,db.count("Yavuz Kocaaslan"));assertEquals(220,db.archivedCount());
        assertArrayEquals(new double[]{7,24310,-24303},db.totalStats("Yavuz Kocaaslan"),0);assertNotNull(db.byId(added));
        assertEquals(24310,db.stats("Yavuz Kocaaslan",1000,2000)[1],0);assertEquals(24310,db.expenseSum("Yavuz Kocaaslan",1000,2000),0);
        assertEquals(220,db.list("Yavuz Kocaaslan","same",0,0,0).size());assertEquals(220,db.pending().stream().filter(t->t.archived).count());assertTrue(db.pendingDeletions().isEmpty());
        db.close();db=new DbHelper(context);assertEquals(220,db.archivedCount());assertEquals(0,db.previewRepair(reference).targets.size());
        db.mergeBackup(reference);assertEquals(441,db.all().size());assertEquals(220,db.archivedCount());
    }
    @Test public void originalMultiplicityAndLegitimateIdenticalNewRowsArePreserved()throws Exception {
        List<Transaction> ref=legacy(5,5);db.upsertFromCloud(row("original-a",5));db.upsertFromCloud(row("original-b",5));db.upsertFromCloud(row("new-legitimate",5));for(Transaction t:ref)db.upsertFromCloud(t);
        assertEquals(2,db.archiveRepair(db.previewRepair(ref)));assertEquals(3,db.count("Yavuz Kocaaslan"));assertEquals(15,db.totalStats("Yavuz Kocaaslan")[1],0);assertEquals(5,db.all().size());
    }
    @Test public void changedCopiesAndMissingOriginalsCannotBeArchived()throws Exception {
        List<Transaction> ref=legacy(5,8,9);db.upsertFromCloud(row("original-a",5));db.upsertFromCloud(row("original-b",8));
        db.upsertFromCloud(row(ref.get(0).syncId,6));db.upsertFromCloud(ref.get(1));db.upsertFromCloud(ref.get(2));
        BackupRepair.Plan plan=db.previewRepair(ref);assertEquals(1,plan.changed);assertEquals(1,plan.withoutOriginal);assertEquals(1,plan.targets.size());
        assertEquals(1,db.archiveRepair(plan));assertEquals(4,db.count("Yavuz Kocaaslan"));assertEquals(5,db.all().size());
    }
    @Test public void partialOriginalMultiplicityDoesNotCollapseLegitimateEntries()throws Exception {
        List<Transaction> ref=legacy(5,5);db.upsertFromCloud(row("only-one-original",5));for(Transaction t:ref)db.upsertFromCloud(t);
        BackupRepair.Plan plan=db.previewRepair(ref);assertEquals(2,plan.withoutOriginal);assertEquals(0,plan.targets.size());assertEquals(3,db.count("Yavuz Kocaaslan"));
    }
    @Test public void concurrentCopyOrOriginalEditsInvalidateWholePreview()throws Exception {
        List<Transaction> ref=legacy(5,8);db.upsertFromCloud(row("original-a",5));db.upsertFromCloud(row("original-b",8));for(Transaction t:ref)db.upsertFromCloud(t);
        BackupRepair.Plan plan=db.previewRepair(ref);Transaction t=db.all().stream().filter(r->r.syncId.equals("original-b")).findFirst().get();t.amount=9;db.update(t);
        try{db.archiveRepair(plan);fail("A stale preview must fail");}catch(IllegalStateException expected){}
        assertEquals(0,db.archivedCount());assertEquals(4,db.all().size());
    }
    @Test public void backupRoundTripAndUndoPreserveArchivesIdsAndPendingState()throws Exception {
        List<Transaction> ref=legacy(5);db.upsertFromCloud(row("original",5));db.upsertFromCloud(ref.get(0));db.archiveRepair(db.previewRepair(ref));
        byte[] backup=BackupData.write(db.all());assertEquals(3,new org.json.JSONObject(new String(backup,StandardCharsets.UTF_8)).getInt("version"));
        List<Transaction> saved=BackupData.read(backup).rows;assertEquals(2,saved.size());assertEquals(1,saved.stream().filter(t->t.archived).count());
        db.close();context.deleteDatabase("kocaaslan_is_takip.db");db=new DbHelper(context);db.mergeBackup(saved);assertEquals(1,db.count("Yavuz Kocaaslan"));assertEquals(1,db.archivedCount());
        long version=db.all().stream().filter(t->t.archived).findFirst().get().updatedAt;
        assertEquals(1,db.undoArchive());assertEquals(2,db.count("Yavuz Kocaaslan"));assertEquals(0,db.archivedCount());assertEquals(10,db.totalStats("Yavuz Kocaaslan")[1],0);
        Transaction undone=db.all().stream().filter(t->t.syncId.equals(ref.get(0).syncId)).findFirst().get();assertTrue(undone.updatedAt>version);db.markSynced(undone.syncId,version);assertEquals(2,db.pending().size());
    }
    @Test public void archiveRejectsStaleCloudSnapshotsButAcceptsNewerUndo()throws Exception {
        List<Transaction> ref=legacy(5);db.upsertFromCloud(row("original",5));db.upsertFromCloud(ref.get(0));db.archiveRepair(db.previewRepair(ref));
        Transaction archived=db.all().stream().filter(t->t.archived).findFirst().get();
        db.upsertFromCloud(row(archived.syncId,5));assertEquals(1,db.archivedCount());
        db.markSynced(archived.syncId,archived.updatedAt);db.upsertFromCloud(row(archived.syncId,5));assertEquals(1,db.archivedCount());
        Transaction undo=row(archived.syncId,5);undo.updatedAt=archived.updatedAt+1;db.upsertFromCloud(undo);assertEquals(0,db.archivedCount());assertEquals(2,db.all().size());
    }
    @Test public void archiveBackupRequiresBooleanStateAndStableIdentity()throws Exception {
        for(String fields:new String[]{"\"archived\":true", "\"syncId\":\"a\"", "\"syncId\":\"a\",\"archived\":\"false\""}) {
            String text="{\"app\":\"Kocaaslan İş Takip\",\"version\":3,\"transactions\":[{\"business\":\"Yavuz Kocaaslan\",\"type\":\"Gider\",\"amount\":5,\"date\":1000,"+fields+"}]}";
            try{BackupData.read(text.getBytes(StandardCharsets.UTF_8));fail("Invalid archive backup must fail");}catch(java.io.IOException expected){}
        }
    }
}
