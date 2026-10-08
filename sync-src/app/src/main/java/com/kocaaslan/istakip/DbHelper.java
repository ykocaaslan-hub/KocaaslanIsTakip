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
    private static final int DB_VERSION = 4;
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
                "sync_state INTEGER NOT NULL DEFAULT 1," +
                "archived INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_business_date ON " + T + "(business,date)");
        createOutbox(db);
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + T + " ADD COLUMN sync_id TEXT");
            db.execSQL("ALTER TABLE " + T + " ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE " + T + " ADD COLUMN sync_state INTEGER NOT NULL DEFAULT 1");
            db.execSQL("UPDATE " + T + " SET sync_id=lower(hex(randomblob(16))), updated_at=strftime('%s','now')*1000, sync_state=1 WHERE sync_id IS NULL");

        }
        if (oldVersion < 3) createOutbox(db);
        if (oldVersion < 4) db.execSQL("ALTER TABLE " + T + " ADD COLUMN archived INTEGER NOT NULL DEFAULT 0");
    }

    private static void createOutbox(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS deleted_transactions (sync_id TEXT PRIMARY KEY, updated_at INTEGER NOT NULL, sync_state INTEGER NOT NULL DEFAULT 1)");
    }

    @Override public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        db.beginTransaction();
        try {
            createOutbox(db);
            // Repair missing identities, preserving every row (including identical transactions).
            db.execSQL("UPDATE " + T + " SET sync_id=lower(hex(randomblob(16))), sync_state=1 WHERE sync_id IS NULL OR trim(sync_id)=''");
            db.execSQL("UPDATE " + T + " SET sync_id=lower(hex(randomblob(16))), sync_state=1 WHERE id NOT IN (SELECT MIN(id) FROM " + T + " GROUP BY sync_id)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_sync_id ON " + T + "(sync_id)");
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
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
        v.put("amount",t.amount); v.put("category",t.category); v.put("note",t.note); v.put("date",t.date); v.put("updated_at",nextVersion(byId(t.id))); v.put("sync_state",1);
        if(getWritableDatabase().update(T,v,"id=?",new String[]{String.valueOf(t.id)})!=1)
            throw new IllegalStateException("Kayıt güncellenemedi");
    }

    private long nextVersion(Transaction t) {
        return Math.max(System.currentTimeMillis(), t == null ? 1 : t.updatedAt + 1);
    }

    // Compatibility entry point: restores now merge instead of clearing existing records.
    public void replaceAll(List<Transaction> rows) { mergeBackup(rows); }

    public static final class RestoreResult {
        public int added, alreadyPresent, previouslyDeleted;
    }

    public RestoreResult mergeBackup(List<Transaction> rows) {
        RestoreResult result=new RestoreResult();
        SQLiteDatabase database=getWritableDatabase();
        database.beginTransaction();
        try {
            for (Transaction t:rows) {
                String identity=t.syncId==null||t.syncId.trim().isEmpty()
                        ?java.util.UUID.randomUUID().toString():t.syncId;
                ContentValues v=values(t); v.put("sync_id",identity);
                v.put("updated_at",t.updatedAt>0?t.updatedAt:System.currentTimeMillis());
                v.put("sync_state",1);
                // A backup may add absent IDs, never overwrite a current row or undo a deletion.
                if(hasDeletion(identity))result.previouslyDeleted++;
                else if(hasIdentity(identity))result.alreadyPresent++;
                else { database.insertOrThrow(T,null,v);result.added++; }
            }
            database.setTransactionSuccessful();
        } finally { database.endTransaction(); }
        return result;
    }

    public int count(String business) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM "+T+" WHERE archived=0 AND business=?",new String[]{business})) {
            return c.moveToFirst()?c.getInt(0):0;
        }
    }

    private boolean hasIdentity(String id) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM "+T+" WHERE sync_id=?",new String[]{id})) {
            return c.moveToFirst();
        }
    }

    public int archivedCount() {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM "+T+" WHERE archived=1",null)) { return c.moveToFirst()?c.getInt(0):0; }
    }

    public BackupRepair.Plan previewRepair(List<Transaction> reference) {
        return BackupRepair.plan(reference,all());
    }

    public int archiveRepair(BackupRepair.Plan preview) {
        SQLiteDatabase database=getWritableDatabase();database.beginTransaction();
        try {
            BackupRepair.Plan current=previewRepair(preview.reference);
            // Recheck every target and retained counterpart after the preview, inside the write transaction.
            if(!preview.sameSelection(current))throw new IllegalStateException("Kayıtlar önizlemeden sonra değişti. Eski yedeği yeniden seçin.");
            for(Transaction t:current.targets) {
                ContentValues v=new ContentValues();v.put("archived",1);v.put("updated_at",nextVersion(t));v.put("sync_state",1);
                if(database.update(T,v,"sync_id=? AND updated_at=? AND archived=0",new String[]{t.syncId,String.valueOf(t.updatedAt)})!=1)
                    throw new IllegalStateException("Kayıt değişti; arşivleme uygulanmadı.");
            }
            database.setTransactionSuccessful();return current.targets.size();
        } finally {database.endTransaction();}
    }

    public int undoArchive() {
        SQLiteDatabase database=getWritableDatabase();database.beginTransaction();
        try {
            int count=0;
            for(Transaction t:all())if(t.archived) {
                ContentValues v=new ContentValues();v.put("archived",0);v.put("updated_at",nextVersion(t));v.put("sync_state",1);
                count+=database.update(T,v,"sync_id=?",new String[]{t.syncId});
            }
            database.setTransactionSuccessful();return count;
        } finally {database.endTransaction();}
    }

    public void delete(long id) {
        SQLiteDatabase database=getWritableDatabase(); database.beginTransaction();
        try {
            Transaction t=byId(id);
            if(t!=null) {
                recordDeletion(t.syncId,nextVersion(t),1);
                database.delete(T,"id=?",new String[]{String.valueOf(id)});
            }
            database.setTransactionSuccessful();
        } finally { database.endTransaction(); }
    }

    public void clearAll() { getWritableDatabase().delete(T, null, null); }

    public double[] stats(String business, long start, long end) {
        double income = 0, expense = 0;
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT type, COALESCE(SUM(amount),0) FROM " + T + " WHERE archived=0 AND business=? AND date>=? AND date<? GROUP BY type",
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
        String sql="SELECT COALESCE(SUM(amount),0) FROM "+T+" WHERE archived=0 AND business=? AND type=?";
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
                "SELECT type, COALESCE(SUM(amount),0) FROM " + T + " WHERE archived=0 AND business=? GROUP BY type",
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
        StringBuilder sql = new StringBuilder("SELECT id,business,type,amount,category,note,date,sync_id,updated_at,archived FROM " + T + " WHERE archived=0 AND business=?");
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
            while (c.moveToNext()) out.add(new Transaction(c.getLong(0), c.getString(1), c.getString(2), c.getDouble(3), c.getString(4), c.getString(5), c.getLong(6), c.getString(7), c.getLong(8), c.getInt(9)!=0));
        } finally { c.close(); }
        return out;
    }

    public List<Transaction> all() {
        List<Transaction> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id,business,type,amount,category,note,date,sync_id,updated_at,archived FROM " + T + " ORDER BY date,id", null);
        try {
            while (c.moveToNext()) out.add(new Transaction(c.getLong(0), c.getString(1), c.getString(2), c.getDouble(3), c.getString(4), c.getString(5), c.getLong(6), c.getString(7), c.getLong(8), c.getInt(9)!=0));
        } finally { c.close(); }
        return out;
    }


    public Transaction byId(long id) {
        Cursor c=getReadableDatabase().rawQuery("SELECT id,business,type,amount,category,note,date,sync_id,updated_at,archived FROM "+T+" WHERE id=?",new String[]{String.valueOf(id)});
        try { if(c.moveToFirst()) return new Transaction(c.getLong(0),c.getString(1),c.getString(2),c.getDouble(3),c.getString(4),c.getString(5),c.getLong(6),c.getString(7),c.getLong(8),c.getInt(9)!=0); }
        finally { c.close(); }
        return null;
    }

    private static ContentValues values(Transaction t) {
        ContentValues v=new ContentValues();
        v.put("business",t.business);v.put("type",t.type);v.put("amount",t.amount);
        v.put("category",t.category);v.put("note",t.note);v.put("date",t.date);
        v.put("sync_id",t.syncId);v.put("updated_at",t.updatedAt);v.put("archived",t.archived?1:0);
        return v;
    }

    private boolean hasDeletion(String id) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT sync_id FROM deleted_transactions WHERE sync_id=?",new String[]{id})) { return c.moveToFirst(); }
    }

    public void upsertFromCloud(Transaction t) {
        if(t.syncId==null||t.syncId.trim().isEmpty())throw new IllegalArgumentException("Missing syncId");
        SQLiteDatabase database=getWritableDatabase(); database.beginTransaction();
        try {
            if(!hasDeletion(t.syncId)) {
                ContentValues v=values(t);v.put("sync_state",0);
                // Dirty local edits cannot be replaced by cached or older cloud snapshots.
                int n=database.update(T,v,"sync_id=? AND sync_state=0 AND updated_at<=?",new String[]{t.syncId,String.valueOf(t.updatedAt)});
                if(n==0)database.insertWithOnConflict(T,null,v,SQLiteDatabase.CONFLICT_IGNORE);
            }
            database.setTransactionSuccessful();
        } finally { database.endTransaction(); }
    }

    public List<Transaction> pending() {
        List<Transaction> rows=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,business,type,amount,category,note,date,sync_id,updated_at,archived FROM "+T+" WHERE sync_state=1",null)) {
            while(c.moveToNext())rows.add(new Transaction(c.getLong(0),c.getString(1),c.getString(2),c.getDouble(3),c.getString(4),c.getString(5),c.getLong(6),c.getString(7),c.getLong(8),c.getInt(9)!=0));
        }
        return rows;
    }

    public void markSynced(String id,long version) {
        ContentValues v=new ContentValues();v.put("sync_state",0);
        getWritableDatabase().update(T,v,"sync_id=? AND updated_at=?",new String[]{id,String.valueOf(version)});
    }

    private void recordDeletion(String id,long version,int state) {
        ContentValues v=new ContentValues();v.put("sync_id",id);v.put("updated_at",version);v.put("sync_state",state);
        getWritableDatabase().insertWithOnConflict("deleted_transactions",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }

    public java.util.Map<String,Long> pendingDeletions() {
        java.util.Map<String,Long> rows=new java.util.HashMap<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT sync_id,updated_at FROM deleted_transactions WHERE sync_state=1",null)) {
            while(c.moveToNext())rows.put(c.getString(0),c.getLong(1));
        }
        return rows;
    }

    public void markDeletionSynced(String id,long version) {
        ContentValues v=new ContentValues();v.put("sync_state",0);
        getWritableDatabase().update("deleted_transactions",v,"sync_id=? AND updated_at=?",new String[]{id,String.valueOf(version)});
    }

    public void applyCloudDeletion(String id,long version) {
        SQLiteDatabase database=getWritableDatabase();database.beginTransaction();
        try {
            try(Cursor c=database.rawQuery("SELECT sync_state,updated_at FROM "+T+" WHERE sync_id=?",new String[]{id})) {
                if(c.moveToFirst() && (c.getInt(0)==1 || c.getLong(1)>version)) return;
            }
            // Only explicit tombstones remove rows; absence from a snapshot never does.
            if(!hasDeletion(id))recordDeletion(id,version,0);
            database.delete(T,"sync_id=? AND sync_state=0 AND updated_at<=?",new String[]{id,String.valueOf(version)});
            database.setTransactionSuccessful();
        } finally { database.endTransaction(); }
    }

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

