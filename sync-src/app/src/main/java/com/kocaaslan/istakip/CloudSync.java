package com.kocaaslan.istakip;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.*;
import com.google.firebase.FirebaseApp;
import java.util.*;

public final class CloudSync {
    private CloudSync() {}
    public interface Result { void done(boolean ok, String error); }
    public interface Changed { void changed(); }
    public interface UploadResult { void done(boolean ok, String error); }

    public static boolean signedIn(){ try { return FirebaseAuth.getInstance().getCurrentUser()!=null; } catch (Exception e) { return false; } }
    public static String email(){ try { FirebaseUser u=FirebaseAuth.getInstance().getCurrentUser(); return u==null?null:u.getEmail(); } catch(Exception e){ return null; } }
    public static String projectId(){ try { return FirebaseApp.getInstance().getOptions().getProjectId(); } catch(Exception e){ return null; } }
    public static String uid(){ try { FirebaseUser u=FirebaseAuth.getInstance().getCurrentUser(); return u==null?null:u.getUid(); } catch (Exception e) { return null; } }
    public static void signIn(String email,String password,Result cb){
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email,password).addOnCompleteListener(t->cb.done(t.isSuccessful(),t.getException()==null?null:t.getException().getMessage()));
    }
    public static void signOut(){ FirebaseAuth.getInstance().signOut(); }
    private static CollectionReference rows(){ return FirebaseFirestore.getInstance().collection("users").document(uid()).collection("transactions"); }

    public static void upload(Transaction t){ upload(t,null); }
    public static void upload(Transaction t, UploadResult cb){
        if(!signedIn()){ if(cb!=null)cb.done(false,"Firebase oturumu yok"); return; }
        if(t==null||t.syncId==null){ if(cb!=null)cb.done(false,"Kayit kimligi yok"); return; }
        Map<String,Object> m=new HashMap<>();
        m.put("business",t.business);m.put("type",t.type);m.put("amount",t.amount);m.put("category",t.category);m.put("note",t.note);m.put("date",t.date);m.put("updatedAt",t.updatedAt);
        rows().document(t.syncId).set(m).addOnCompleteListener(x->{ if(cb!=null)cb.done(x.isSuccessful(),x.getException()==null?null:x.getException().getMessage()); });
    }
    public static void delete(String syncId){ if(signedIn()&&syncId!=null) rows().document(syncId).delete(); }

    private static String fingerprint(DocumentSnapshot d){
        String business=d.getString("business"),type=d.getString("type"),category=d.getString("category"),note=d.getString("note");
        Double amount=d.getDouble("amount"); Long date=d.getLong("date");
        return String.valueOf(business)+"|"+String.valueOf(type)+"|"+String.valueOf(amount)+"|"+String.valueOf(category)+"|"+String.valueOf(note)+"|"+String.valueOf(date);
    }

    public static ListenerRegistration listen(DbHelper local, Changed changed){
        if(!signedIn())return null;
        return rows().addSnapshotListener((snap,e)->{
            if(e!=null||snap==null)return;

            // 1.4.2: Bulutta ayni kaydin tekrar tekrar olusmasini temizle.
            Map<String,DocumentSnapshot> keep=new LinkedHashMap<>();
            for(DocumentSnapshot d:snap.getDocuments()){
                String key=fingerprint(d);
                DocumentSnapshot old=keep.get(key);
                if(old==null) keep.put(key,d);
                else {
                    Long oldUpdated=old.getLong("updatedAt"),newUpdated=d.getLong("updatedAt");
                    long ou=oldUpdated==null?0:oldUpdated, nu=newUpdated==null?0:newUpdated;
                    if(nu<ou){ rows().document(old.getId()).delete(); keep.put(key,d); }
                    else rows().document(d.getId()).delete();
                }
            }

            for(DocumentSnapshot d:keep.values()){
                String business=d.getString("business"),type=d.getString("type"),category=d.getString("category"),note=d.getString("note");
                Double amount=d.getDouble("amount");Long date=d.getLong("date"),updated=d.getLong("updatedAt");
                if(business!=null&&type!=null&&amount!=null&&date!=null)
                    local.upsertFromCloud(new Transaction(0,business,type,amount,category==null?"Diğer":category,note==null?"":note,date,d.getId(),updated==null?0:updated));
            }
            changed.changed();
        });
    }

    // Eski surumlerle uyumluluk icin birakildi; 1.4.2 acilista bunu cagirmiyor.
    public static void uploadAll(DbHelper local){ if(signedIn()) for(Transaction t:local.all()) upload(t); }
}
