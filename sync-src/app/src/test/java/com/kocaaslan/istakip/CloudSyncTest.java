package com.kocaaslan.istakip;

import android.os.Looper;
import com.google.android.gms.tasks.*;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class CloudSyncTest {
    private DbHelper db;private FirebaseFirestore fs;private DocumentReference ref;private DocumentSnapshot remote;
    private MockedStatic<FirebaseFirestore> firestore;private MockedStatic<FirebaseAuth> auth;private MockedStatic<FirebaseApp> app;
    private CloudSync.Session session;private boolean offline;private com.google.firebase.firestore.Transaction tx;
    @Before public void setup()throws Exception{
        RuntimeEnvironment.getApplication().deleteDatabase("kocaaslan_is_takip.db");db=new DbHelper(RuntimeEnvironment.getApplication());
        fs=mock(FirebaseFirestore.class);FirebaseAuth a=mock(FirebaseAuth.class);FirebaseUser u=mock(FirebaseUser.class);when(u.getUid()).thenReturn("shared-uid");when(a.getCurrentUser()).thenReturn(u);
        firestore=mockStatic(FirebaseFirestore.class);firestore.when(FirebaseFirestore::getInstance).thenReturn(fs);
        auth=mockStatic(FirebaseAuth.class);auth.when(FirebaseAuth::getInstance).thenReturn(a);
        app=mockStatic(FirebaseApp.class);FirebaseApp fa=mock(FirebaseApp.class);app.when(FirebaseApp::getInstance).thenReturn(fa);when(fa.getOptions()).thenReturn(new FirebaseOptions.Builder().setApplicationId("test").setProjectId("same-project").setApiKey("test").build());
        CollectionReference users=mock(CollectionReference.class),rows=mock(CollectionReference.class);DocumentReference user=mock(DocumentReference.class);ref=mock(DocumentReference.class);
        when(fs.collection("kullanicilar")).thenReturn(users);when(users.document("shared-uid")).thenReturn(user);when(user.collection("işlemler")).thenReturn(rows);when(user.collection("tanilama")).thenReturn(rows);when(rows.document(anyString())).thenReturn(ref);
        when(rows.addSnapshotListener(eq(MetadataChanges.INCLUDE),any(com.google.firebase.firestore.EventListener.class))).thenReturn(mock(ListenerRegistration.class));
        when(fs.enableNetwork()).thenReturn(Tasks.forResult(null));remote=mock(DocumentSnapshot.class);
        tx=mock(com.google.firebase.firestore.Transaction.class);when(tx.get(ref)).thenReturn(remote);
        when(fs.runTransaction(any(com.google.firebase.firestore.Transaction.Function.class))).thenAnswer(invocation->{
            if(offline)return Tasks.forException(new FirebaseFirestoreException("offline",FirebaseFirestoreException.Code.UNAVAILABLE));
            com.google.firebase.firestore.Transaction.Function<DocumentSnapshot> fn=invocation.getArgument(0);return Tasks.forResult(fn.apply(tx));
        });
        when(ref.set(anyMap())).thenReturn(Tasks.forResult(null));when(ref.get(Source.SERVER)).thenReturn(Tasks.forResult(remote));
    }
    @After public void close(){if(session!=null)session.remove();db.close();app.close();auth.close();firestore.close();}
    private void idle(){Shadows.shadowOf(Looper.getMainLooper()).idle();}
    @Test public void offlineQueueRetriesAndAcknowledgesOnlyAfterServerSuccess(){
        offline=true;db.add("shop","Gider",5,"Kira","",1000);
        session=CloudSync.listen(db,()->{},(ok,e)->{});idle();assertEquals(1,db.pending().size());
        offline=false;session.flush();idle();assertTrue(db.pending().isEmpty());assertEquals(1,db.all().size());
    }
    @Test public void newerServerRecordWinsWithoutDuplicateOrStaleOverwrite(){
        long id=db.add("shop","Gider",5,"Kira","",1000);Transaction local=db.byId(id);
        when(remote.exists()).thenReturn(true);when(remote.getId()).thenReturn(local.syncId);when(remote.getLong("updatedAt")).thenReturn(local.updatedAt+10);when(remote.getLong("date")).thenReturn(1000L);
        when(remote.getString("business")).thenReturn("shop");when(remote.getString("type")).thenReturn("Gider");when(remote.getDouble("amount")).thenReturn(9.0);
        session=CloudSync.listen(db,()->{},(ok,e)->{});idle();assertEquals(9,db.byId(id).amount,0);assertEquals(1,db.all().size());assertTrue(db.pending().isEmpty());
    }
    @Test public void diagnosisKeepsExistingListenerAndFirestoreClientAlive(){
        session=CloudSync.listen(db,()->{},(ok,e)->{});idle();List<Boolean> results=new ArrayList<>();
        CloudSync.diagnose((ok,e)->results.add(ok));idle();assertEquals(Arrays.asList(true),results);verify(fs,never()).terminate();
        db.add("shop","Gider",5,"Kira","",1000);session.flush();idle();assertTrue(db.pending().isEmpty());
    }
    @Test public void removedSessionCannotTouchClosedDatabaseOrRetry(){
        offline=true;db.add("shop","Gider",5,"Kira","",1000);session=CloudSync.listen(db,()->{},(ok,e)->{});
        session.remove();db.close();idle();session.flush();verify(fs,never()).runTransaction(any(com.google.firebase.firestore.Transaction.Function.class));
    }
    @Test public void archiveUploadIncludesFullPayloadAndOfflineQueueSurvives()throws Exception {
        Transaction original=new Transaction(0,"shop","Gider",5,"Kira","same",1000,"original",12),imported=new Transaction(0,"shop","Gider",5,"Kira","same",1000,"import",12);
        db.upsertFromCloud(original);db.upsertFromCloud(imported);db.archiveRepair(db.previewRepair(Arrays.asList(imported)));
        offline=true;session=CloudSync.listen(db,()->{},(ok,e)->{});idle();assertEquals(1,db.pending().size());assertEquals(1,db.archivedCount());
        offline=false;session.flush();idle();assertTrue(db.pending().isEmpty());assertEquals(2,db.all().size());
        org.mockito.ArgumentCaptor<Map> payload=org.mockito.ArgumentCaptor.forClass(Map.class);verify(tx).set(eq(ref),payload.capture(),any(SetOptions.class));
        assertEquals(true,payload.getValue().get("archived"));assertEquals(false,payload.getValue().get("deleted"));assertEquals(5.0,payload.getValue().get("amount"));assertEquals("same",payload.getValue().get("note"));
    }
    @Test public void staleDeviceUploadReceivesNewerRemoteArchiveWithoutRemovingData(){
        long id=db.add("shop","Gider",5,"Kira","same",1000);Transaction local=db.byId(id);
        when(remote.exists()).thenReturn(true);when(remote.getId()).thenReturn(local.syncId);when(remote.getLong("updatedAt")).thenReturn(local.updatedAt+10);when(remote.getLong("date")).thenReturn(1000L);
        when(remote.getString("business")).thenReturn("shop");when(remote.getString("type")).thenReturn("Gider");when(remote.getString("category")).thenReturn("Kira");when(remote.getString("note")).thenReturn("same");when(remote.getDouble("amount")).thenReturn(5.0);when(remote.getBoolean("archived")).thenReturn(true);
        session=CloudSync.listen(db,()->{},(ok,e)->{});idle();assertEquals(1,db.all().size());assertEquals(1,db.archivedCount());assertEquals(0,db.count("shop"));assertTrue(db.pending().isEmpty());verify(tx,never()).set(any(DocumentReference.class),anyMap(),any(SetOptions.class));
    }
    @Test public void downloadedArchiveAndLaterUndoConvergeOnAnotherDevice()throws Exception {
        session=CloudSync.listen(db,()->{},(ok,e)->{});idle();
        java.lang.reflect.Method apply=CloudSync.class.getDeclaredMethod("apply",DbHelper.class,DocumentSnapshot.class);apply.setAccessible(true);
        when(remote.getId()).thenReturn("import");when(remote.getLong("updatedAt")).thenReturn(20L);when(remote.getLong("date")).thenReturn(1000L);when(remote.getMetadata()).thenReturn(mock(SnapshotMetadata.class));
        when(remote.getString("business")).thenReturn("shop");when(remote.getString("type")).thenReturn("Gider");when(remote.getDouble("amount")).thenReturn(5.0);when(remote.getBoolean("archived")).thenReturn(true);
        apply.invoke(null,db,remote);assertEquals(1,db.all().size());assertEquals(1,db.archivedCount());assertTrue(db.pending().isEmpty());
        when(remote.getLong("updatedAt")).thenReturn(21L);when(remote.getBoolean("archived")).thenReturn(false);apply.invoke(null,db,remote);assertEquals(0,db.archivedCount());assertEquals(5,db.totalStats("shop")[1],0);assertEquals(1,db.all().size());
    }

}
