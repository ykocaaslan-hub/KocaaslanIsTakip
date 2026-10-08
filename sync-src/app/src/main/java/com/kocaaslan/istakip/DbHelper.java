package com.kocaaslan.istakip;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class DbHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "kocaaslan_is_takip.db";
    private static final int DB_VERSION = 2;
    private static final String T = "transactions";

    public DbHelper(Context c) { super(c, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "business TEXT NOT NULL," +
                "type TEXT NOT NULL," +
                "amount REAL NOT NULL," +
                "category TEXT," +
                "note TEXT," +
                "date INTEGER NOT NULL," +
                "sync_id TEXT," +
                "updated_at INTEGER NOT NULL DEFAULT 0," +
                "sync_state INTEGER NOT NULL DEFAULT 1)");
        db.execSQL("CREATE INDEX idx_business_date ON " + T + "(business,date)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + T + " ADD COLUMN sync_id TEXT");
            db.execSQL("ALTER TABLE " + T + " ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE " + T + " ADD COLUMN sync_state INTEGER NOT NULL DEFAULT 1");
            db.execSQL("UPDATE " + T + " SET sync_id=lower(hex(randomblob(16))), updated_at=strftime('%s','now')*1000, sync_state=1 WHERE sync_id IS NULL");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_sync_id ON " + T + "(sync_id)");
        }
    }

    public long add(String business, String type, double amount, String category, String note, long date) {
        ContentValues v = new ContentValues();
        v.put("business", business); v.put("type", type); v.put("amount", amount);
        v.put("category", category); v.put("note", note); v.put("date", date);
        v.put("sync_id", java.util.UUID.randomUUID().toString()); v.put("updated_at", System.currentTimeMillis()); v.put("sync_state", 1);
        return getWritableDatabase().insertOrThrow(T, null, v);
    }

    public void update(Transaction t) {
        ContentValues v = new ContentValues();
        v.put("amount",t.amount); v.put("category",t.category); v.put("note",t.note); v.put("date",t.date); v.put("updated_at",System.currentTimeMillis()); v.put("sync_state",1);
        if(getWritableDatabase().update(T,v,"id=?",new String[]{String.valueOf(t.id)})!=1)
            throw new IllegalStateException("Kayıt güncellenemedi");
    }

    public void replaceAll(List<Transaction> rows) {
        SQLiteDatabase database=getWritableDatabase();
        database.beginTransaction();
        try {
            clearAll();
            for(Transaction t:rows) {
                ContentValues v=new ContentValues();
                v.put("business",t.business);v.put("type",t.type);v.put("amount",t.amount);
                v.put("category",t.category);v.put("note",t.note);v.put("date",t.date);
                v.put("sync_id",t.syncId==null||t.syncId.trim().isEmpty()
                        ?java.util.UUID.randomUUID().toString():t.syncId);
                v.put("updated_at",t.updatedAt>0?t.updatedAt:System.currentTimeMillis());
                v.put("sync_state",1);
                database.insertOrThrow(T,null,v);
            }
            database.setTransactionSuccessful();
        } finally { database.endTransaction(); }
    }

    public void delete(long id) { getWritableDatabase().delete(T, "id=?", new String[]{String.valueOf(id)}); }

    public void clearAll() { getWritableDatabase().delete(T, null, null); }

    public double[] stats(String business, long start, long end) {
        double income = 0, expense = 0;
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT type, COALESCE(SUM(amount),0) FROM " + T + " WHERE business=? AND date>=? AND date<? GROUP BY type",
                new String[]{business, String.valueOf(start), String.valueOf(end)});
        try {
            while (c.moveToNext()) {
                if ("Gelir".equals(c.getString(0))) income = c.getDouble(1); else expense = c.getDouble(1);
            }
        } finally { c.close(); }
        return new double[]{income, expense, income - expense};
    }

    public double expenseSum(String business,long start,long end) {
        double total=0;
        String sql="SELECT COALESCE(SUM(amount),0) FROM "+T+" WHERE business=? AND type=?";
        List<String> args=new ArrayList<>(); args.add(business); args.add("Gider");
        if(start>0){sql+=" AND date>=?";args.add(String.valueOf(start));}
        if(end>0){sql+=" AND date<?";args.add(String.valueOf(end));}
        Cursor c=getReadableDatabase().rawQuery(sql,args.toArray(new String[0]));
        try{if(c.moveToFirst())total=c.getDouble(0);}finally{c.close();}
        return total;
    }

    public double[] totalStats(String business) {
        double income = 0, expense = 0;
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT type, COALESCE(SUM(amount),0) FROM " + T + " WHERE business=? GROUP BY type",
                new String[]{business});
        try {
            while (c.moveToNext()) {
                if ("Gelir".equals(c.getString(0))) income = c.getDouble(1); else expense = c.getDouble(1);
            }
        } finally { c.close(); }
        return new double[]{income, expense, income - expense};
    }

    public List<Transaction> list(String business, String search, long start, long end, int limit) {
        List<Transaction> out = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT id,business,type,amount,category,note,date,sync_id,updated_at FROM " + T + " WHERE business=?");
        List<String> args = new ArrayList<>(); args.add(business);
        if (start > 0) { sql.append(" AND date>=?"); args.add(String.valueOf(start)); }
        if (end > 0) { sql.append(" AND date<?"); args.add(String.valueOf(end)); }
        if (search != null && !search.trim().isEmpty()) {
            sql.append(" AND (category LIKE ? OR note LIKE ? OR type LIKE ?)");
            String q = "%" + search.trim() + "%"; args.add(q); args.add(q); args.add(q);
        }
        sql.append(" ORDER BY date DESC,id DESC");
        if (limit > 0) sql.append(" LIMIT ").append(limit);
        Cursor c = getReadableDatabase().rawQuery(sql.toString(), args.toArray(new String[0]));
        try {
            while (c.moveToNext()) out.add(new Transaction(c.getLong(0), c.getString(1), c.getString(2), c.getDouble(3), c.getString(4), c.getString(5), c.getLong(6), c.getString(7), c.getLong(8)));
        } finally { c.close(); }
        return out;
    }

    public List<Transaction> all() {
        List<Transaction> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id,business,type,amount,category,note,date,sync_id,updated_at FROM " + T + " ORDER BY date,id", null);
        try {
            while (c.moveToNext()) out.add(new Transaction(c.getLong(0), c.getString(1), c.getString(2), c.getDouble(3), c.getString(4), c.getString(5), c.getLong(6), c.getString(7), c.getLong(8)));
        } finally { c.close(); }
        return out;
    }


    public Transaction byId(long id) {
        Cursor c=getReadableDatabase().rawQuery("SELECT id,business,type,amount,category,note,date,sync_id,updated_at FROM "+T+" WHERE id=?",new String[]{String.valueOf(id)});
        try { if(c.moveToFirst()) return new Transaction(c.getLong(0),c.getString(1),c.getString(2),c.getDouble(3),c.getString(4),c.getString(5),c.getLong(6),c.getString(7),c.getLong(8)); }
        finally { c.close(); }
        return null;
    }

    public void upsertFromCloud(Transaction t) {
        ContentValues v=new ContentValues();
        v.put("business",t.business);v.put("type",t.type);v.put("amount",t.amount);v.put("category",t.category);v.put("note",t.note);v.put("date",t.date);
        v.put("sync_id",t.syncId);v.put("updated_at",t.updatedAt);v.put("sync_state",0);
        SQLiteDatabase database=getWritableDatabase();
        int n=database.update(T,v,"sync_id=?",new String[]{t.syncId});
        if(n==0){
            // Geri yuklenen yedekte ayni kayit varsa yeni bir kopya eklemek yerine
            // mevcut kaydi bulut kimligiyle eslestir.
            Cursor c=database.rawQuery("SELECT id FROM "+T+" WHERE business=? AND type=? AND amount=? AND IFNULL(category,'')=? AND IFNULL(note,'')=? AND date=? LIMIT 1",
                    new String[]{t.business,t.type,String.valueOf(t.amount),t.category==null?"":t.category,t.note==null?"":t.note,String.valueOf(t.date)});
            try {
                if(c.moveToFirst()) database.update(T,v,"id=?",new String[]{String.valueOf(c.getLong(0))});
                else database.insertOrThrow(T,null,v);
            } finally { c.close(); }
        }
    }

    public void deleteBySyncId(String syncId) { if(syncId!=null)getWritableDatabase().delete(T,"sync_id=?",new String[]{syncId}); }

    public double[][] lastSixMonths(String business) {
        double[][] r = new double[6][2];
        Calendar base = Calendar.getInstance();
        base.set(Calendar.DAY_OF_MONTH, 1); base.set(Calendar.HOUR_OF_DAY,0); base.set(Calendar.MINUTE,0); base.set(Calendar.SECOND,0); base.set(Calendar.MILLISECOND,0);
        base.add(Calendar.MONTH, -5);
        for (int i=0;i<6;i++) {
            Calendar s=(Calendar)base.clone(); s.add(Calendar.MONTH,i);
            Calendar e=(Calendar)s.clone(); e.add(Calendar.MONTH,1);
            double[] st=stats(business,s.getTimeInMillis(),e.getTimeInMillis()); r[i][0]=st[0]; r[i][1]=st[1];
        }
        return r;
    }
}
