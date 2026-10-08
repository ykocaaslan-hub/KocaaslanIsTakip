package com.kocaaslan.istakip;

import android.content.Context;
import android.os.*;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Two real, separate SQLite files replay a shared mock server's collection snapshots. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class TwoDeviceSyncTest {
    private DbHelper phone,tablet;private CloudSync.Session ps,ts;private Context context;
    private MockedStatic<FirebaseAuth> auth;private MockedStatic<FirebaseFirestore> firestore;
    private final Map<String,Map<String,Object>> server=new LinkedHashMap<>();
    private final Map<String,DocumentReference> refs=new HashMap<>();
    private final List<com.google.firebase.firestore.EventListener<QuerySnapshot>> listeners=new ArrayList<>();
    private boolean offline;private final Handler handler=new Handler(Looper.getMainLooper());
    @Before @SuppressWarnings("unchecked") public void setup()throws Exception {
        context=RuntimeEnvironment.getApplication();context.deleteDatabase("test-phone.db");context.deleteDatabase("test-tablet.db");phone=new DbHelper(context,"test-phone.db");tablet=new DbHelper(context,"test-tablet.db");
        FirebaseAuth a=mock(FirebaseAuth.class);FirebaseUser u=mock(FirebaseUser.class);when(u.getUid()).thenReturn("same-account");when(a.getCurrentUser()).thenReturn(u);auth=mockStatic(FirebaseAuth.class);auth.when(FirebaseAuth::getInstance).thenReturn(a);
        FirebaseFirestore fs=mock(FirebaseFirestore.class);firestore=mockStatic(FirebaseFirestore.class);firestore.when(FirebaseFirestore::getInstance).thenReturn(fs);
        CollectionReference users=mock(CollectionReference.class),rows=mock(CollectionReference.class);DocumentReference user=mock(DocumentReference.class);when(fs.collection("kullanicilar")).thenReturn(users);when(users.document("same-account")).thenReturn(user);when(user.collection("işlemler")).thenReturn(rows);
        when(rows.document(anyString())).thenAnswer(inv->{String id=inv.getArgument(0);if(!refs.containsKey(id)){DocumentReference ref=mock(DocumentReference.class);when(ref.getId()).thenReturn(id);refs.put(id,ref);}return refs.get(id);});
        when(rows.addSnapshotListener(eq(MetadataChanges.INCLUDE),any(com.google.firebase.firestore.EventListener.class))).thenAnswer(inv->{com.google.firebase.firestore.EventListener<QuerySnapshot> listener=inv.getArgument(1);listeners.add(listener);handler.post(()->listener.onEvent(snapshot(),null));return (ListenerRegistration)()->listeners.remove(listener);});
        when(fs.enableNetwork()).thenReturn(Tasks.forResult(null));
        when(fs.runTransaction(any(com.google.firebase.firestore.Transaction.Function.class))).thenAnswer(inv->{
            if(offline)return Tasks.forException(new FirebaseFirestoreException("offline",FirebaseFirestoreException.Code.UNAVAILABLE));
            com.google.firebase.firestore.Transaction tx=mock(com.google.firebase.firestore.Transaction.class);
            when(tx.get(any(DocumentReference.class))).thenAnswer(read->document(((DocumentReference)read.getArgument(0)).getId()));
            when(tx.set(any(DocumentReference.class),anyMap(),any(SetOptions.class))).thenAnswer(write->{String id=((DocumentReference)write.getArgument(0)).getId();Map<String,Object> merged=new HashMap<>(server.getOrDefault(id,Collections.emptyMap()));merged.putAll((Map<String,Object>)write.getArgument(1));server.put(id,merged);return tx;});
            com.google.firebase.firestore.Transaction.Function<DocumentSnapshot> function=inv.getArgument(0);DocumentSnapshot result=function.apply(tx);handler.post(this::replay);return Tasks.forResult(result);
        });
    }
    @After public void close(){if(ps!=null)ps.remove();if(ts!=null)ts.remove();phone.close();tablet.close();firestore.close();auth.close();}
    private DocumentSnapshot document(String id){
        DocumentSnapshot d=mock(DocumentSnapshot.class);Map<String,Object> data=new HashMap<>(server.getOrDefault(id,Collections.emptyMap()));when(d.getId()).thenReturn(id);when(d.exists()).thenReturn(server.containsKey(id));
        when(d.getLong(anyString())).thenAnswer(i->{Object v=data.get(i.getArgument(0));return v instanceof Number?((Number)v).longValue():null;});when(d.getDouble(anyString())).thenAnswer(i->{Object v=data.get(i.getArgument(0));return v instanceof Number?((Number)v).doubleValue():null;});when(d.getString(anyString())).thenAnswer(i->{Object v=data.get(i.getArgument(0));return v instanceof String?(String)v:null;});when(d.getBoolean(anyString())).thenAnswer(i->{Object v=data.get(i.getArgument(0));return v instanceof Boolean?(Boolean)v:null;});when(d.getMetadata()).thenReturn(mock(SnapshotMetadata.class));return d;
    }
    private QuerySnapshot snapshot(){QuerySnapshot s=mock(QuerySnapshot.class);List<DocumentSnapshot> docs=new ArrayList<>();for(String id:server.keySet())docs.add(document(id));when(s.getDocuments()).thenReturn(docs);return s;}
    private void replay(){QuerySnapshot s=snapshot();for(com.google.firebase.firestore.EventListener<QuerySnapshot> listener:new ArrayList<>(listeners))listener.onEvent(s,null);}
    private void idle(){Shadows.shadowOf(Looper.getMainLooper()).idle();}
    private void start(){ps=CloudSync.listen(phone,()->{},(ok,e)->{});ts=CloudSync.listen(tablet,()->{},(ok,e)->{});idle();}
    private List<Transaction> seed(){List<Transaction> rows=Arrays.asList(new Transaction(0,"shop","Gider",5,"Kira","same",1000,"original",10),new Transaction(0,"shop","Gider",5,"Kira","same",1000,"legacy-import",10));phone.mergeBackup(rows);return rows;}
    @Test public void startupAndRepeatedFullSnapshotsNeverAddNewIdentitiesAcrossTwoDatabases(){
        seed();start();assertEquals(2,server.size());assertEquals(2,tablet.all().size());
        for(int i=0;i<8;i++){ps.remove();ts.remove();phone.close();tablet.close();phone=new DbHelper(context,"test-phone.db");tablet=new DbHelper(context,"test-tablet.db");start();replay();idle();}
        assertEquals(2,phone.all().size());assertEquals(2,tablet.all().size());assertEquals(2,server.size());assertEquals(10,phone.totalStats("shop")[1],0);assertEquals(10,tablet.totalStats("shop")[1],0);
    }
    @Test public void archiveSurvivesSecondDeviceReopenAndOldSnapshotsWithoutDeletingAnyRow(){
        List<Transaction> rows=seed();start();phone.archiveRepair(phone.previewRepair(Collections.singletonList(rows.get(1))));ps.flush();idle();
        assertEquals(1,phone.archivedCount());assertEquals(1,tablet.archivedCount());assertEquals(2,server.size());
        ts.remove();tablet.close();tablet=new DbHelper(context,"test-tablet.db");ts=CloudSync.listen(tablet,()->{},(ok,e)->{});idle();for(int i=0;i<8;i++){replay();idle();}
        assertEquals(2,phone.all().size());assertEquals(2,tablet.all().size());assertEquals(1,phone.count("shop"));assertEquals(1,tablet.count("shop"));assertEquals(5,tablet.totalStats("shop")[1],0);assertTrue(phone.pending().isEmpty());assertTrue(tablet.pending().isEmpty());
    }
    @Test public void offlineArchiveThenRestartReconnectAndUndoConvergeWithStableIds(){
        List<Transaction> rows=seed();start();offline=true;phone.archiveRepair(phone.previewRepair(Collections.singletonList(rows.get(1))));ps.flush();idle();assertEquals(1,phone.pending().size());
        ps.remove();phone.close();phone=new DbHelper(context,"test-phone.db");ps=CloudSync.listen(phone,()->{},(ok,e)->{});idle();assertEquals(1,phone.archivedCount());assertEquals(1,phone.pending().size());
        offline=false;ps.flush();idle();assertEquals(1,tablet.archivedCount());assertEquals(2,tablet.all().size());assertTrue(phone.pending().isEmpty());
        phone.undoArchive();ps.flush();idle();assertEquals(0,tablet.archivedCount());assertEquals(2,phone.count("shop"));assertEquals(2,tablet.count("shop"));assertEquals(2,server.size());
    }
}
