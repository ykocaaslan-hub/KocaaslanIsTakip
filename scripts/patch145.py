from pathlib import Path
main=Path("app/app/src/main/java/com/kocaaslan/istakip/MainActivity.java")
s=main.read_text(encoding="utf-8")
needle="root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(18),dp(14),dp(18),dp(28)); root.setBackgroundColor(0xFF031426); setContentView(root);"
if needle in s:
    repl="root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(18),dp(14),dp(18),dp(28)); root.setBackgroundColor(0xFF031426); ScrollView mainScroll=new ScrollView(this); mainScroll.setFillViewport(true); mainScroll.addView(root,new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT)); setContentView(mainScroll);"
    s=s.replace(needle,repl,1)
main.write_text(s,encoding="utf-8")
