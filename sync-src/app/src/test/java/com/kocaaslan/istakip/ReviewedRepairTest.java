package com.kocaaslan.istakip;

import android.content.Context;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class ReviewedRepairTest {
    private DbHelper db;
    static List<Transaction> targets(){return Arrays.asList(row("migration-a",false),row("migration-b",false));}
    static List<Transaction> retained(){return Arrays.asList(row("reference-a",false),row("reference-b",true));}
    static Transaction row(String id,boolean archived){return new Transaction(0,"Yavuz Kocaaslan","Gider",id.endsWith("a")?5:7,"Kira","",1000,id,10,archived);}
    static byte[] bytes(List<Transaction> targets,List<Transaction> refs)throws Exception {
        JSONObject root=new JSONObject();root.put("app",ReviewedRepair.APP);root.put("version",1);root.put("installationId","phone-device");root.put("projectId","project");root.put("uid","same-account");
        root.put("targets",new JSONObject(new String(BackupData.write(targets),StandardCharsets.UTF_8)).getJSONArray("transactions"));root.put("retained",new JSONObject(new String(BackupData.write(refs),StandardCharsets.UTF_8)).getJSONArray("transactions"));
        JSONArray pairs=new JSONArray();for(Transaction target:targets){Transaction ref=refs.stream().filter(r->BackupRepair.key(r).equals(BackupRepair.key(target))).findFirst().get();pairs.put(new JSONObject().put("target",target.syncId).put("retained",ref.syncId));}root.put("pairs",pairs);return root.toString().getBytes(StandardCharsets.UTF_8);
    }
    @Before public void setup(){Context c=RuntimeEnvironment.getApplication();c.deleteDatabase("reviewed.db");db=new DbHelper(c,"reviewed.db");List<Transaction> rows=new ArrayList<>(targets());rows.addAll(retained());db.mergeBackup(rows);}
    @After public void close(){db.close();}
    private ReviewedRepair source()throws Exception{return ReviewedRepair.read(bytes(targets(),retained()));}
    @Test public void explicitTargetsPreserveArchivedCounterpartNewEntryAndAllIdentities()throws Exception {
        db.add("Yavuz Kocaaslan","Gider",5,"Kira","",1000);Set<String> before=new HashSet<>();for(Transaction t:db.all())before.add(t.syncId);
        assertEquals(2,db.archiveRepair(BackupRepair.reviewedPlan(source(),db.all())));assertEquals(5,db.all().size());assertEquals(3,db.archivedCount());assertEquals(2,db.count("Yavuz Kocaaslan"));assertEquals(10,db.totalStats("Yavuz Kocaaslan")[1],0);assertTrue(db.pendingDeletions().isEmpty());
        for(Transaction t:db.all())assertTrue(before.remove(t.syncId));assertTrue(before.isEmpty());
    }
    @Test public void changedTargetAfterPreviewRejectsEveryArchive()throws Exception {
        BackupRepair.Plan plan=BackupRepair.reviewedPlan(source(),db.all());Transaction t=db.all().stream().filter(r->r.syncId.equals("migration-b")).findFirst().get();t.amount=8;db.update(t);
        assertThrows(IllegalStateException.class,()->db.archiveRepair(plan));assertEquals(1,db.archivedCount());assertEquals(4,db.all().size());
    }
    @Test public void changedRetainedArchiveStateOrVersionRejectsEveryArchive()throws Exception {
        BackupRepair.Plan plan=BackupRepair.reviewedPlan(source(),db.all());db.undoArchive();assertThrows(IllegalStateException.class,()->db.archiveRepair(plan));assertEquals(0,db.archivedCount());assertEquals(4,db.all().size());
    }
    @Test public void repeatedPlanAndReopenDoNotCreateOrReactivateRecords()throws Exception {
        ReviewedRepair source=source();db.archiveRepair(BackupRepair.reviewedPlan(source,db.all()));db.close();db=new DbHelper(RuntimeEnvironment.getApplication(),"reviewed.db");BackupRepair.Plan again=BackupRepair.reviewedPlan(source,db.all());assertEquals(2,again.alreadyArchived);assertEquals(0,again.targets.size());assertEquals(0,db.archiveRepair(again));assertEquals(4,db.all().size());assertEquals(3,db.archivedCount());
    }
    @Test public void wrongDeviceAccountAndUnpairedOrConflictingIdsAreRejected()throws Exception {
        ReviewedRepair source=source();source.checkDevice("phone-device","project","same-account");assertThrows(java.io.IOException.class,()->source.checkDevice("tablet-device","project","same-account"));assertThrows(java.io.IOException.class,()->source.checkDevice("phone-device","project","other-account"));
        JSONObject malformed=new JSONObject(new String(bytes(targets(),retained()),StandardCharsets.UTF_8));malformed.getJSONArray("pairs").getJSONObject(0).put("retained","reference-b");assertThrows(java.io.IOException.class,()->ReviewedRepair.read(malformed.toString().getBytes(StandardCharsets.UTF_8)));
        assertThrows(java.io.IOException.class,()->ReviewedRepair.read(bytes(retained(),retained())));assertEquals(4,db.all().size());
    }
}
