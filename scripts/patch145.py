from pathlib import Path

main=Path("app/app/src/main/java/com/kocaaslan/istakip/MainActivity.java")
s=main.read_text(encoding="utf-8")
old="root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(18),dp(16),dp(18),dp(24)); root.setBackgroundColor(0xFFF4F8F6); setContentView(root);"
new="root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(18),dp(16),dp(18),dp(24)); root.setBackgroundColor(0xFFF4F8F6); ScrollView mainScroll=new ScrollView(this); mainScroll.setFillViewport(true); mainScroll.addView(root,new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT)); setContentView(mainScroll);"
assert old in s
s=s.replace(old,new,1)
old="LinearLayout.LayoutParams listParams=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f); root.addView(list,listParams);"
new="list.setOnTouchListener((v,e)->{v.getParent().requestDisallowInterceptTouchEvent(true);return false;}); LinearLayout.LayoutParams listParams=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(420)); root.addView(list,listParams);"
assert old in s
s=s.replace(old,new,1)
old="syncListener=CloudSync.listen(db,()->runOnUiThread(()->{if(adapter!=null)refresh();}));"
new="syncListener=CloudSync.listen(db,()->runOnUiThread(()->{if(adapter!=null)refresh();})); for(Transaction t:db.pendingSync()) CloudSync.upload(t);"
assert old in s
s=s.replace(old,new,1)
main.write_text(s,encoding="utf-8")

db=Path("app/app/src/main/java/com/kocaaslan/istakip/DbHelper.java")
s=db.read_text(encoding="utf-8")
marker="    public Transaction byId(long id) {"
method='''    public List<Transaction> pendingSync() {
        List<Transaction> out=new ArrayList<>();
        Cursor c=getReadableDatabase().rawQuery("SELECT id,business,type,amount,category,note,date,sync_id,updated_at FROM "+T+" WHERE sync_state=1 ORDER BY id",null);
        try { while(c.moveToNext()) out.add(new Transaction(c.getLong(0),c.getString(1),c.getString(2),c.getDouble(3),c.getString(4),c.getString(5),c.getLong(6),c.getString(7),c.getLong(8))); }
        finally { c.close(); }
        return out;
    }

'''
assert marker in s
s=s.replace(marker,method+marker,1)
db.write_text(s,encoding="utf-8")
