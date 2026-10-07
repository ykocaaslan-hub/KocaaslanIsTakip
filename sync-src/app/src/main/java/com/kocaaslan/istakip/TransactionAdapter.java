package com.kocaaslan.istakip;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TransactionAdapter extends BaseAdapter {
    public interface DeleteListener { void onDelete(Transaction t); }
    private final Context ctx; private final DeleteListener listener; private final NumberFormat money;
    private List<Transaction> items = new ArrayList<>();
    public TransactionAdapter(Context c, DeleteListener l){ctx=c;listener=l;money=NumberFormat.getCurrencyInstance(new Locale("tr","TR"));}
    public void setItems(List<Transaction> i){items=i;notifyDataSetChanged();}
    @Override public int getCount(){return items.size();}
    @Override public Transaction getItem(int p){return items.get(p);}
    @Override public long getItemId(int p){return items.get(p).id;}
    @Override public View getView(int pos, View convertView, ViewGroup parent){
        final Transaction t=getItem(pos);
        LinearLayout row=new LinearLayout(ctx); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(14),dp(10),dp(10),dp(10));
        GradientDrawable bg=new GradientDrawable(); bg.setColor(0xFF0A2748); bg.setCornerRadius(dp(14)); bg.setStroke(dp(1),0xFF2B5E8D); row.setBackground(bg); row.setElevation(dp(5));
        LinearLayout text=new LinearLayout(ctx); text.setOrientation(LinearLayout.VERTICAL); text.setPadding(0,0,dp(8),0);
        TextView title=new TextView(ctx); title.setText(("Gelir".equals(t.type)?"▲ ":"▼ ")+t.category+(t.note==null||t.note.trim().isEmpty()?"":" · "+t.note)); title.setTextSize(15); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD); title.setTextColor(0xFFFFFFFF);
        TextView date=new TextView(ctx); date.setText(new SimpleDateFormat("dd MMM yyyy",new Locale("tr","TR")).format(new Date(t.date))); date.setTextColor(0xFF9FB4C8); date.setTextSize(12);
        text.addView(title); text.addView(date); row.addView(text,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        TextView amt=new TextView(ctx); amt.setText(("Gelir".equals(t.type)?"+ ":"- ")+money.format(t.amount)); amt.setTextSize(16); amt.setTypeface(Typeface.DEFAULT,Typeface.BOLD); amt.setTextColor("Gelir".equals(t.type)?0xFF3EEA83:0xFFFF5B63); row.addView(amt);
        Button del=new Button(ctx); del.setText("Sil"); del.setTextSize(12); del.setMinWidth(0); del.setMinimumWidth(0); del.setPadding(dp(10),0,dp(10),0); del.setOnClickListener(v->listener.onDelete(t)); row.addView(del,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,dp(42)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); lp.setMargins(0,dp(5),0,dp(5)); row.setLayoutParams(lp); return row;
    }
    private int dp(int x){return (int)(x*ctx.getResources().getDisplayMetrics().density+.5f);}
}
