package com.kocaaslan.istakip;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** User-reviewed explicit IDs, bound to the source installation and Firebase account. */
public final class ReviewedRepair {
    public static final String APP="Kocaaslan İş Takip İncelenmiş Düzeltme";
    public final List<Transaction> targets,retained;
    private final String installationId,projectId,uid;
    private ReviewedRepair(JSONObject root,List<Transaction> targets,List<Transaction> retained)throws Exception {
        this.targets=Collections.unmodifiableList(targets);this.retained=Collections.unmodifiableList(retained);
        installationId=root.getString("installationId");projectId=root.getString("projectId");uid=root.getString("uid");
        if(installationId.trim().isEmpty()||projectId.trim().isEmpty()||uid.trim().isEmpty())throw new IOException("Cihaz/hesap bilgisi eksik.");
    }
    public void checkDevice(String installation,String project,String user)throws IOException {
        if(!installationId.equals(installation)||!projectId.equals(project)||!uid.equals(user))
            throw new IOException("Bu düzeltme başka cihaz veya hesap için hazırlanmış. Telefonda aynı hesapla uygulayın.");
    }
    public static ReviewedRepair read(byte[] bytes)throws Exception {
        JSONObject root=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
        if(!APP.equals(root.getString("app"))||root.getInt("version")!=1)throw new IOException("Uyumsuz düzeltme dosyası.");
        List<Transaction> targets=rows(root.getJSONArray("targets")),retained=rows(root.getJSONArray("retained"));
        Set<String> ids=new HashSet<>();Map<String,Transaction> refs=new HashMap<>();
        for(Transaction t:retained){ids.add(t.syncId);refs.put(t.syncId,t);}
        for(Transaction t:targets)if(t.archived||!ids.add(t.syncId))throw new IOException("Düzeltme kimlikleri çakışıyor veya hedef zaten arşivde.");
        JSONArray pairs=root.getJSONArray("pairs");Set<String> paired=new HashSet<>(),usedRefs=new HashSet<>();Map<String,Transaction> targetById=new HashMap<>();for(Transaction t:targets)targetById.put(t.syncId,t);
        for(int i=0;i<pairs.length();i++){
            JSONObject pair=pairs.getJSONObject(i);String target=pair.getString("target"),ref=pair.getString("retained");
            if(!paired.add(target)||!targetById.containsKey(target)||!refs.containsKey(ref)||!BackupRepair.key(targetById.get(target)).equals(BackupRepair.key(refs.get(ref))))
                throw new IOException("Düzeltmenin kayıt eşleştirmesi geçersiz.");
            usedRefs.add(ref);
        }
        if(paired.size()!=targets.size()||usedRefs.size()!=retained.size())throw new IOException("Düzeltmenin kimlik listesi eksik.");
        return new ReviewedRepair(root,targets,retained);
    }
    private static List<Transaction> rows(JSONArray rows)throws Exception {
        JSONObject backup=new JSONObject();backup.put("app","Kocaaslan İş Takip");backup.put("version",3);backup.put("transactions",rows);
        List<Transaction> values=BackupData.read(backup.toString().getBytes(StandardCharsets.UTF_8)).rows;
        for(Transaction t:values)if(t.updatedAt<=0)throw new IOException("Kayıt sürümü eksik.");return values;
    }
}
