package com.kocaaslan.istakip;

public class Transaction {
    public long id;
    public String business;
    public String type;
    public double amount;
    public String category;
    public String note;
    public long date;
    public String syncId;
    public long updatedAt;
    public boolean archived;

    public Transaction(long id, String business, String type, double amount, String category, String note, long date) {
        this(id,business,type,amount,category,note,date,null,0);
    }

    public Transaction(long id, String business, String type, double amount, String category, String note, long date, String syncId, long updatedAt) {
        this(id,business,type,amount,category,note,date,syncId,updatedAt,false);
    }

    public Transaction(long id, String business, String type, double amount, String category, String note, long date, String syncId, long updatedAt, boolean archived) {
        this.archived=archived;
        this.id=id; this.business=business; this.type=type; this.amount=amount; this.category=category; this.note=note; this.date=date; this.syncId=syncId; this.updatedAt=updatedAt;
    }
}

