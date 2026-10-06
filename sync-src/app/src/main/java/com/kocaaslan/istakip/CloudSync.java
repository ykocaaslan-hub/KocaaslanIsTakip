package com.kocaaslan.istakip;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.*;
import java.util.*;

public final class CloudSync {
    private CloudSync() {}
    public interface Result { void done(boolean ok, String error); }
    public interface Changed { void changed(); }

    public static boolean signedIn(){ try { return FirebaseAuth.getInstance().getCurrentUser()!=null; } catch (Exception e) { return false; } }
    public static String uid(){ try { FirebaseUser u=FirebaseAuth.getInstance().getCurrentUser(); return u==null?null:u.getUid(); } catch (Exception e) { return null; } }
    public static void signIn(String email,String password,Result cb){
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email,password).addOnCompleteListener(t->cb.done(t.isSuccessful(),t.getException()==null?null:t.getException().getMessage()));
    }
    public static void signOut(){ FirebaseAuth.getInstance().signOut(); }
    private static CollectionReference rows(){ return FirebaseFirestore.getInstance().collection("users").document(uid()).collection("transactions"); }

    public static void upload(Transaction t){
        if(!signedIn()||t==null||t.syncId==null)return;
        Map<String,Object> m=new HashMap<>();
        m.put("business",t.business);m.put("type",t.type);m.put("amount",t.amount);m.put("category",t.category);m.put("note",t.note);m.put("date",t.date);m.put("updatedAt",t.updatedAt);
        rows().document(t.syncId).set(m);
    }
    public static void delete(String syncId){ if(signedIn()&&syncId!=null) rows().document(syncId).delete(); }

    public static ListenerRegistration listen(DbHelper local, Changed changed){
        if(!signedIn())return null;
        return rows().addSnapshotListener((snap,e)->{
            if(e!=null||snap==null)return;
            for(DocumentChange dc:snap.getDocumentChanges()){
                DocumentSnapshot d=dc.getDocument();
                if(dc.getType()==DocumentChange.Type.REMOVED){ local.deleteBySyncId(d.getId()); continue; }
                String business=d.getString("business"),type=d.getString("type"),category=d.getString("category"),note=d.getString("note");
                Double amount=d.getDouble("amount");Long date=d.getLong("date"),updated=d.getLong("updatedAt");
                if(business!=null&&type!=null&&amount!=null&&date!=null)
                    local.upsertFromCloud(new Transaction(0,business,type,amount,category==null?"Diğer":category,note==null?"":note,date,d.getId(),updated==null?0:updated));
            }
            changed.changed();
        });
    }
    public static void uploadAll(DbHelper local){ if(signedIn()) for(Transaction t:local.all()) upload(t); }
}
