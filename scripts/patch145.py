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
new="syncListener=CloudSync.listen(db,()->runOnUiThread(()->{if(adapter!=null)refresh();}));"
assert old in s
s=s.replace(old,new,1)
main.write_text(s,encoding="utf-8")

