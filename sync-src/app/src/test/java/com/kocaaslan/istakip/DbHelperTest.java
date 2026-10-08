package com.kocaaslan.istakip;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class DbHelperTest {
    private Context context;private DbHelper db;
    @Before public void setup(){context=RuntimeEnvironment.getApplication();context.deleteDatabase("kocaaslan_is_takip.db");db=new DbHelper(context);db.getWritableDatabase();}
    @After public void close(){db.close();}
    private Transaction row(String id,long version,double amount){return new Transaction(0,"shop","Gider",amount,"Kira","same",1000,id,version);}
    @Test public void identicalContentDifferentIdsRemainSeparateAcrossRestarts(){
        for(int i=0;i<5;i++){db.upsertFromCloud(row("a",10,5));db.upsertFromCloud(row("b",10,5));db.close();db=new DbHelper(context);}
        assertEquals(2,db.all().size());assertEquals(10,db.totalStats("shop")[1],0);
    }
    @Test public void dirtyEditAndNewerVersionSurviveOldSnapshotsAndLateAcknowledgement(){
        long id=db.add("shop","Gider",5,"Kira","same",1000);Transaction original=db.byId(id);
        db.upsertFromCloud(row(original.syncId,original.updatedAt+100,7));assertEquals(5,db.byId(id).amount,0);
        db.update(new Transaction(id,"shop","Gider",9,"Kira","same",1000));
        Transaction edited=db.byId(id);db.markSynced(original.syncId,original.updatedAt);
        assertEquals(1,db.pending().size());assertTrue(edited.updatedAt>original.updatedAt);
        db.markSynced(edited.syncId,edited.updatedAt);db.upsertFromCloud(row(edited.syncId,edited.updatedAt-1,3));
        assertEquals(9,db.byId(id).amount,0);assertTrue(db.pending().isEmpty());
    }
    @Test public void restoreMergesAndKeepsStableIdsWithoutOverwritingCurrentRows(){
        long id=db.add("shop","Gelir",20,"Satış","keep",1000);
        db.mergeBackup(Arrays.asList(row("backup",12,5),row("backup",12,5)));
        db.mergeBackup(Arrays.asList(row("backup",13,8)));
        assertEquals(2,db.all().size());assertNotNull(db.byId(id));
        assertEquals("backup",db.all().get(1).syncId);assertEquals(5,db.all().get(1).amount,0);
    }
    @Test public void pendingUploadAndDeletionSurviveProcessRestart(){
        long id=db.add("shop","Gider",5,"Kira","same",1000);String sync=db.byId(id).syncId;
        db.close();db=new DbHelper(context);assertEquals(sync,db.pending().get(0).syncId);
        db.delete(id);db.close();db=new DbHelper(context);assertEquals(1,db.pendingDeletions().size());assertTrue(db.all().isEmpty());
        db.upsertFromCloud(row(sync,Long.MAX_VALUE,8));assertTrue(db.all().isEmpty());
        long version=db.pendingDeletions().get(sync);db.markDeletionSynced(sync,version);
        db.mergeBackup(Arrays.asList(row(sync,10,5)));assertTrue(db.all().isEmpty());
    }
    @Test public void cloudDeletionRequiresExplicitVersionAndDoesNotRemoveDirtyRows(){
        db.upsertFromCloud(row("remote",10,5));db.applyCloudDeletion("remote",9);assertEquals(1,db.all().size());
        db.applyCloudDeletion("remote",10);assertTrue(db.all().isEmpty());
        long id=db.add("shop","Gider",5,"Kira","same",1000);db.applyCloudDeletion(db.byId(id).syncId,Long.MAX_VALUE);assertNotNull(db.byId(id));
    }
    @Test public void versionTwoUpgradePreservesAllRowsAndRepairsOnlyMissingOrRepeatedIds(){
        db.close();context.deleteDatabase("kocaaslan_is_takip.db");
        SQLiteDatabase old=context.openOrCreateDatabase("kocaaslan_is_takip.db",0,null);
        old.execSQL("CREATE TABLE transactions (id INTEGER PRIMARY KEY AUTOINCREMENT,business TEXT NOT NULL,type TEXT NOT NULL,amount REAL NOT NULL,category TEXT,note TEXT,date INTEGER NOT NULL,sync_id TEXT,updated_at INTEGER NOT NULL DEFAULT 0,sync_state INTEGER NOT NULL DEFAULT 1)");
        old.execSQL("INSERT INTO transactions(business,type,amount,date,sync_id) VALUES ('shop','Gider',5,1000,'stable'),('shop','Gider',5,1000,'stable'),('shop','Gider',5,1000,NULL)");old.setVersion(2);old.close();
        db=new DbHelper(context);assertEquals(3,db.all().size());assertEquals("stable",db.all().get(0).syncId);
        Set<String> ids=new HashSet<>();for(Transaction t:db.all())ids.add(t.syncId);assertEquals(3,ids.size());assertFalse(ids.contains(null));
    }
    @Test public void restoreSummaryDistinguishesNewExistingAndDeletedIds(){
        db.mergeBackup(Arrays.asList(row("existing",12,5)));long deleted=db.add("shop","Gider",8,"Kira","",1000);String deletedId=db.byId(deleted).syncId;db.delete(deleted);
        DbHelper.RestoreResult result=db.mergeBackup(Arrays.asList(row("existing",13,9),row("new",14,7),row(deletedId,15,8)));
        assertEquals(1,result.added);assertEquals(1,result.alreadyPresent);assertEquals(1,result.previouslyDeleted);assertEquals(2,db.count("shop"));assertEquals(12,db.totalStats("shop")[1],0);
    }
    @Test public void invalidRestoreRowRollsBackEntireImportWithoutChangingExistingRecords(){
        long current=db.add("shop","Gelir",20,"Satış","keep",1000);
        try{db.mergeBackup(Arrays.asList(row("new",14,7),new Transaction(0,null,"Gider",9,"","",2000,"invalid",12)));fail("Invalid row must fail");}catch(android.database.sqlite.SQLiteConstraintException expected){}
        assertEquals(1,db.all().size());assertNotNull(db.byId(current));assertEquals(0,db.count("other"));
    }
    @Test public void totalsAndListRespectInclusiveStartExclusiveEndAndBusiness(){
        db.add("shop","Gelir",100,"","",1000);db.add("shop","Gider",20,"","",1000);
        db.add("shop","Gider",30,"","",1999);db.add("shop","Gider",40,"","",2000);db.add("other","Gider",999,"","",1000);
        assertArrayEquals(new double[]{100,50,50},db.stats("shop",1000,2000),0);
        assertEquals(50,db.expenseSum("shop",1000,2000),0);assertEquals(3,db.list("shop","",1000,2000,0).size());
        assertArrayEquals(new double[]{100,90,10},db.totalStats("shop"),0);
    }
}
