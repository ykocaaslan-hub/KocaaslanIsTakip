package com.kocaaslan.istakip;

import android.os.Handler;
import android.os.Looper;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CloudSync {
    private CloudSync() {}
    // Firestore's default persistent cache survives process death. Never terminate the shared client.
    private static FirebaseFirestore fs(){ return FirebaseFirestore.getInstance(); }
    public interface Result { void done(boolean ok, String error); }
    public interface Changed { void changed(); }
    public static boolean signedIn(){ return uid()!=null; }
    public static String email(){ FirebaseUser u=FirebaseAuth.getInstance().getCurrentUser();return u==null?null:u.getEmail(); }
    public static String projectId(){ return FirebaseApp.getInstance().getOptions().getProjectId(); }
    public static String uid(){ FirebaseUser u=FirebaseAuth.getInstance().getCurrentUser();return u==null?null:u.getUid(); }
    private static String error(String stage,Exception e){
        String code=e instanceof FirebaseFirestoreException?((FirebaseFirestoreException)e).getCode().name():e==null?"UNKNOWN":e.getClass().getSimpleName();
        return stage+": "+code+(e==null?"":" — "+e.getMessage());
    }
    public static void signIn(String email,String password,Result cb){
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email,password).addOnCompleteListener(t->cb.done(t.isSuccessful(),t.isSuccessful()?null:error("AUTH",t.getException())));
    }
    public static void signOut(){ FirebaseAuth.getInstance().signOut(); }
    public static void reconnect(Result cb){ fs().enableNetwork().addOnCompleteListener(t->cb.done(t.isSuccessful(),t.isSuccessful()?null:error("NETWORK",t.getException()))); }
    public static void diagnose(Result cb){
        final String user=uid(),project=projectId();
        if(user==null){ cb.done(false,"AUTH: Firebase oturumu yok");return; }
        Handler handler=new Handler(Looper.getMainLooper());AtomicBoolean done=new AtomicBoolean();
        Runnable timeout=()->{if(done.compareAndSet(false,true))cb.done(false,"PROJE: "+project+"\nUID: "+user+"\nFIRESTORE: Sunucu yanıtı bekleniyor; yerel kuyruk korunuyor.");};
        handler.postDelayed(timeout,20000);
        fs().enableNetwork().continueWithTask(net->{
            if(!net.isSuccessful())throw net.getException();
            DocumentReference ref=fs().collection("kullanicilar").document(user).collection("tanilama").document("baglanti");
            Map<String,Object> m=new HashMap<>();m.put("test",true);m.put("time",System.currentTimeMillis());
            return ref.set(m).continueWithTask(t->{if(!t.isSuccessful())throw t.getException();return ref.get(Source.SERVER);});
        }).addOnCompleteListener(t->{
            if(done.compareAndSet(false,true)){
                handler.removeCallbacks(timeout);
                cb.done(t.isSuccessful(),"PROJE: "+project+"\nUID: "+user+"\n"+(t.isSuccessful()?"AUTH / FIRESTORE YAZ / OKU: OK":error("FIRESTORE",t.getException())));
            }
        });
    }
    private static CollectionReference rows(String user){ return fs().collection("kullanicilar").document(user).collection("işlemler"); }
    private static Map<String,Object> values(Transaction t){
        Map<String,Object> m=new HashMap<>();m.put("business",t.business);m.put("type",t.type);m.put("amount",t.amount);
        m.put("category",t.category);m.put("note",t.note);m.put("date",t.date);m.put("updatedAt",t.updatedAt);m.put("deleted",false);return m;
    }
    private static void apply(DbHelper local,DocumentSnapshot d){
        Long updated=d.getLong("updatedAt");long version=updated==null?0:updated;
        if(Boolean.TRUE.equals(d.getBoolean("deleted"))){local.applyCloudDeletion(d.getId(),version);return;}
        String business=d.getString("business"),type=d.getString("type"),category=d.getString("category"),note=d.getString("note");
        Double amount=d.getDouble("amount");Long date=d.getLong("date");
        if(business==null||(!"Gelir".equals(type)&&!"Gider".equals(type))||amount==null||(Double.isNaN(amount)||Double.isInfinite(amount))||amount<=0||date==null||date<=0)
            throw new IllegalArgumentException("Geçersiz bulut kaydı: "+d.getId());
        local.upsertFromCloud(new Transaction(0,business,type,amount,category==null?"Diğer":category,note==null?"":note,date,d.getId(),version));
    }

    public static Session listen(DbHelper local,Changed changed,Result status){
        String user=uid();if(user==null)return null;
        return new Session(local,changed,status,user);
    }
    public static final class Session implements ListenerRegistration {
        private final DbHelper local;private final Changed changed;private final Result status;private final String user;
        private final Handler handler=new Handler(Looper.getMainLooper());private final Set<String> inFlight=new HashSet<>();
        private ListenerRegistration listener;private boolean active=true;private String lastError;
        private final Runnable retry=new Runnable(){public void run(){if(valid()){flush();handler.postDelayed(this,10000);}}};
        private Session(DbHelper local,Changed changed,Result status,String user){
            this.local=local;this.changed=changed;this.status=status;this.user=user;
            listener=rows(user).addSnapshotListener(MetadataChanges.INCLUDE,(snap,e)->{
                if(!valid())return;
                if(e!=null){report(error("FIRESTORE LISTEN",e));return;}
                if(snap==null)return;
                for(DocumentSnapshot d:snap.getDocuments()){
                    if(d.getMetadata().hasPendingWrites())continue;
                    try{apply(local,d);}catch(Exception ex){report(error("DOWNLOAD",ex));}
                }
                changed.changed();flush();
            });
            reconnect((ok,e)->{if(valid()){if(!ok)report(e);else flush();}});
            handler.postDelayed(retry,10000);
        }
        private boolean valid(){return active&&user.equals(uid());}
        private void report(String message){if(!message.equals(lastError)){lastError=message;status.done(false,message);}}
        public void flush(){
            if(!valid())return;
            for(Transaction t:local.pending())send(t.syncId,t.updatedAt,values(t),false);
            for(Map.Entry<String,Long> d:local.pendingDeletions().entrySet()){
                Map<String,Object> m=new HashMap<>();m.put("deleted",true);m.put("updatedAt",d.getValue());send(d.getKey(),d.getValue(),m,true);
            }
        }
        private void send(String id,long version,Map<String,Object> value,boolean deletion){
            if(!inFlight.add(id))return;
            DocumentReference ref=rows(user).document(id);
            // Transactions retry concurrent writes and refuse to overwrite a newer remote version.
            // Offline failures leave SQLite's durable outbox intact for reconnect/restart.
            fs().runTransaction(tx->{
                DocumentSnapshot remote=tx.get(ref);Long rv=remote.getLong("updatedAt");
                if(!deletion && remote.exists() && (Boolean.TRUE.equals(remote.getBoolean("deleted")) || (rv!=null&&rv>version)))return remote;
                if(deletion && rv!=null&&rv>version)value.put("updatedAt",rv+1);
                tx.set(ref,value,SetOptions.merge());return (DocumentSnapshot)null;
            }).addOnCompleteListener(task->{
                inFlight.remove(id);if(!valid())return;
                if(!task.isSuccessful()){report(error(deletion?"DELETE":"UPLOAD",task.getException()));return;}
                if(deletion)local.markDeletionSynced(id,version);else local.markSynced(id,version);
                if(task.getResult()!=null){try{apply(local,task.getResult());}catch(Exception ex){report(error("DOWNLOAD",ex));}}
                lastError=null;changed.changed();status.done(true,null);
                // A local edit made during this upload still has a different version and stays pending.
                handler.post(()->{if(valid())flush();});
            });
        }
        @Override public void remove(){active=false;handler.removeCallbacksAndMessages(null);if(listener!=null){listener.remove();listener=null;}}
    }
}
