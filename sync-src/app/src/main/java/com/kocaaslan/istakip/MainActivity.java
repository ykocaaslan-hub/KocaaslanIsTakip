package com.kocaaslan.istakip;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;


import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity implements TransactionAdapter.DeleteListener {
    private static final String B1="Yavuz Kocaaslan", B2="Kocaaslan Kantin";
    private static final int REQ_CSV=1001, REQ_BACKUP=1002, REQ_RESTORE=1003, REQ_REPAIR=1004;
    private DbHelper db; private String business=B1; private int dashboardPeriod=0; private LinearLayout root, recordsBox;
    private TextView titleBusiness, dayIncome,dayExpense,dayNet, weekIncome,weekExpense,weekNet, monthIncome,monthExpense,monthNet, totalIncome,totalExpense,totalNet;
    private Button b1,b2; private EditText search; private Spinner filter; private ChartView chart; private TransactionAdapter adapter; private ListView list;
    private NumberFormat money; private SharedPreferences prefs;
    private long exportStart=0, exportEnd=0, customStart=0, customEnd=0;
    private String savedSearch=""; private int savedFilter=1;
    private CloudSync.Session syncListener;
    private boolean destroyed=false;
    private TextView recordsStatus;private Button allRecordsButton;

    @Override public void onCreate(Bundle b){ super.onCreate(b); db=new DbHelper(this); prefs=getSharedPreferences("settings",MODE_PRIVATE); money=NumberFormat.getCurrencyInstance(new Locale("tr","TR"));
        if(b!=null){business=b.getString("business",B1);savedSearch=b.getString("search","");savedFilter=b.getInt("filter",1);dashboardPeriod=b.getInt("dashboardPeriod",0);customStart=b.getLong("customStart",0);customEnd=b.getLong("customEnd",0);exportStart=b.getLong("exportStart",0);exportEnd=b.getLong("exportEnd",0);}
        String pin=prefs.getString("pin",""); buildUi(); try { if(CloudSync.signedIn()) startCloudSync(); else promptCloudLogin(pin); } catch(Exception e) { Toast.makeText(this,"Senkronizasyon şu anda kullanılamıyor; yerel kayıtlarınız korunuyor.",Toast.LENGTH_LONG).show(); }
    }

    @Override protected void onSaveInstanceState(Bundle state){
        super.onSaveInstanceState(state);state.putInt("dashboardPeriod",dashboardPeriod);state.putString("business",business);state.putString("search",search==null?savedSearch:search.getText().toString());state.putInt("filter",filter==null?savedFilter:filter.getSelectedItemPosition());state.putLong("customStart",customStart);state.putLong("customEnd",customEnd);state.putLong("exportStart",exportStart);state.putLong("exportEnd",exportEnd);
    }
    @Override protected void onResume(){super.onResume();if(adapter!=null)refresh();if(db!=null&&CloudSync.signedIn()&&syncListener==null)startCloudSync();}
    @Override protected void onStop(){if(syncListener!=null){syncListener.remove();syncListener=null;}super.onStop();}
    @Override protected void onDestroy(){destroyed=true;if(syncListener!=null)syncListener.remove();if(db!=null)db.close();super.onDestroy();}


    private void promptCloudLogin(String pin){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(22),dp(8),dp(22),0);
        EditText email=new EditText(this);email.setHint("E-posta");email.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);box.addView(email);
        EditText pass=new EditText(this);pass.setHint("Şifre");pass.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);box.addView(pass);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Bulut senkronizasyonu").setMessage("Telefon ve tablette aynı Firebase hesabıyla giriş yapın.").setView(box).setCancelable(false).setPositiveButton("Giriş yap",null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String e=email.getText().toString().trim(),p=pass.getText().toString();
            if(e.isEmpty()){email.setError("E-posta gerekli");return;} if(p.isEmpty()){pass.setError("Şifre gerekli");return;}
            CloudSync.signIn(e,p,(ok,err)->runOnUiThread(()->{if(ok){d.dismiss();startCloudSync();if(pin.isEmpty())buildUi();else promptPin(pin);}else Toast.makeText(this,"Giriş başarısız: "+err,Toast.LENGTH_LONG).show();}));
        }));d.show();
    }

    private void startCloudSync(){
        if(destroyed||!CloudSync.signedIn())return;
        if(syncListener!=null)syncListener.remove();
        syncListener=CloudSync.listen(db,()->{if(!destroyed&&adapter!=null)refresh();},(ok,err)->{
            if(!destroyed&&!ok)Toast.makeText(this,"Senkron bekliyor: "+err+". Yerel kayıtlar korunuyor.",Toast.LENGTH_LONG).show();
        });
    }
    private void syncTransaction(long id){
        if(syncListener!=null)syncListener.flush();
        else if(CloudSync.signedIn())startCloudSync();
    }

    private void promptPin(String expected){
        final EditText in=new EditText(this); in.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD); in.setHint("4 haneli PIN"); in.setPadding(dp(18),dp(12),dp(18),dp(12));
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Kocaaslan İş Takip").setMessage("Uygulamayı açmak için PIN'inizi girin.").setView(in).setCancelable(false).setPositiveButton("Aç",null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{ if(expected.equals(in.getText().toString())){d.dismiss();buildUi();} else {in.setError("PIN yanlış");in.setText("");} })); d.show();
    }

    private void buildUi(){
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(18),dp(14),dp(18),dp(28)); root.setBackgroundColor(0xFF031426); LinearLayout frame=new LinearLayout(this);frame.setOrientation(LinearLayout.VERTICAL);frame.setBackgroundColor(0xFF031426);
        ScrollView mainScroll=new ScrollView(this);mainScroll.setFillViewport(true);
        mainScroll.addView(root,new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        frame.addView(mainScroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));setContentView(frame);
        frame.setOnApplyWindowInsetsListener((view,insets)->{view.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;});frame.requestApplyInsets();

        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(12),dp(12),dp(12),dp(12));GradientDrawable hg=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0xFF06213D,0xFF0B3764});hg.setCornerRadius(dp(22));header.setBackground(hg);header.setElevation(dp(10));
        TextView menu=txt("☰",24,Color.WHITE,true);menu.setGravity(Gravity.CENTER);menu.setMinimumWidth(dp(48));menu.setMinimumHeight(dp(48));menu.setContentDescription("Ayarlar");header.addView(menu,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout htxt=new LinearLayout(this);htxt.setOrientation(LinearLayout.VERTICAL);htxt.addView(txt("Kocaaslan İş Takip",20,0xFFFFD21F,true));htxt.addView(txt("“Daima Fenerbahçe”",16,0xFFFFD21F,true));htxt.addView(txt("1907",12,0xFFFFD21F,true));header.addView(htxt,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));menu.setOnClickListener(v->settingsDialog());root.addView(header,mp(0,0,0,12));

        LinearLayout businesses=new LinearLayout(this);businesses.setOrientation(LinearLayout.HORIZONTAL);
        b1=button("YK • 1. İşletme\n"+B1,0xFF0B2C50,Color.WHITE);b2=button("KK • 2. İşletme\n"+B2,0xFF0B2C50,Color.WHITE);
        b1.setTextSize(15);b2.setTextSize(15);b1.setGravity(Gravity.CENTER_VERTICAL);b2.setGravity(Gravity.CENTER_VERTICAL);
        b1.setOnClickListener(v->switchBusiness(B1));b2.setOnClickListener(v->switchBusiness(B2));addFlexible(businesses,b1,72);addFlexible(businesses,b2,72);root.addView(businesses,mp(0,0,0,12));

        titleBusiness=txt(business,1,0xFF031426,false);titleBusiness.setVisibility(View.GONE);root.addView(titleBusiness,new LinearLayout.LayoutParams(1,1));

        LinearLayout tabs=new LinearLayout(this);tabs.setOrientation(LinearLayout.VERTICAL);String[] tabNames={"Bugün","Bu Hafta","Bu Ay","Tümü"};
        LinearLayout tabRow=null;
        for(int k=0;k<4;k++){
            if(k%4==0){tabRow=new LinearLayout(this);tabs.addView(tabRow,mp(0,k==0?0:6,0,0));}
            final int period=k;Button tb=button(tabNames[k],k==dashboardPeriod?0xFFFFD21F:0xFF082846,k==dashboardPeriod?0xFF061A33:Color.WHITE);tb.setTextSize(14);
            tb.setOnClickListener(v->{savedSearch=search.getText().toString();dashboardPeriod=period;savedFilter=period==3?0:period+1;buildUi();});addFlexible(tabRow,tb,48);
        }
        root.addView(tabs,mp(0,0,0,12));

        LinearLayout stats=new LinearLayout(this);stats.setOrientation(LinearLayout.HORIZONTAL);
        dayIncome=summaryCard(stats,"Gelir","₺0",0xFF00D968);dayExpense=summaryCard(stats,"Gider","₺0",0xFFFF3B4D);dayNet=summaryCard(stats,"Net","₺0",0xFF1B8CFF);
        weekIncome=txt("",1,Color.TRANSPARENT,false);weekExpense=txt("",1,Color.TRANSPARENT,false);weekNet=txt("",1,Color.TRANSPARENT,false);monthIncome=txt("",1,Color.TRANSPARENT,false);monthExpense=txt("",1,Color.TRANSPARENT,false);monthNet=txt("",1,Color.TRANSPARENT,false);
        root.addView(stats,mp(0,0,0,12));

        LinearLayout quick=new LinearLayout(this);quick.setOrientation(LinearLayout.HORIZONTAL);
        Button addI=button("＋ Gelir ekle",0xFF009D50,Color.WHITE),addE=button("＋ Gider ekle",0xFFD51E2B,Color.WHITE);addI.setTextSize(17);addE.setTextSize(17);addI.setOnClickListener(v->showAdd("Gelir"));addE.setOnClickListener(v->showAdd("Gider"));addFlexible(quick,addI,64);addFlexible(quick,addE,64);root.addView(quick,mp(0,0,0,12));

        LinearLayout total= (LinearLayout)totalCard(); total.setVisibility(View.GONE); root.addView(total,new LinearLayout.LayoutParams(1,1));
        Button monthlyHistory=button("Detay Gör  ›",0xFF0B2C50,Color.WHITE);monthlyHistory.setOnClickListener(v->showMonthlyHistory());

        LinearLayout chartHead=new LinearLayout(this);chartHead.setOrientation(compactLayout()?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);chartHead.setGravity(Gravity.CENTER_VERTICAL);
        addFlexible(chartHead,txt("Son 6 Ayın Gelir - Gider Durumu",18,Color.WHITE,true),0);addFlexible(chartHead,monthlyHistory,48);root.addView(chartHead,mp(0,0,0,6));
        LinearLayout legend=new LinearLayout(this);legend.setGravity(Gravity.RIGHT);legend.addView(txt("● Gelir     ",12,0xFF20E777,true));legend.addView(txt("● Gider",12,0xFFFF4B55,true));root.addView(legend);
        chart=new ChartView(this);chart.setBackground(cardBg(0xFF082846));chart.setElevation(dp(8));root.addView(chart,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(235)));

        LinearLayout section=new LinearLayout(this);section.setOrientation(compactLayout()?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);section.setGravity(Gravity.CENTER_VERTICAL);section.setPadding(0,dp(10),0,dp(6));addFlexible(section,txt("Son İşlemler",20,Color.WHITE,true),0);
        search=new EditText(this);search.setHint("Kayıtlarda ara");search.setSingleLine(true);search.setTextColor(Color.WHITE);search.setHintTextColor(0xFFB7C5D3);search.setBackground(cardBg(0xFF082846));search.setPadding(dp(14),dp(8),dp(14),dp(8));addFlexible(section,search,48);root.addView(section);
        filter=new Spinner(this);ArrayAdapter<String> fa=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Tümü","Bugün","Bu hafta","Bu ay","Özel tarih"});filter.setAdapter(fa);filter.setVisibility(View.GONE);root.addView(filter,new LinearLayout.LayoutParams(1,1));
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){refreshList();}public void afterTextChanged(Editable e){}});
        filter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){if(pos==4&&customEnd==0)chooseRange();else refreshList();}public void onNothingSelected(android.widget.AdapterView<?> p){}});

        recordsStatus=txt("",14,0xFFB7C5D3,false);root.addView(recordsStatus,mp(0,4,0,6));
        allRecordsButton=button("Tüm kayıtları göster",0xFF0B2C50,Color.WHITE);
        allRecordsButton.setOnClickListener(v->showAllRecords());root.addView(allRecordsButton,mp(0,0,0,6));

        list=new ListView(this);list.setDivider(null);list.setDividerHeight(0);list.setNestedScrollingEnabled(false);adapter=new TransactionAdapter(this,this);list.setAdapter(adapter);list.setOnItemClickListener((parent,view,position,id)->showForm(adapter.getItem(position).type,adapter.getItem(position)));LinearLayout.LayoutParams listParams=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(360));root.addView(list,listParams);

        LinearLayout bottom=new LinearLayout(this);bottom.setOrientation(LinearLayout.VERTICAL);String[] nav={"Ana Sayfa","Kayıtlar","Raporlar","Genel Toplamlar"};LinearLayout navRow=null;
        int navColumns=4;
        for(int k=0;k<nav.length;k++){
            if(k%navColumns==0){navRow=new LinearLayout(this);bottom.addView(navRow,mp(0,k==0?0:4,0,0));}
            Button nb=button(nav[k],k==0?0xFF0B2C50:0xFF061C34,k==0?0xFFFFD21F:Color.WHITE);nb.setTextSize(13);
            if(k==0)nb.setOnClickListener(v->mainScroll.smoothScrollTo(0,0));else if(k==1)nb.setOnClickListener(v->{refreshList();mainScroll.smoothScrollTo(0,recordsStatus.getTop());});else if(k==2)nb.setOnClickListener(v->showCategoryReport());else if(k==3)nb.setOnClickListener(v->showGeneralBreakdown());addFlexible(navRow,nb,48);
        }
        frame.addView(bottom,mp(0,0,0,0));

        LinearLayout acts=new LinearLayout(this);acts.setOrientation(LinearLayout.HORIZONTAL);
        Button csv=button("CSV / Excel",0xFF0B2C50,Color.WHITE),backup=button("Yedekle",0xFF0B2C50,Color.WHITE),restore=button("Geri Yükle",0xFF0B2C50,Color.WHITE);
        csv.setOnClickListener(v->beginCsv());backup.setOnClickListener(v->beginBackup());restore.setOnClickListener(v->beginRestore());addFlexible(acts,csv,48);addFlexible(acts,backup,48);addFlexible(acts,restore,48);root.addView(acts);
        Button repair=button("Çift kayıtları düzelt",0xFF0B2C50,Color.WHITE);repair.setOnClickListener(v->beginRepair());root.addView(repair,mp(0,8,0,0));
        search.setText(savedSearch);filter.setSelection(savedFilter);switchBusiness(business);
    }

    private boolean largeText(){return getResources().getConfiguration().fontScale>1.25f;}
    private boolean compactLayout(){return getResources().getConfiguration().screenWidthDp<600||largeText();}
    private LinearLayout.LayoutParams valueParams(){return new LinearLayout.LayoutParams(compactLayout()?ViewGroup.LayoutParams.MATCH_PARENT:0,ViewGroup.LayoutParams.WRAP_CONTENT,compactLayout()?0:1);}
    private void addFlexible(LinearLayout parent,View child,int minHeight){
        boolean vertical=parent.getOrientation()==LinearLayout.VERTICAL;
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(vertical?ViewGroup.LayoutParams.MATCH_PARENT:0,ViewGroup.LayoutParams.WRAP_CONTENT,vertical?0:1);
        if(parent.getChildCount()>0)params.setMargins(vertical?0:dp(8),vertical?dp(8):0,0,0);
        child.setMinimumHeight(dp(minHeight));parent.addView(child,params);
    }
    private TextView summaryCard(LinearLayout parent,String label,String value,int accent){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(8),dp(12),dp(8),dp(12));GradientDrawable g=bg(0xFF092B4D,18);g.setStroke(dp(2),accent);card.setBackground(g);card.setElevation(dp(9));
        card.addView(txt(label,16,Color.WHITE,true),mp(0,0,0,0));TextView v=txt(value,23,accent,true);card.addView(v,mp(0,4,0,0));addFlexible(parent,card,0);card.getLayoutParams().height=ViewGroup.LayoutParams.MATCH_PARENT;card.setOnClickListener(view->showSelectedTotals());return v;
    }

    private View periodCard(String label,int which){ LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(13),dp(13),dp(13),dp(13));box.setBackground(cardBg(0xFF0A2748));box.setElevation(dp(7));TextView l=txt(label,12,0xFFFFD21F,true);box.addView(l);TextView i=txt("Gelir 0",13,0xFF3EEA83,true),e=txt("Gider 0",13,0xFFFF5B63,true),n=txt("Net 0",17,0xFF4DB5FF,true);box.addView(i);box.addView(e);box.addView(n);if(which==0){dayIncome=i;dayExpense=e;dayNet=n;}else if(which==1){weekIncome=i;weekExpense=e;weekNet=n;}else{monthIncome=i;monthExpense=e;monthNet=n;}return box; }

    private View totalCard(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(16),dp(14),dp(16),dp(14));
        GradientDrawable g=bg(0xFF0A2748,16);g.setStroke(dp(2),0xFFFFD21F);box.setBackground(g);box.setElevation(dp(8));
        TextView l=txt("GENEL TOPLAM",14,0xFFFFD21F,true);box.addView(l);
        TextView sub=txt("İlk kayıttan bugüne • Detay için dokunun",12,0xFFB8C8D8,false);box.addView(sub);
        LinearLayout vals=new LinearLayout(this);vals.setOrientation(compactLayout()?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);vals.setPadding(0,dp(8),0,0);
        totalIncome=txt("Gelir  0",16,0xFF13835D,true);totalExpense=txt("Gider  0",16,0xFFD94A4A,true);totalNet=txt("Net  0",18,0xFF4DB5FF,true);
        vals.addView(totalIncome,valueParams());
        vals.addView(totalExpense,valueParams());
        vals.addView(totalNet,valueParams());
        box.addView(vals);box.setOnClickListener(v->showGeneralBreakdown());return box;
    }

    private void showGeneralBreakdown(){
        List<Transaction> rows=db.list(business,"",0,0,0);
        TreeMap<Integer,TreeMap<Integer,double[]>> years=new TreeMap<>(Collections.reverseOrder());
        double allIncome=0,allExpense=0;
        Calendar cal=Calendar.getInstance();
        for(Transaction t:rows){
            cal.setTimeInMillis(t.date);int y=cal.get(Calendar.YEAR),m=cal.get(Calendar.MONTH);
            TreeMap<Integer,double[]> months=years.get(y);if(months==null){months=new TreeMap<>();years.put(y,months);}
            double[] v=months.get(m);if(v==null){v=new double[]{0,0};months.put(m,v);}
            if("Gelir".equals(t.type)){v[0]+=t.amount;allIncome+=t.amount;}else{v[1]+=t.amount;allExpense+=t.amount;}
        }
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(14),dp(8),dp(14),dp(8));
        content.addView(txt(business+" — "+rows.size()+" kayıt, tüm tarihler",17,0xFF173C35,true),mp(0,0,0,10));
        if(years.isEmpty())content.addView(txt("Henüz kayıt bulunmuyor.",14,0xFF62746F,false));
        String[] monthNames=new java.text.DateFormatSymbols(new Locale("tr","TR")).getMonths();
        for(Map.Entry<Integer,TreeMap<Integer,double[]>> ye:years.entrySet()){
            int year=ye.getKey();double yi=0,yea=0;
            TextView yh=txt(String.valueOf(year),20,0xFF173C35,true);content.addView(yh,mp(0,8,0,6));
            for(Map.Entry<Integer,double[]> me:ye.getValue().entrySet()){
                double[] v=me.getValue();yi+=v[0];yea+=v[1];double net=v[0]-v[1];
                LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(12),dp(10),dp(12),dp(10));card.setBackground(cardBg(0xFFFFFFFF));
                String mn=monthNames[me.getKey()];mn=mn.substring(0,1).toUpperCase(new Locale("tr","TR"))+mn.substring(1);card.addView(txt(mn,16,0xFF173C35,true));
                LinearLayout vals=new LinearLayout(this);vals.setOrientation(compactLayout()?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);vals.setPadding(0,dp(6),0,0);
                vals.addView(txt("Ciro\n"+money.format(v[0]),13,0xFF13835D,true),valueParams());
                vals.addView(txt("Gider\n"+money.format(v[1]),13,0xFFD94A4A,true),valueParams());
                vals.addView(txt("Net\n"+money.format(net),13,net>=0?0xFF13835D:0xFFD94A4A,true),valueParams());
                card.addView(vals);content.addView(card,mp(0,0,0,6));
            }
            double yn=yi-yea;LinearLayout yt=new LinearLayout(this);yt.setOrientation(LinearLayout.VERTICAL);yt.setPadding(dp(14),dp(11),dp(14),dp(11));GradientDrawable yg=bg(0xFFEAF4F0,14);yg.setStroke(dp(1),0xFFB8D7CD);yt.setBackground(yg);
            yt.addView(txt(year+" YILLIK TOPLAM",15,0xFF315E54,true));yt.addView(txt("Ciro: "+money.format(yi)+"   Gider: "+money.format(yea)+"   Net: "+money.format(yn),14,yn>=0?0xFF13835D:0xFFD94A4A,true));content.addView(yt,mp(0,0,0,14));
        }
        double allNet=allIncome-allExpense;LinearLayout grand=new LinearLayout(this);grand.setOrientation(LinearLayout.VERTICAL);grand.setPadding(dp(16),dp(14),dp(16),dp(14));GradientDrawable gg=bg(0xFFFFF2C7,16);gg.setStroke(dp(1),0xFFE0BE58);grand.setBackground(gg);
        grand.addView(txt("TÜM YILLAR GENEL TOPLAMI — "+rows.size()+" kayıt",16,0xFF7A5B12,true));grand.addView(txt("Ciro: "+money.format(allIncome)+"\nGider: "+money.format(allExpense)+"\nNet: "+money.format(allNet),16,allNet>=0?0xFF13835D:0xFFD94A4A,true));content.addView(grand,mp(0,4,0,8));
        ScrollView sv=new ScrollView(this);sv.addView(content);AlertDialog d=new AlertDialog.Builder(this).setTitle("Genel Toplam Detayı").setView(sv).setPositiveButton("Kapat",null).create();d.setOnShowListener(x->d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,(int)(getResources().getDisplayMetrics().heightPixels*0.86)));d.show();
    }

    private void showMonthlyHistory(){
        List<Transaction> rows=db.list(business,"",0,0,0);
        TreeMap<String,double[]> months=new TreeMap<>(Collections.reverseOrder());
        TreeMap<String,Long> monthDates=new TreeMap<>(Collections.reverseOrder());
        SimpleDateFormat keyFmt=new SimpleDateFormat("yyyy-MM",Locale.US);
        for(Transaction t:rows){
            String key=keyFmt.format(new Date(t.date));
            double[] v=months.get(key);if(v==null){v=new double[]{0,0};months.put(key,v);monthDates.put(key,t.date);}
            if("Gelir".equals(t.type))v[0]+=t.amount;else v[1]+=t.amount;
        }
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(14),dp(8),dp(14),dp(8));
        TextView who=txt(business,16,0xFF173C35,true);content.addView(who,mp(0,0,0,8));
        if(months.isEmpty())content.addView(txt("Henüz aylık kayıt bulunmuyor.",14,0xFF62746F,false));
        SimpleDateFormat monthFmt=new SimpleDateFormat("MMMM yyyy",new Locale("tr","TR"));
        for(Map.Entry<String,double[]> entry:months.entrySet()){
            double[] v=entry.getValue();double net=v[0]-v[1];
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(14),dp(12),dp(14),dp(12));card.setBackground(cardBg(0xFFFFFFFF));
            String label=monthFmt.format(new Date(monthDates.get(entry.getKey())));label=label.substring(0,1).toUpperCase(new Locale("tr","TR"))+label.substring(1);
            card.addView(txt(label,17,0xFF173C35,true));
            LinearLayout vals=new LinearLayout(this);vals.setOrientation(compactLayout()?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);vals.setPadding(0,dp(7),0,0);
            vals.addView(txt("Ciro\n"+money.format(v[0]),14,0xFF13835D,true),valueParams());
            vals.addView(txt("Gider\n"+money.format(v[1]),14,0xFFD94A4A,true),valueParams());
            TextView netView=txt("Net\n"+money.format(net),14,net>=0?0xFF13835D:0xFFD94A4A,true);vals.addView(netView,valueParams());
            card.addView(vals);content.addView(card,mp(0,0,0,8));
        }
        ScrollView sv=new ScrollView(this);sv.addView(content);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Aylık Geçmiş").setView(sv).setPositiveButton("Kapat",null).create();d.setOnShowListener(x->d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,(int)(getResources().getDisplayMetrics().heightPixels*0.82)));d.show();
    }

    private void switchBusiness(String b){business=b;titleBusiness.setText(b);boolean first=B1.equals(b);b1.setBackground(bg(first?0xFF1F9D72:0xFFE6ECE9,14));b1.setTextColor(first?Color.WHITE:0xFF24413B);b2.setBackground(bg(!first?0xFF1F9D72:0xFFE6ECE9,14));b2.setTextColor(!first?Color.WHITE:0xFF24413B);refresh();}

    private void refresh(){ Calendar now=Calendar.getInstance(); long day=startOfDay(now).getTimeInMillis();Calendar d2=(Calendar)startOfDay(now).clone();d2.add(Calendar.DAY_OF_MONTH,1);long dayEnd=d2.getTimeInMillis();Calendar ws=startOfWeek(now);Calendar we=(Calendar)ws.clone();we.add(Calendar.DAY_OF_MONTH,7);Calendar ms=startOfMonth(now);Calendar me=(Calendar)ms.clone();me.add(Calendar.MONTH,1);double[] selected=dashboardPeriod==0?db.stats(business,day,dayEnd):dashboardPeriod==1?db.stats(business,ws.getTimeInMillis(),we.getTimeInMillis()):dashboardPeriod==2?db.stats(business,ms.getTimeInMillis(),me.getTimeInMillis()):db.totalStats(business);long expenseStart=dashboardPeriod==0?day:dashboardPeriod==1?ws.getTimeInMillis():dashboardPeriod==2?ms.getTimeInMillis():0;long expenseEnd=dashboardPeriod==0?dayEnd:dashboardPeriod==1?we.getTimeInMillis():dashboardPeriod==2?me.getTimeInMillis():0;selected[1]=db.expenseSum(business,expenseStart,expenseEnd);selected[2]=selected[0]-selected[1];setStats(selected,dayIncome,dayExpense,dayNet);setStats(db.stats(business,ws.getTimeInMillis(),we.getTimeInMillis()),weekIncome,weekExpense,weekNet);setStats(db.stats(business,ms.getTimeInMillis(),me.getTimeInMillis()),monthIncome,monthExpense,monthNet);setStats(db.totalStats(business),totalIncome,totalExpense,totalNet);chart.setData(db.lastSixMonths(business));refreshList();}
    private void setStats(double[] s,TextView i,TextView e,TextView n){i.setText(money.format(s[0]));e.setText(money.format(s[1]));n.setText(money.format(s[2]));i.setTextSize(16);e.setTextSize(16);n.setTextSize(16);i.setSingleLine(false);e.setSingleLine(false);n.setSingleLine(false);n.setTextColor(s[2]>=0?0xFF13835D:0xFFD94A4A);}
    private void showSelectedTotals(){
        new AlertDialog.Builder(this).setTitle(business+" — "+String.valueOf(filter.getSelectedItem()))
            .setMessage("Gelir: "+dayIncome.getText()+"\nGider: "+dayExpense.getText()+"\nNet: "+dayNet.getText())
            .setPositiveButton("Kapat",null).show();
    }
    private void refreshList(){
        if(adapter==null)return;
        long[] r=filterRange();adapter.setItems(db.list(business,search==null?"":search.getText().toString(),r[0],r[1],0));resizeRecordList();
        int total=db.count(business),visible=adapter.getCount();
        boolean narrowed=(filter!=null&&filter.getSelectedItemPosition()!=0)||(search!=null&&!search.getText().toString().trim().isEmpty());
        String period=filter==null?"Tümü":String.valueOf(filter.getSelectedItem());
        if(recordsStatus!=null){
            String message=business+" — "+visible+" / "+total+" kayıt gösteriliyor ("+period+")";
            if(visible==0&&total>0)message+="\nBu seçime uyan kayıt yok. Tüm kayıtları gösterebilirsiniz.";
            if(total==0){String other=B1.equals(business)?B2:B1;int otherCount=db.count(other);if(otherCount>0)message+="\n"+other+": "+otherCount+" kayıt; üstten bu işletmeyi seçebilirsiniz.";}
            recordsStatus.setText(message);
        }
        if(allRecordsButton!=null)allRecordsButton.setVisibility(narrowed&&visible<total?View.VISIBLE:View.GONE);
    }

    private void showAllRecords(){
        savedSearch="";savedFilter=0;dashboardPeriod=3;customStart=0;customEnd=0;
        buildUi();
        root.post(()->{if(!destroyed&&root.getParent() instanceof ScrollView)((ScrollView)root.getParent()).smoothScrollTo(0,recordsStatus.getTop());});
    }

    private void resizeRecordList(){
        if(list==null||adapter==null)return;
        int count=adapter.getCount();
        int width=Math.max(1,getResources().getDisplayMetrics().widthPixels-dp(36));
        int wanted=0;
        for(int i=0;i<count;i++){
            View row=adapter.getView(i,null,list);
            row.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
            wanted+=row.getMeasuredHeight();
        }
        wanted=Math.max(dp(180),wanted+list.getPaddingTop()+list.getPaddingBottom());
        ViewGroup.LayoutParams p=list.getLayoutParams();
        if(p!=null){p.height=wanted;list.setLayoutParams(p);}
        list.setNestedScrollingEnabled(false);
    }
    private long[] filterRange(){int p=filter==null?0:filter.getSelectedItemPosition();Calendar now=Calendar.getInstance();if(p==4)return new long[]{customStart,customEnd};if(p==1){Calendar s=startOfDay(now),e=(Calendar)s.clone();e.add(Calendar.DAY_OF_MONTH,1);return new long[]{s.getTimeInMillis(),e.getTimeInMillis()};}if(p==2){Calendar s=startOfWeek(now),e=(Calendar)s.clone();e.add(Calendar.DAY_OF_MONTH,7);return new long[]{s.getTimeInMillis(),e.getTimeInMillis()};}if(p==3){Calendar s=startOfMonth(now),e=(Calendar)s.clone();e.add(Calendar.MONTH,1);return new long[]{s.getTimeInMillis(),e.getTimeInMillis()};}return new long[]{0,0};}

    private void showAdd(String type){showForm(type,null);}
    private void showForm(String type,Transaction existing){ final Calendar selected=Calendar.getInstance();if(existing!=null)selected.setTimeInMillis(existing.date); LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(20),dp(8),dp(20),0);TextView who=txt(business,15,0xFF1F5A4D,true);form.addView(who);EditText amount=new EditText(this);amount.setHint("Tutar (₺)");amount.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);form.addView(amount);Spinner cat=new Spinner(this);String[] incomeCats={"Satış","Hizmet","Diğer Gelir"};String[] expenseCats={"Mal Alımı","Kira","Personel","Fatura","Vergi","Ulaşım","Bakım/Onarım","Diğer Gider"};cat.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,"Gelir".equals(type)?incomeCats:expenseCats));form.addView(cat);EditText note=new EditText(this);note.setHint("Açıklama (isteğe bağlı)");form.addView(note);Button dateBtn=button("Tarih: "+fmtDate(selected.getTimeInMillis()),0xFFE8F0ED,0xFF24413B);dateBtn.setOnClickListener(v->new DatePickerDialog(this,(view,y,m,d)->{selected.set(y,m,d,12,0,0);selected.set(Calendar.MILLISECOND,0);dateBtn.setText("Tarih: "+fmtDate(selected.getTimeInMillis()));},selected.get(Calendar.YEAR),selected.get(Calendar.MONTH),selected.get(Calendar.DAY_OF_MONTH)).show());form.addView(dateBtn,mp(0,8,0,0));if(existing!=null){amount.setText(Double.toString(existing.amount));note.setText(existing.note);String[] cats="Gelir".equals(type)?incomeCats:expenseCats;for(int k=0;k<cats.length;k++)if(cats[k].equals(existing.category))cat.setSelection(k);}AlertDialog dlg=new AlertDialog.Builder(this).setTitle(type+(existing==null?" Ekle":" Düzenle")).setView(form).setNegativeButton("Vazgeç",null).setPositiveButton("Kaydet",null).create();
        final boolean[] committed={false};
        dlg.setOnShowListener(x->dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(committed[0])return;
            long savedId;
            try{
                double val=Double.parseDouble(amount.getText().toString().trim().replace(",", "."));
                if(val<=0||Double.isNaN(val)||Double.isInfinite(val))throw new IllegalArgumentException();
                if(existing==null)savedId=db.add(business,type,val,String.valueOf(cat.getSelectedItem()),note.getText().toString().trim(),selected.getTimeInMillis());
                else {db.update(new Transaction(existing.id,existing.business,existing.type,val,String.valueOf(cat.getSelectedItem()),note.getText().toString().trim(),selected.getTimeInMillis()));savedId=existing.id;}
            }catch(IllegalArgumentException ex){amount.setError("Geçerli bir tutar girin");return;}
            catch(Exception ex){Toast.makeText(this,"Kayıt kaydedilemedi",Toast.LENGTH_LONG).show();return;}
            // A committed local write is final for this form, regardless of the later network result.
            committed[0]=true;dlg.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);dlg.dismiss();refresh();
            try{syncTransaction(savedId);}catch(Exception ex){Toast.makeText(this,"Kayıt cihazda kaydedildi; bulut aktarımı bekliyor.",Toast.LENGTH_LONG).show();}
        }));dlg.show();
    }


    @Override public void onDelete(Transaction t){new AlertDialog.Builder(this).setTitle("Kaydı sil").setMessage(fmtDate(t.date)+" tarihli "+t.category+" kaydı silinsin mi?").setNegativeButton("Vazgeç",null).setPositiveButton("Sil",(d,w)->{db.delete(t.id);syncTransaction(t.id);refresh();}).show();}

    private void settingsDialog(){String pin=prefs.getString("pin","");String[] opts={pin.isEmpty()?"PIN kilidi oluştur":"PIN kilidini değiştir","PIN kilidini kaldır","Tarih aralığı seç","Kategori raporu","Senkron Bilgisi","Uygulama hakkında","Arşivdeki kayıtlar"};new AlertDialog.Builder(this).setTitle("Ayarlar").setItems(opts,(d,which)->{if(which==0)setPin();else if(which==1){prefs.edit().remove("pin").apply();Toast.makeText(this,"PIN kilidi kaldırıldı",Toast.LENGTH_SHORT).show();}else if(which==2)chooseRange();else if(which==3)showCategoryReport();else if(which==4)showSyncInfo();else if(which==6)showArchive();else new AlertDialog.Builder(this).setTitle("Kocaaslan İş Takip v"+appVersion()).setMessage("Paket: "+BuildConfig.BUILD_REVISION+"\n\nYavuz Kocaaslan ve Kocaaslan Kantin için kişisel ciro-gider ve kâr/zarar takip uygulaması. Veriler cihazda saklanır.").setPositiveButton("Tamam",null).show();}).show();}
    private void showSyncInfo(){
        String project=CloudSync.projectId(), email=CloudSync.email(), uid=CloudSync.uid();
        String msg="Paket: "+appVersion()+" / "+BuildConfig.BUILD_REVISION+"\n\n"+localTotals()+"\nArşivde: "+db.archivedCount()+"\nAktarım bekleyen: "+db.pending().size()+"\n\nDurum: "+(CloudSync.signedIn()?"GİRİŞ YAPILMIŞ":"GİRİŞ YOK")+"\n\nFirebase Projesi: "+String.valueOf(project)+"\nE-posta: "+String.valueOf(email)+"\nUID: "+String.valueOf(uid)+"\n\nBağlantı testi için TEST ET düğmesine basın.";
        AlertDialog dlg=new AlertDialog.Builder(this).setTitle("Senkron Bilgisi").setMessage(msg).setNegativeButton("Kapat",null).setNeutralButton("Hesabı Yeniden Bağla",null).setPositiveButton("TEST ET",null).create();
        dlg.setOnShowListener(x->{dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{dlg.dismiss();try{if(syncListener!=null){syncListener.remove();syncListener=null;}CloudSync.signOut();}catch(Exception ignored){}promptCloudLogin(prefs.getString("pin",""));});dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{dlg.setMessage("Firebase sunucusu test ediliyor...");CloudSync.diagnose((ok,error)->runOnUiThread(()->dlg.setMessage((ok?"BAŞARILI\n\n":"HATA\n\n")+String.valueOf(error))));});});dlg.show();
    }
    private void setPin(){EditText in=new EditText(this);in.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);in.setHint("4 haneli PIN");new AlertDialog.Builder(this).setTitle("PIN oluştur").setView(in).setNegativeButton("Vazgeç",null).setPositiveButton("Kaydet",(d,w)->{String p=in.getText().toString();if(p.matches("[0-9]{4}")){prefs.edit().putString("pin",p).apply();Toast.makeText(this,"PIN kaydedildi",Toast.LENGTH_SHORT).show();}else Toast.makeText(this,"PIN 4 rakam olmalı",Toast.LENGTH_LONG).show();}).show();}

    private void beginCsv(){long[] r=filterRange();exportStart=r[0];exportEnd=r[1];Intent i=new Intent("android.intent.action.CREATE_DOCUMENT");i.setType("text/csv");i.putExtra(Intent.EXTRA_TITLE,"Kocaaslan_"+safe(business)+"_"+new SimpleDateFormat("yyyyMMdd",Locale.US).format(new Date())+".csv");startActivityForResult(i,REQ_CSV);}
    private String appVersion(){
        try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception e){return "?";}
    }
    private void beginBackup(){Intent i=new Intent("android.intent.action.CREATE_DOCUMENT");i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"Kocaaslan_IsTakip_Yedek_"+new SimpleDateFormat("yyyyMMdd_HHmm",Locale.US).format(new Date())+".json");startActivityForResult(i,REQ_BACKUP);}
    private void beginRestore(){Intent i=new Intent("android.intent.action.OPEN_DOCUMENT");i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,REQ_RESTORE);}

    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri u=data.getData();try{if(req==REQ_CSV)writeCsv(u);else if(req==REQ_BACKUP)writeBackup(u);else if(req==REQ_RESTORE)restoreBackup(u);else if(req==REQ_REPAIR)repairBackup(u);}catch(Exception e){if(req==REQ_RESTORE||req==REQ_REPAIR)new AlertDialog.Builder(this).setTitle(req==REQ_REPAIR?"Arşivleme yapılamadı":"Geri yükleme yapılamadı").setMessage("Mevcut kayıtlar değişmedi.\n"+e.getMessage()).setPositiveButton("Tamam",null).show();else Toast.makeText(this,"İşlem tamamlanamadı: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
    private void writeCsv(Uri u)throws Exception{List<Transaction> rows=db.list(business,search==null?"":search.getText().toString(),exportStart,exportEnd,0);OutputStream os=getContentResolver().openOutputStream(u);OutputStreamWriter w=new OutputStreamWriter(os,StandardCharsets.UTF_8);w.write('\uFEFF');w.write("Tarih;İşletme;Tür;Kategori;Açıklama;Tutar\n");for(Transaction t:rows)w.write(csv(fmtDate(t.date))+";"+csv(t.business)+";"+csv(t.type)+";"+csv(t.category)+";"+csv(t.note)+";"+String.format(Locale.US,"%.2f",t.amount).replace('.',',')+"\n");w.close();Toast.makeText(this,"CSV kaydedildi",Toast.LENGTH_SHORT).show();}
    private void writeBackup(Uri u)throws Exception{
        try(OutputStream os=getContentResolver().openOutputStream(u)){
            if(os==null)throw new IOException("Yedek dosyası açılamadı");os.write(diagnosticBackup());
        }
        Toast.makeText(this,"Yedek kaydedildi; arşivdeki kayıtlar da korundu",Toast.LENGTH_SHORT).show();
    }
    private byte[] diagnosticBackup()throws Exception {
        JSONObject backup=new JSONObject(new String(BackupData.write(db.all()),StandardCharsets.UTF_8));
        String device=prefs.getString("installation_id",null);
        if(device==null){device=UUID.randomUUID().toString();prefs.edit().putString("installation_id",device).commit();}
        JSONObject info=new JSONObject();info.put("installationId",device);info.put("buildRevision",BuildConfig.BUILD_REVISION);info.put("versionName",appVersion());
        JSONArray pending=new JSONArray();for(Transaction t:db.pending())pending.put(t.syncId);info.put("pendingSyncIds",pending);info.put("archivedCount",db.archivedCount());
        try{info.put("projectId",CloudSync.projectId());info.put("uid",CloudSync.uid());}catch(Exception ex){info.put("authStatus","unavailable");}
        backup.put("diagnostics",info);return backup.toString(2).getBytes(StandardCharsets.UTF_8);
    }
    private BackupData readBackup(Uri u)throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(InputStream is=getContentResolver().openInputStream(u)){
            if(is==null)throw new IOException("Yedek açılamadı");byte[] buf=new byte[4096];int n;
            while((n=is.read(buf))!=-1){if(out.size()+n>10*1024*1024)throw new IOException("Yedek çok büyük");out.write(buf,0,n);}
        }
        return BackupData.read(out.toByteArray());
    }
    private void restoreBackup(Uri u)throws Exception{
        BackupData backup=readBackup(u);List<Transaction> rows=backup.rows;boolean missingIdentity=backup.missingIdentity;
        String preview="Mevcut kayıtlar korunarak yedekteki "+rows.size()+" kayıt birleştirilecek.\n\n"+backupTotals(rows)+"\n\nAynı kimlikteki mevcut kayıtlar korunur; önceden silinen kayıtlar otomatik geri getirilmez. Devam edilsin mi?";
        Runnable applyRestore=()->{
            DbHelper.RestoreResult result;
            try{result=db.mergeBackup(rows);}catch(Exception e){new AlertDialog.Builder(this).setTitle("Geri yükleme başarısız").setMessage("Mevcut kayıtlar korundu.\n"+String.valueOf(e.getMessage())).setPositiveButton("Tamam",null).show();return;}
            // Data import is complete. Display it before attempting network operations.
            boolean selectedInBackup=false;for(Transaction t:rows)if(business.equals(t.business)){selectedInBackup=true;break;}
            if(!selectedInBackup)business=rows.get(0).business;
            showAllRecords();
            String summary="Yedek geri yüklendi.\nYeni eklenen: "+result.added+"\nAynı kimlikle zaten bulunan: "+result.alreadyPresent+"\nÖnceden silinmiş olduğu için eklenmeyen: "+result.previouslyDeleted+"\n\nCihazdaki tüm kayıtlar (mevcut + eklenen):\n"+localTotals()+"\nArşivde korunan: "+db.archivedCount()+"\n\nTümü seçildi, arama temizlendi. İşletmeler arasında üstten geçebilirsiniz.";
            new AlertDialog.Builder(this).setTitle("Geri yükleme sonucu").setMessage(summary).setPositiveButton("Tamam",null).show();
            try{syncTransaction(0);}catch(Exception e){Toast.makeText(this,"Kayıtlar cihazda yüklendi; bulut aktarımı bekliyor: "+e.getMessage(),Toast.LENGTH_LONG).show();}
        };
        final boolean legacy=missingIdentity;
        new AlertDialog.Builder(this).setTitle("Yedeği geri yükle").setMessage(preview).setNegativeButton("Vazgeç",null).setPositiveButton("Yükle",(d,w)->{
            boolean cloudPossible=true;
            try{cloudPossible=CloudSync.signedIn();}catch(Exception ignored){}
            if(legacy&&(!db.all().isEmpty()||cloudPossible)){
                new AlertDialog.Builder(this).setTitle("Bu yedek ikinci kopyalar oluşturabilir")
                    .setMessage("Bu eski yedekte kayıt kimlikleri yok. Cihazınızdaki veya buluttaki aynı kayıtlar yeniden eklenip toplamları artırabilir.\n\nÖnce mevcut kayıtları yedekleyip eski yedekle karşılaştırın. Aynı içerikli kayıtları otomatik silmiyoruz. Yalnızca bu kayıtların ayrı işlemler olduğunu doğruladıysanız ekleyin.")
                    .setNegativeButton("Vazgeç",null).setNeutralButton("Mevcut kayıtları yedekle",(warning,which)->beginBackup())
                    .setPositiveButton("Ayrı işlemler olarak ekle",(warning,which)->applyRestore.run()).show();
            }else applyRestore.run();
        }).show();
    }

    private void beginRepair(){
        new AlertDialog.Builder(this).setTitle("Çift kayıtları düzelt")
            .setMessage("Kopyalar oluşmadan önce aldığınız eski yedeği seçin (örneğin 10:30 yedeği). Bu dosya geri yüklenmeyecek; geri yüklemenin eklediği kopyaların kimlikleri doğrulanacak.\n\nKopyalar silinmeden arşivde korunacak, gelir/gider toplamlarına katılmayacak. Yeni veya değişmiş işlemler korunur.")
            .setNegativeButton("Vazgeç",null).setPositiveButton("Eski yedeği seç",(d,w)->{
                Intent i=new Intent("android.intent.action.OPEN_DOCUMENT");i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,REQ_REPAIR);
            }).show();
    }
    private void saveRepairSnapshot()throws Exception {
        File file=File.createTempFile("Arsivleme_Oncesi_",".json",getFilesDir());
        try(FileOutputStream out=new FileOutputStream(file)){out.write(BackupData.write(db.all()));out.getFD().sync();}
    }
    private void repairBackup(Uri u)throws Exception {
        BackupData source=readBackup(u);
        if(!source.allMissingIdentity)throw new IOException("Kopyalar oluşmadan önceki kimliksiz eski yedeği seçin. Güncel yedek bu işlem için kullanılamaz.");
        BackupRepair.Plan plan=db.previewRepair(source.rows);
        String details="Arşivlenecek kopya: "+plan.targets.size()+"\nZaten arşivde: "+plan.alreadyArchived+"\nKimliği bulunmayan: "+plan.missing+"\nİçeriği değişmiş, korunacak: "+plan.changed+"\nAsıl kaydı doğrulanamayan, korunacak: "+plan.withoutOriginal;
        if(plan.targets.isEmpty()){
            new AlertDialog.Builder(this).setTitle("Arşivlenecek kopya bulunamadı").setMessage(details+"\n\nMevcut kayıtlar değişmedi. Daha önce arşivlenmiş kayıtlar yeniden işlenmez.").setPositiveButton("Tamam",null).show();return;
        }
        List<Transaction> activeAfter=new ArrayList<>();Set<String> ids=new HashSet<>();for(Transaction t:plan.targets)ids.add(t.syncId);
        for(Transaction t:db.all())if(!t.archived&&!ids.contains(t.syncId))activeAfter.add(t);
        String preview=details+"\n\nArşivlemeden sonra aktif kayıtlar:\n"+backupTotals(activeAfter)+"\n\nKayıtlar silinmeyecek. Arşiv yedeklere dahil edilir; Ayarlar → Arşivdeki kayıtlar bölümünden geri alınabilir. Telefon ve tablette bu güncelleme kurulu olmalı ve aynı hesaba giriş yapılmalı. İşlem öncesi yedek cihazda ayrıca saklanacak.";
        new AlertDialog.Builder(this).setTitle("Kopyaları arşivle") .setMessage(preview).setNegativeButton("Vazgeç",null)
            .setNeutralButton("Mevcut kayıtları yedekle",(d,w)->beginBackup())
            .setPositiveButton("Kopyaları arşivle",(d,w)->{
                int count;
                try{saveRepairSnapshot();count=db.archiveRepair(plan);}catch(Exception e){
                    new AlertDialog.Builder(this).setTitle("Arşivleme uygulanmadı").setMessage("Kayıtlar değişmedi.\n"+e.getMessage()).setPositiveButton("Tamam",null).show();return;
                }
                showAllRecords();
                new AlertDialog.Builder(this).setTitle("Kopyalar arşivlendi")
                    .setMessage(count+" kopya silinmeden arşivde korundu.\n\nAktif kayıtlar:\n"+localTotals()+"\n\nBulut aktarımı kuyrukta; bağlantı olduğunda diğer güncel cihaz da aynı arşiv durumunu alır. Yedekle düğmesi arşivdeki kayıtları da saklar.").setPositiveButton("Tamam",null).show();
                try{syncTransaction(0);}catch(Exception e){Toast.makeText(this,"Arşiv cihazda korundu; bulut aktarımı bekliyor: "+e.getMessage(),Toast.LENGTH_LONG).show();}
            }).show();
    }
    private void showArchive(){
        int count=db.archivedCount();
        new AlertDialog.Builder(this).setTitle("Arşivdeki kayıtlar")
            .setMessage(count+" kayıt arşivde saklanıyor ve toplamların dışında tutuluyor. Yedekle düğmesi bunları da dosyaya ekler.\n\nArşivi geri almak kopyaları yeniden toplamların içine getirir.")
            .setNegativeButton("Kapat",null).setNeutralButton("Yedekle",(d,w)->beginBackup())
            .setPositiveButton("Arşivi geri al",(d,w)->{
                if(count==0)return;
                new AlertDialog.Builder(this).setTitle("Arşivi geri al")
                    .setMessage("Arşivdeki tüm kayıtlar yeniden aktif olacak; kopyalar toplamları artırabilir. Devam edilsin mi?")
                    .setNegativeButton("Vazgeç",null).setPositiveButton("Geri al",(confirm,which)->{
                        try{saveRepairSnapshot();int restored=db.undoArchive();showAllRecords();new AlertDialog.Builder(this).setTitle("Arşiv geri alındı").setMessage(restored+" kayıt yeniden aktif.\n"+localTotals()).setPositiveButton("Tamam",null).show();}
                        catch(Exception e){new AlertDialog.Builder(this).setTitle("Geri alma yapılamadı").setMessage(String.valueOf(e.getMessage())).setPositiveButton("Tamam",null).show();return;}
                        try{syncTransaction(0);}catch(Exception e){Toast.makeText(this,"Bulut aktarımı bekliyor",Toast.LENGTH_LONG).show();}
                    }).show();
            }).show();
    }

    private String backupTotals(List<Transaction> rows){
        StringBuilder text=new StringBuilder("Aktif kayıtların işletme toplamları:");
        for(String name:new String[]{B1,B2}){
            int count=0;double income=0,expense=0;
            for(Transaction t:rows)if(!t.archived&&name.equals(t.business)){count++;if("Gelir".equals(t.type))income+=t.amount;else expense+=t.amount;}
            text.append("\n").append(name).append(": ").append(count).append(" kayıt\nGelir: ").append(money.format(income)).append("   Gider: ").append(money.format(expense)).append("   Net: ").append(money.format(income-expense));
        }
        return text.toString();
    }
    private String localTotals(){
        StringBuilder text=new StringBuilder();
        for(String name:new String[]{B1,B2}){
            double[] totals=db.totalStats(name);
            if(text.length()>0)text.append("\n");
            text.append(name).append(": ").append(db.count(name)).append(" kayıt\nGelir: ").append(money.format(totals[0])).append("   Gider: ").append(money.format(totals[1])).append("   Net: ").append(money.format(totals[2]));
        }
        return text.toString();
    }

    private void chooseRange(){
        Calendar start=Calendar.getInstance(),end=Calendar.getInstance();
        new DatePickerDialog(this,(v,y,m,d)->{
            start.set(y,m,d,0,0,0);start.set(Calendar.MILLISECOND,0);
            new DatePickerDialog(this,(v2,y2,m2,d2)->{
                end.set(y2,m2,d2,0,0,0);end.set(Calendar.MILLISECOND,0);
                if(end.before(start)){Toast.makeText(this,"Bitiş tarihi başlangıçtan önce olamaz",Toast.LENGTH_LONG).show();return;}
                end.add(Calendar.DAY_OF_MONTH,1);customStart=start.getTimeInMillis();customEnd=end.getTimeInMillis();filter.setSelection(4);refreshList();
            },end.get(Calendar.YEAR),end.get(Calendar.MONTH),end.get(Calendar.DAY_OF_MONTH)).show();
        },start.get(Calendar.YEAR),start.get(Calendar.MONTH),start.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showCategoryReport(){
        long[] range=filterRange();Map<String,double[]> groups=new TreeMap<>();
        for(Transaction t:db.list(business,search.getText().toString(),range[0],range[1],0)){
            String category=t.category==null?"Diğer":t.category;double[] totals=groups.get(category);if(totals==null){totals=new double[2];groups.put(category,totals);}totals["Gelir".equals(t.type)?0:1]+=t.amount;
        }
        StringBuilder report=new StringBuilder(business+"\nSeçili dönem ve arama sonuçları\n\n");
        for(Map.Entry<String,double[]> entry:groups.entrySet()){double[] t=entry.getValue();report.append(entry.getKey()).append("\nGelir: ").append(money.format(t[0])).append("\nGider: ").append(money.format(t[1])).append("\nNet: ").append(money.format(t[0]-t[1])).append("\n\n");}
        if(groups.isEmpty())report.append("Kayıt bulunmuyor.");
        TextView text=txt(report.toString(),15,0xFF173C35,false);text.setPadding(dp(18),dp(12),dp(18),dp(12));ScrollView scroll=new ScrollView(this);scroll.addView(text);
        new AlertDialog.Builder(this).setTitle("Kategori raporu").setView(scroll).setPositiveButton("Kapat",null).show();
    }

    private Calendar startOfDay(Calendar c){Calendar x=(Calendar)c.clone();x.set(Calendar.HOUR_OF_DAY,0);x.set(Calendar.MINUTE,0);x.set(Calendar.SECOND,0);x.set(Calendar.MILLISECOND,0);return x;}
    private Calendar startOfWeek(Calendar c){Calendar x=startOfDay(c);int dow=x.get(Calendar.DAY_OF_WEEK);int delta=(dow==Calendar.SUNDAY)?-6:(Calendar.MONDAY-dow);x.add(Calendar.DAY_OF_MONTH,delta);return x;}
    private Calendar startOfMonth(Calendar c){Calendar x=startOfDay(c);x.set(Calendar.DAY_OF_MONTH,1);return x;}
    private String fmtDate(long m){return new SimpleDateFormat("dd.MM.yyyy",Locale.US).format(new Date(m));}
    private String csv(String s){if(s==null)s="";return "\""+s.replace("\"","\"\"")+"\"";}
    private String safe(String s){return s.replace(" ","_").replaceAll("[^A-Za-z0-9_ğüşöçıİĞÜŞÖÇ]","");}
    private TextView txt(String s,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setSingleLine(false);t.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_SIMPLE);t.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private Button button(String s,int bg,int fg){Button b=new Button(this);b.setText(s);b.setTextColor(fg);b.setTextSize(13);b.setAllCaps(false);b.setSingleLine(false);b.setMaxLines(Integer.MAX_VALUE);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(10),dp(10),dp(10),dp(10));b.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_SIMPLE);b.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackground(bg(bg,14));return b;}
    private GradientDrawable bg(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private GradientDrawable cardBg(int color){GradientDrawable g=bg(color,16);g.setStroke(dp(1),0xFFE2EAE7);return g;}
    private LinearLayout.LayoutParams mp(int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
}

