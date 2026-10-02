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
    private static final int DB_VERSION = 1;
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
                "date INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_business_date ON " + T + "(business,date)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }

    public long add(String business, String type, double amount, String category, String note, long date) {
        ContentValues v = new ContentValues();
        v.put("business", business); v.put("type", type); v.put("amount", amount);
        v.put("category", category); v.put("note", note); v.put("date", date);
        return getWritableDatabase().insertOrThrow(T, null, v);
    }

    public void update(Transaction t) {
        ContentValues v = new ContentValues();
        v.put("amount",t.amount); v.put("category",t.category); v.put("note",t.note); v.put("date",t.date);
        if(getWritableDatabase().update(T,v,"id=?",new String[]{String.valueOf(t.id)})!=1)
            throw new IllegalStateException("Kayıt güncellenemedi");
    }

    public void replaceAll(List<Transaction> rows) {
        SQLiteDatabase database=getWritableDatabase();
        database.beginTransaction();
        try {
            clearAll();
            for(Transaction t:rows) add(t.business,t.type,t.amount,t.category,t.note,t.date);
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
        StringBuilder sql = new StringBuilder("SELECT id,business,type,amount,category,note,date FROM " + T + " WHERE business=?");
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
            while (c.moveToNext()) out.add(new Transaction(c.getLong(0), c.getString(1), c.getString(2), c.getDouble(3), c.getString(4), c.getString(5), c.getLong(6)));
        } finally { c.close(); }
        return out;
    }

    public List<Transaction> all() {
        List<Transaction> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id,business,type,amount,category,note,date FROM " + T + " ORDER BY date,id", null);
        try {
            while (c.moveToNext()) out.add(new Transaction(c.getLong(0), c.getString(1), c.getString(2), c.getDouble(3), c.getString(4), c.getString(5), c.getLong(6)));
        } finally { c.close(); }
        return out;
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
