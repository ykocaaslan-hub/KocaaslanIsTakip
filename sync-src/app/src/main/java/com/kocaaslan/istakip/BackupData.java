package com.kocaaslan.istakip;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Shared backup codec; v3 preserves reversible archive state. */
public final class BackupData {
    public final List<Transaction> rows=new ArrayList<>();
    public boolean missingIdentity,allMissingIdentity=true;
    public static BackupData read(byte[] bytes)throws Exception {
        String text=new String(bytes,StandardCharsets.UTF_8);
        JSONObject root=new JSONObject(text);int version=root.getInt("version");
        if(!"Kocaaslan İş Takip".equals(root.getString("app"))||version<1||version>3)throw new IOException("Uyumsuz yedek");
        BackupData result=new BackupData();JSONArray values=root.getJSONArray("transactions");Set<String> ids=new HashSet<>();
        for(int i=0;i<values.length();i++) {
            JSONObject o=values.getJSONObject(i);String business=o.getString("business"),type=o.getString("type");double amount=o.getDouble("amount");long date=o.getLong("date");
            if((!"Yavuz Kocaaslan".equals(business)&&!"Kocaaslan Kantin".equals(business))||(!"Gelir".equals(type)&&!"Gider".equals(type))||amount<=0||Double.isNaN(amount)||Double.isInfinite(amount)||date<=0)throw new IOException("Geçersiz kayıt: "+(i+1));
            String id=o.optString("syncId","");boolean missing=id.trim().isEmpty();result.missingIdentity|=missing;result.allMissingIdentity&=missing;
            if(version==3&&(missing||!o.has("archived")||!(o.get("archived") instanceof Boolean)))throw new IOException("Arşiv bilgisi/kimliği eksik: "+(i+1));
            if(missing)id=UUID.nameUUIDFromBytes((text+"#"+i).getBytes(StandardCharsets.UTF_8)).toString();
            if(!ids.add(id))throw new IOException("Yedekte aynı işlem kimliği tekrarlanıyor.");
            result.rows.add(new Transaction(0,business,type,amount,o.optString("category","Diğer"),o.optString("note",""),date,id,o.optLong("updatedAt",0),version==3&&o.getBoolean("archived")));
        }
        if(result.rows.isEmpty())throw new IOException("Yedekte işlem kaydı yok. Mevcut kayıtlar değişmedi.");
        return result;
    }
    public static byte[] write(List<Transaction> rows)throws Exception {
        JSONArray values=new JSONArray();
        for(Transaction t:rows) {
            JSONObject o=new JSONObject();o.put("business",t.business);o.put("type",t.type);o.put("amount",t.amount);o.put("category",t.category);o.put("note",t.note);o.put("date",t.date);o.put("syncId",t.syncId);o.put("updatedAt",t.updatedAt);o.put("archived",t.archived);values.put(o);
        }
        JSONObject root=new JSONObject();root.put("app","Kocaaslan İş Takip");root.put("version",3);root.put("transactions",values);
        return root.toString(2).getBytes(StandardCharsets.UTF_8);
    }
}
