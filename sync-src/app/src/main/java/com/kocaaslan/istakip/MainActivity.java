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
import com.google.firebase.firestore.ListenerRegistration;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity implements TransactionAdapter.DeleteListener {
    private static final String B1="Yavuz Kocaaslan", B2="Kocaaslan Kantin";
    private static final int REQ_CSV=1001, REQ_BACKUP=1002, REQ_RESTORE=1003;
    private DbHelper db; private String business=B1; private LinearLayout root, recordsBox;
    private TextView titleBusiness, dayIncome,dayExpense,dayNet, weekIncome,weekExpense,weekNet, monthIncome,monthExpense,monthNet, totalIncome,totalExpense,totalNet;
    private Button b1,b2; private EditText search; private Spinner filter; private ChartView chart; private TransactionAdapter adapter; private ListView list;
    private NumberFormat money; private SharedPreferences prefs;
    private long exportStart=0, exportEnd=0, customStart=0, customEnd=0;
    private String savedSearch=""; private int savedFilter=0;
    private ListenerRegistration syncListener;

    @Override public void onCreate(Bundle b){ super.onCreate(b); db=new DbHelper(this); prefs=getSharedPreferences("settings",MODE_PRIVATE); money=NumberFormat.getCurrencyInstance(new Locale("tr","TR"));
        if(b!=null){business=b.getString("business",B1);savedSearch=b.getString("search","");savedFilter=b.getInt("filter",0);customStart=b.getLong("customStart",0);customEnd=b.getLong("customEnd",0);exportStart=b.getLong("exportStart",0);exportEnd=b.getLong("exportEnd",0);}
        String pin=prefs.getString("pin",""); buildUi(); try { if(CloudSync.signedIn()) startCloudSync(); else promptCloudLogin(pin); } catch(Exception e) { Toast.makeText(this,"Senkronizasyon şu anda kullanılamıyor; yerel kayıtlarınız korunuyor.",Toast.LENGTH_LONG).show(); }
    }

    @Override protected void onSaveInstanceState(Bundle state){
        super.onSaveInstanceState(state);state.putString("business",business);state.putString("search",search==null?savedSearch:search.getText().toString());state.putInt("filter",filter==null?savedFilter:filter.getSelectedItemPosition());state.putLong("customStart",customStart);state.putLong("customEnd",customEnd);state.putLong("exportStart",exportStart);state.putLong("exportEnd",exportEnd);
    }
    @Override protected void onResume(){super.onResume();if(adapter!=null)refresh();}
    @Override protected void onDestroy(){if(syncListener!=null)syncListener.remove();if(db!=null)db.close();super.onDestroy();}


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
        // 1.4.2: acilista tum yerel kayitlari tekrar yukleme; bu eski surumde kopya uretiyordu.
        if(syncListener!=null)syncListener.remove();
        syncListener=CloudSync.listen(db,()->runOnUiThread(()->{if(adapter!=null)refresh();}));
    }
    private void syncTransaction(long id){
        Transaction t=db.byId(id);
        if(t==null){Toast.makeText(this,"Senkron: kayit bulunamadi",Toast.LENGTH_LONG).show();return;}
        Toast.makeText(this,"Senkron testi basladi",Toast.LENGTH_LONG).show(); CloudSync.upload(t,(ok,err)->runOnUiThread(()->new AlertDialog.Builder(this).setTitle(ok?"SENKRON BASARILI":"SENKRON HATASI").setMessage(ok?"Kayit Firebase bulutuna gonderildi.":String.valueOf(err)).setPositiveButton("Tamam",null).show()));
    }

    private void promptPin(String expected){
        final EditText in=new EditText(this); in.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD); in.setHint("4 haneli PIN"); in.setPadding(dp(18),dp(12),dp(18),dp(12));
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Kocaaslan İş Takip").setMessage("Uygulamayı açmak için PIN'inizi girin.").setView(in).setCancelable(false).setPositiveButton("Aç",null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{ if(expected.equals(in.getText().toString())){d.dismiss();buildUi();} else {in.setError("PIN yanlış");in.setText("");} })); d.show();
    }

    private void buildUi(){
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(18),dp(16),dp(18),dp(24)); root.setBackgroundColor(0xFFF4F8F6); setContentView(root);
        root.setOnApplyWindowInsetsListener((view,insets)->{view.setPadding(dp(18),insets.getSystemWindowInsetTop()+dp(16),dp(18),insets.getSystemWindowInsetBottom()+dp(24));return insets;});root.requestApplyInsets();

        LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(dp(18),dp(15),dp(18),dp(15)); GradientDrawable hg=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0xFF123B34,0xFF1A6457}); hg.setCornerRadius(dp(20)); header.setBackground(hg);
        LinearLayout ht=new LinearLayout(this); ht.setOrientation(LinearLayout.VERTICAL); TextView app=txt("Kocaaslan İş Takip",24,Color.WHITE,true); TextView sub=txt("Ciro • Gider • Kâr/Zarar",13,0xFFD2E8E2,false); ht.addView(app);ht.addView(sub);header.addView(ht,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1)); Button settings=button("Ayarlar",0xFFF5A623,0xFF17352F); settings.setOnClickListener(v->settingsDialog());header.addView(settings);root.addView(header,mp(0,0,0,14));

        LinearLayout businesses=new LinearLayout(this); businesses.setOrientation(LinearLayout.HORIZONTAL); b1=button(B1,0xFF1F9D72,Color.WHITE); b2=button(B2,0xFFE6ECE9,0xFF24413B); b1.setOnClickListener(v->switchBusiness(B1)); b2.setOnClickListener(v->switchBusiness(B2)); businesses.addView(b1,new LinearLayout.LayoutParams(0,dp(54),1)); LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,dp(54),1);bp.setMargins(dp(8),0,0,0);businesses.addView(b2,bp);root.addView(businesses,mp(0,0,0,14));

        titleBusiness=txt(business,20,0xFF173C35,true);root.addView(titleBusiness,mp(2,0,0,10));
        LinearLayout periods=new LinearLayout(this); periods.setOrientation(LinearLayout.HORIZONTAL); periods.addView(periodCard("BUGÜN",0),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1)); LinearLayout.LayoutParams p2=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);p2.setMargins(dp(8),0,0,0);periods.addView(periodCard("BU HAFTA",1),p2); LinearLayout.LayoutParams p3=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);p3.setMargins(dp(8),0,0,0);periods.addView(periodCard("BU AY",2),p3);root.addView(periods,mp(0,0,0,14));

        root.addView(totalCard(),mp(0,0,0,8));
        Button monthlyHistory=button("AYLIK GEÇMİŞİ GÖR",0xFF315E54,Color.WHITE);monthlyHistory.setOnClickListener(v->showMonthlyHistory());root.addView(monthlyHistory,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50)));

        LinearLayout quick=new LinearLayout(this); quick.setOrientation(LinearLayout.HORIZONTAL); Button addI=button("+ GELİR EKLE",0xFF1F9D72,Color.WHITE); Button addE=button("− GİDER EKLE",0xFFE45858,Color.WHITE);addI.setOnClickListener(v->showAdd("Gelir"));addE.setOnClickListener(v->showAdd("Gider"));quick.addView(addI,new LinearLayout.LayoutParams(0,dp(56),1));LinearLayout.LayoutParams qe=new LinearLayout.LayoutParams(0,dp(56),1);qe.setMargins(dp(8),0,0,0);quick.addView(addE,qe);root.addView(quick,mp(0,0,0,15));

        TextView chartTitle=txt("Son 6 Ay",17,0xFF173C35,true);root.addView(chartTitle); LinearLayout legend=new LinearLayout(this);legend.setOrientation(LinearLayout.HORIZONTAL);legend.addView(txt("■ Gelir",12,0xFF1F9D72,true));TextView lg=txt("   ■ Gider",12,0xFFE45858,true);legend.addView(lg);root.addView(legend);chart=new ChartView(this);chart.setBackground(cardBg(0xFFFFFFFF));root.addView(chart,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(245)));

        LinearLayout tools=new LinearLayout(this);tools.setGravity(Gravity.CENTER_VERTICAL);search=new EditText(this);search.setHint("Kayıtlarda ara…");search.setSingleLine(true);search.setBackground(cardBg(0xFFFFFFFF));search.setPadding(dp(14),0,dp(14),0);filter=new Spinner(this);ArrayAdapter<String> fa=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Tümü","Bugün","Bu hafta","Bu ay","Özel tarih"});filter.setAdapter(fa);tools.addView(search,new LinearLayout.LayoutParams(0,dp(52),1));LinearLayout.LayoutParams fl=new LinearLayout.LayoutParams(dp(140),dp(52));fl.setMargins(dp(8),0,0,0);tools.addView(filter,fl);root.addView(tools,mp(0,14,0,8));
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){refreshList();}public void afterTextChanged(Editable e){}});filter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){if(pos==4&&customEnd==0)chooseRange();else refreshList();}public void onNothingSelected(android.widget.AdapterView<?> p){}});

        LinearLayout acts=new LinearLayout(this);acts.setOrientation(LinearLayout.HORIZONTAL);Button csv=button("CSV / Excel",0xFF294D45,Color.WHITE);Button backup=button("Yedekle",0xFF476D64,Color.WHITE);Button restore=button("Geri Yükle",0xFF6B7C78,Color.WHITE);csv.setOnClickListener(v->beginCsv());backup.setOnClickListener(v->beginBackup());restore.setOnClickListener(v->beginRestore());acts.addView(csv,new LinearLayout.LayoutParams(0,dp(48),1));LinearLayout.LayoutParams al=new LinearLayout.LayoutParams(0,dp(48),1);al.setMargins(dp(6),0,0,0);acts.addView(backup,al);LinearLayout.LayoutParams ar=new LinearLayout.LayoutParams(0,dp(48),1);ar.setMargins(dp(6),0,0,0);acts.addView(restore,ar);root.addView(acts,mp(0,0,0,10));

        list=new ListView(this);list.setDivider(null);list.setDividerHeight(0);list.setNestedScrollingEnabled(false);adapter=new TransactionAdapter(this,this);list.setAdapter(adapter);list.setOnItemClickListener((parent,view,position,id)->showForm(adapter.getItem(position).type,adapter.getItem(position)));LinearLayout.LayoutParams listParams=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f); root.addView(list,listParams);
        search.setText(savedSearch);filter.setSelection(savedFilter);switchBusiness(business);
    }

    private View periodCard(String label,int which){ LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(13),dp(13),dp(13),dp(13));box.setBackground(cardBg(0xFFFFFFFF));TextView l=txt(label,12,0xFF62746F,true);box.addView(l);TextView i=txt("Gelir 0",13,0xFF13835D,true),e=txt("Gider 0",13,0xFFD94A4A,true),n=txt("Net 0",17,0xFF173C35,true);box.addView(i);box.addView(e);box.addView(n);if(which==0){dayIncome=i;dayExpense=e;dayNet=n;}else if(which==1){weekIncome=i;weekExpense=e;weekNet=n;}else{monthIncome=i;monthExpense=e;monthNet=n;}return box; }

    private View totalCard(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(16),dp(14),dp(16),dp(14));
        GradientDrawable g=bg(0xFFFFF7DF,16);g.setStroke(dp(1),0xFFF0D89B);box.setBackground(g);
        TextView l=txt("GENEL TOPLAM",14,0xFF7A5B12,true);box.addView(l);
        TextView sub=txt("İlk kayıttan bugüne • Detay için dokunun",12,0xFF806F45,false);box.addView(sub);
        LinearLayout vals=new LinearLayout(this);vals.setOrientation(LinearLayout.HORIZONTAL);vals.setPadding(0,dp(8),0,0);
        totalIncome=txt("Gelir  0",16,0xFF13835D,true);totalExpense=txt("Gider  0",16,0xFFD94A4A,true);totalNet=txt("Net  0",18,0xFF173C35,true);
        vals.addView(totalIncome,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        vals.addView(totalExpense,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        vals.addView(totalNet,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
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
        content.addView(txt(business,17,0xFF173C35,true),mp(0,0,0,10));
        if(years.isEmpty())content.addView(txt("Henüz kayıt bulunmuyor.",14,0xFF62746F,false));
        String[] monthNames=new java.text.DateFormatSymbols(new Locale("tr","TR")).getMonths();
        for(Map.Entry<Integer,TreeMap<Integer,double[]>> ye:years.entrySet()){
            int year=ye.getKey();double yi=0,yea=0;
            TextView yh=txt(String.valueOf(year),20,0xFF173C35,true);content.addView(yh,mp(0,8,0,6));
            for(Map.Entry<Integer,double[]> me:ye.getValue().entrySet()){
                double[] v=me.getValue();yi+=v[0];yea+=v[1];double net=v[0]-v[1];
                LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(12),dp(10),dp(12),dp(10));card.setBackground(cardBg(0xFFFFFFFF));
                String mn=monthNames[me.getKey()];mn=mn.substring(0,1).toUpperCase(new Locale("tr","TR"))+mn.substring(1);card.addView(txt(mn,16,0xFF173C35,true));
                LinearLayout vals=new LinearLayout(this);vals.setOrientation(LinearLayout.HORIZONTAL);vals.setPadding(0,dp(6),0,0);
                vals.addView(txt("Ciro\n"+money.format(v[0]),13,0xFF13835D,true),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
                vals.addView(txt("Gider\n"+money.format(v[1]),13,0xFFD94A4A,true),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
                vals.addView(txt("Net\n"+money.format(net),13,net>=0?0xFF13835D:0xFFD94A4A,true),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
                card.addView(vals);content.addView(card,mp(0,0,0,6));
            }
            double yn=yi-yea;LinearLayout yt=new LinearLayout(this);yt.setOrientation(LinearLayout.VERTICAL);yt.setPadding(dp(14),dp(11),dp(14),dp(11));GradientDrawable yg=bg(0xFFEAF4F0,14);yg.setStroke(dp(1),0xFFB8D7CD);yt.setBackground(yg);
            yt.addView(txt(year+" YILLIK TOPLAM",15,0xFF315E54,true));yt.addView(txt("Ciro: "+money.format(yi)+"   Gider: "+money.format(yea)+"   Net: "+money.format(yn),14,yn>=0?0xFF13835D:0xFFD94A4A,true));content.addView(yt,mp(0,0,0,14));
        }
        double allNet=allIncome-allExpense;LinearLayout grand=new LinearLayout(this);grand.setOrientation(LinearLayout.VERTICAL);grand.setPadding(dp(16),dp(14),dp(16),dp(14));GradientDrawable gg=bg(0xFFFFF2C7,16);gg.setStroke(dp(1),0xFFE0BE58);grand.setBackground(gg);
        grand.addView(txt("TÜM YILLAR GENEL TOPLAMI",16,0xFF7A5B12,true));grand.addView(txt("Ciro: "+money.format(allIncome)+"\nGider: "+money.format(allExpense)+"\nNet: "+money.format(allNet),16,allNet>=0?0xFF13835D:0xFFD94A4A,true));content.addView(grand,mp(0,4,0,8));
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
            LinearLayout vals=new LinearLayout(this);vals.setOrientation(LinearLayout.HORIZONTAL);vals.setPadding(0,dp(7),0,0);
            vals.addView(txt("Ciro\n"+money.format(v[0]),14,0xFF13835D,true),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            vals.addView(txt("Gider\n"+money.format(v[1]),14,0xFFD94A4A,true),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            TextView netView=txt("Net\n"+money.format(net),14,net>=0?0xFF13835D:0xFFD94A4A,true);vals.addView(netView,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            card.addView(vals);content.addView(card,mp(0,0,0,8));
        }
        ScrollView sv=new ScrollView(this);sv.addView(content);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Aylık Geçmiş").setView(sv).setPositiveButton("Kapat",null).create();d.setOnShowListener(x->d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,(int)(getResources().getDisplayMetrics().heightPixels*0.82)));d.show();
    }

    private void switchBusiness(String b){business=b;titleBusiness.setText(b);boolean first=B1.equals(b);b1.setBackground(bg(first?0xFF1F9D72:0xFFE6ECE9,14));b1.setTextColor(first?Color.WHITE:0xFF24413B);b2.setBackground(bg(!first?0xFF1F9D72:0xFFE6ECE9,14));b2.setTextColor(!first?Color.WHITE:0xFF24413B);refresh();}

    private void refresh(){ Calendar now=Calendar.getInstance(); long day=startOfDay(now).getTimeInMillis();Calendar d2=(Calendar)startOfDay(now).clone();d2.add(Calendar.DAY_OF_MONTH,1);long dayEnd=d2.getTimeInMillis();Calendar ws=startOfWeek(now);Calendar we=(Calendar)ws.clone();we.add(Calendar.DAY_OF_MONTH,7);Calendar ms=startOfMonth(now);Calendar me=(Calendar)ms.clone();me.add(Calendar.MONTH,1);setStats(db.stats(business,day,dayEnd),dayIncome,dayExpense,dayNet);setStats(db.stats(business,ws.getTimeInMillis(),we.getTimeInMillis()),weekIncome,weekExpense,weekNet);setStats(db.stats(business,ms.getTimeInMillis(),me.getTimeInMillis()),monthIncome,monthExpense,monthNet);setStats(db.totalStats(business),totalIncome,totalExpense,totalNet);chart.setData(db.lastSixMonths(business));refreshList();}
    private void setStats(double[] s,TextView i,TextView e,TextView n){i.setText("Gelir  "+money.format(s[0]));e.setText("Gider  "+money.format(s[1]));n.setText("Net  "+money.format(s[2]));n.setTextColor(s[2]>=0?0xFF13835D:0xFFD94A4A);}
    private void refreshList(){if(adapter==null)return;long[] r=filterRange();adapter.setItems(db.list(business,search==null?"":search.getText().toString(),r[0],r[1],0));}
    private long[] filterRange(){int p=filter==null?0:filter.getSelectedItemPosition();Calendar now=Calendar.getInstance();if(p==4)return new long[]{customStart,customEnd};if(p==1){Calendar s=startOfDay(now),e=(Calendar)s.clone();e.add(Calendar.DAY_OF_MONTH,1);return new long[]{s.getTimeInMillis(),e.getTimeInMillis()};}if(p==2){Calendar s=startOfWeek(now),e=(Calendar)s.clone();e.add(Calendar.DAY_OF_MONTH,7);return new long[]{s.getTimeInMillis(),e.getTimeInMillis()};}if(p==3){Calendar s=startOfMonth(now),e=(Calendar)s.clone();e.add(Calendar.MONTH,1);return new long[]{s.getTimeInMillis(),e.getTimeInMillis()};}return new long[]{0,0};}

    private void showAdd(String type){showForm(type,null);}
    private void showForm(String type,Transaction existing){ final Calendar selected=Calendar.getInstance();if(existing!=null)selected.setTimeInMillis(existing.date); LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(20),dp(8),dp(20),0);TextView who=txt(business,15,0xFF1F5A4D,true);form.addView(who);EditText amount=new EditText(this);amount.setHint("Tutar (₺)");amount.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);form.addView(amount);Spinner cat=new Spinner(this);String[] incomeCats={"Satış","Hizmet","Diğer Gelir"};String[] expenseCats={"Mal Alımı","Kira","Personel","Fatura","Vergi","Ulaşım","Bakım/Onarım","Diğer Gider"};cat.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,"Gelir".equals(type)?incomeCats:expenseCats));form.addView(cat);EditText note=new EditText(this);note.setHint("Açıklama (isteğe bağlı)");form.addView(note);Button dateBtn=button("Tarih: "+fmtDate(selected.getTimeInMillis()),0xFFE8F0ED,0xFF24413B);dateBtn.setOnClickListener(v->new DatePickerDialog(this,(view,y,m,d)->{selected.set(y,m,d,12,0,0);selected.set(Calendar.MILLISECOND,0);dateBtn.setText("Tarih: "+fmtDate(selected.getTimeInMillis()));},selected.get(Calendar.YEAR),selected.get(Calendar.MONTH),selected.get(Calendar.DAY_OF_MONTH)).show());form.addView(dateBtn,mp(0,8,0,0));if(existing!=null){amount.setText(Double.toString(existing.amount));note.setText(existing.note);String[] cats="Gelir".equals(type)?incomeCats:expenseCats;for(int k=0;k<cats.length;k++)if(cats[k].equals(existing.category))cat.setSelection(k);}AlertDialog dlg=new AlertDialog.Builder(this).setTitle(type+(existing==null?" Ekle":" Düzenle")).setView(form).setNegativeButton("Vazgeç",null).setPositiveButton("Kaydet",null).create();dlg.setOnShowListener(x->dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{String a=amount.getText().toString().trim().replace(",", ".");double val=Double.parseDouble(a);if(val<=0||Double.isNaN(val)||Double.isInfinite(val))throw new IllegalArgumentException();if(existing==null){long newId=db.add(business,type,val,String.valueOf(cat.getSelectedItem()),note.getText().toString().trim(),selected.getTimeInMillis());syncTransaction(newId);}else{db.update(new Transaction(existing.id,existing.business,existing.type,val,String.valueOf(cat.getSelectedItem()),note.getText().toString().trim(),selected.getTimeInMillis()));syncTransaction(existing.id);}dlg.dismiss();refresh();}catch(IllegalArgumentException ex){amount.setError("Geçerli bir tutar girin");}catch(Exception ex){Toast.makeText(this,"Kayıt kaydedilemedi",Toast.LENGTH_LONG).show();}}));dlg.show();}

    @Override public void onDelete(Transaction t){new AlertDialog.Builder(this).setTitle("Kaydı sil").setMessage(fmtDate(t.date)+" tarihli "+t.category+" kaydı silinsin mi?").setNegativeButton("Vazgeç",null).setPositiveButton("Sil",(d,w)->{Transaction local=db.byId(t.id);if(local!=null)CloudSync.delete(local.syncId);db.delete(t.id);refresh();}).show();}

    private void settingsDialog(){String pin=prefs.getString("pin","");String[] opts={pin.isEmpty()?"PIN kilidi oluştur":"PIN kilidini değiştir","PIN kilidini kaldır","Tarih aralığı seç","Kategori raporu","Senkron Bilgisi","Uygulama hakkında"};new AlertDialog.Builder(this).setTitle("Ayarlar").setItems(opts,(d,which)->{if(which==0)setPin();else if(which==1){prefs.edit().remove("pin").apply();Toast.makeText(this,"PIN kilidi kaldırıldı",Toast.LENGTH_SHORT).show();}else if(which==2)chooseRange();else if(which==3)showCategoryReport();else if(which==4)showSyncInfo();else new AlertDialog.Builder(this).setTitle("Kocaaslan İş Takip v1.5.1").setMessage("Yavuz Kocaaslan ve Kocaaslan Kantin için kişisel ciro-gider ve kâr/zarar takip uygulaması. Veriler cihazda saklanır.").setPositiveButton("Tamam",null).show();}).show();}
    private void showSyncInfo(){
        String project=CloudSync.projectId(), email=CloudSync.email(), uid=CloudSync.uid();
        String msg="Durum: "+(CloudSync.signedIn()?"GİRİŞ YAPILMIŞ":"GİRİŞ YOK")+"\n\nFirebase Projesi: "+String.valueOf(project)+"\nE-posta: "+String.valueOf(email)+"\nUID: "+String.valueOf(uid);
        new AlertDialog.Builder(this).setTitle("Senkron Bilgisi").setMessage(msg).setNegativeButton("Kapat",null).setPositiveButton("Hesabı Yeniden Bağla",(d,w)->{try{CloudSync.signOut();}catch(Exception ignored){} promptCloudLogin(prefs.getString("pin",""));}).show();
    }
    private void setPin(){EditText in=new EditText(this);in.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);in.setHint("4 haneli PIN");new AlertDialog.Builder(this).setTitle("PIN oluştur").setView(in).setNegativeButton("Vazgeç",null).setPositiveButton("Kaydet",(d,w)->{String p=in.getText().toString();if(p.matches("[0-9]{4}")){prefs.edit().putString("pin",p).apply();Toast.makeText(this,"PIN kaydedildi",Toast.LENGTH_SHORT).show();}else Toast.makeText(this,"PIN 4 rakam olmalı",Toast.LENGTH_LONG).show();}).show();}

    private void beginCsv(){long[] r=filterRange();exportStart=r[0];exportEnd=r[1];Intent i=new Intent("android.intent.action.CREATE_DOCUMENT");i.setType("text/csv");i.putExtra(Intent.EXTRA_TITLE,"Kocaaslan_"+safe(business)+"_"+new SimpleDateFormat("yyyyMMdd",Locale.US).format(new Date())+".csv");startActivityForResult(i,REQ_CSV);}
    private void beginBackup(){Intent i=new Intent("android.intent.action.CREATE_DOCUMENT");i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"Kocaaslan_IsTakip_Yedek_"+new SimpleDateFormat("yyyyMMdd_HHmm",Locale.US).format(new Date())+".json");startActivityForResult(i,REQ_BACKUP);}
    private void beginRestore(){Intent i=new Intent("android.intent.action.OPEN_DOCUMENT");i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,REQ_RESTORE);}

    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri u=data.getData();try{if(req==REQ_CSV)writeCsv(u);else if(req==REQ_BACKUP)writeBackup(u);else if(req==REQ_RESTORE)restoreBackup(u);}catch(Exception e){Toast.makeText(this,"İşlem tamamlanamadı: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
    private void writeCsv(Uri u)throws Exception{List<Transaction> rows=db.list(business,search==null?"":search.getText().toString(),exportStart,exportEnd,0);OutputStream os=getContentResolver().openOutputStream(u);OutputStreamWriter w=new OutputStreamWriter(os,StandardCharsets.UTF_8);w.write('\uFEFF');w.write("Tarih;İşletme;Tür;Kategori;Açıklama;Tutar\n");for(Transaction t:rows)w.write(csv(fmtDate(t.date))+";"+csv(t.business)+";"+csv(t.type)+";"+csv(t.category)+";"+csv(t.note)+";"+String.format(Locale.US,"%.2f",t.amount).replace('.',',')+"\n");w.close();Toast.makeText(this,"CSV kaydedildi",Toast.LENGTH_SHORT).show();}
    private void writeBackup(Uri u)throws Exception{JSONArray a=new JSONArray();for(Transaction t:db.all()){JSONObject o=new JSONObject();o.put("business",t.business);o.put("type",t.type);o.put("amount",t.amount);o.put("category",t.category);o.put("note",t.note);o.put("date",t.date);a.put(o);}JSONObject root=new JSONObject();root.put("app","Kocaaslan İş Takip");root.put("version",1);root.put("transactions",a);OutputStream os=getContentResolver().openOutputStream(u);os.write(root.toString(2).getBytes(StandardCharsets.UTF_8));os.close();Toast.makeText(this,"Yedek kaydedildi",Toast.LENGTH_SHORT).show();}
    private void restoreBackup(Uri u)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(InputStream is=getContentResolver().openInputStream(u)){
            if(is==null)throw new IOException("Yedek açılamadı");byte[] buf=new byte[4096];int n;
            while((n=is.read(buf))!=-1){if(out.size()+n>10*1024*1024)throw new IOException("Yedek çok büyük");out.write(buf,0,n);}
        }
        JSONObject root=new JSONObject(out.toString("UTF-8"));
        if(!"Kocaaslan İş Takip".equals(root.getString("app"))||root.getInt("version")!=1)throw new IOException("Uyumsuz yedek");
        JSONArray a=root.getJSONArray("transactions");List<Transaction> rows=new ArrayList<>();
        for(int x=0;x<a.length();x++){
            JSONObject o=a.getJSONObject(x);String business=o.getString("business"),type=o.getString("type");double amount=o.getDouble("amount");long date=o.getLong("date");
            if((!B1.equals(business)&&!B2.equals(business))||(!"Gelir".equals(type)&&!"Gider".equals(type))||amount<=0||Double.isNaN(amount)||Double.isInfinite(amount)||date<=0)throw new IOException("Geçersiz kayıt: "+(x+1));
            rows.add(new Transaction(0,business,type,amount,o.optString("category","Diğer"),o.optString("note",""),date));
        }
        new AlertDialog.Builder(this).setTitle("Yedeği geri yükle").setMessage("Mevcut kayıtlar silinip yedekteki "+rows.size()+" kayıt yüklenecek. Devam edilsin mi?").setNegativeButton("Vazgeç",null).setPositiveButton("Yükle",(d,w)->{try{db.replaceAll(rows);refresh();Toast.makeText(this,"Yedek geri yüklendi",Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(this,"Yükleme başarısız; mevcut kayıtlar korundu",Toast.LENGTH_LONG).show();}}).show();
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
    private TextView txt(String s,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private Button button(String s,int bg,int fg){Button b=new Button(this);b.setText(s);b.setTextColor(fg);b.setTextSize(13);b.setAllCaps(false);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackground(bg(bg,14));return b;}
    private GradientDrawable bg(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private GradientDrawable cardBg(int color){GradientDrawable g=bg(color,16);g.setStroke(dp(1),0xFFE2EAE7);return g;}
    private LinearLayout.LayoutParams mp(int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
}
