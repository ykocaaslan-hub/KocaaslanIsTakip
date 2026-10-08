package com.kocaaslan.istakip;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

/** A personal tribute, with a bundled historical photograph and ordinary accessible text. */
public final class ThemeHeader extends LinearLayout {
    private static final int GOLD=0xFFFFD65A,WHITE=0xFFF6F7FA,MUTED=0xFFB5C4D8;
    public ThemeHeader(Context context,Runnable settings){
        super(context);setOrientation(VERTICAL);setPadding(dp(16),dp(14),dp(16),dp(14));
        GradientDrawable background=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFF142E55,0xFF081A34});background.setCornerRadius(dp(22));background.setStroke(dp(1),0xFF655A37);setBackground(background);setElevation(dp(6));
        LinearLayout toolbar=new LinearLayout(context);toolbar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout name=new LinearLayout(context);name.setOrientation(VERTICAL);
        TextView eyebrow=label("KOCAASLAN",11,GOLD,true);eyebrow.setLetterSpacing(.14f);name.addView(eyebrow);name.addView(label("İş Takip",24,WHITE,true));toolbar.addView(name,new LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        Button menu=new Button(context);menu.setText("☰");menu.setTextSize(22);menu.setTextColor(WHITE);menu.setAllCaps(false);menu.setContentDescription("Ayarlar");menu.setPadding(0,0,0,0);menu.setMinimumWidth(0);menu.setMinWidth(0);menu.setMinimumHeight(dp(48));menu.setBackground(rounded(0xFF1D3557,12,0xFF3F5270));menu.setOnClickListener(v->settings.run());toolbar.addView(menu,new LayoutParams(dp(48),ViewGroup.LayoutParams.WRAP_CONTENT));addView(toolbar);

        LinearLayout tribute=new LinearLayout(context);tribute.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout words=new LinearLayout(context);words.setOrientation(VERTICAL);
        TextView badge=label("1907",13,0xFF0A203D,true);badge.setGravity(Gravity.CENTER);badge.setPadding(dp(10),dp(4),dp(10),dp(4));badge.setBackground(rounded(GOLD,8,GOLD));words.addView(badge,new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView motto=label("Daima\nFenerbahçe",23,GOLD,true);LayoutParams mottoParams=new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);mottoParams.topMargin=dp(8);words.addView(motto,mottoParams);
        TextView sub=label("Sarı lacivert bir tutku",12,MUTED,false);LayoutParams subParams=new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);subParams.topMargin=dp(5);words.addView(sub,subParams);
        tribute.addView(words,new LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        LinearLayout portraitColumn=new LinearLayout(context);portraitColumn.setOrientation(VERTICAL);portraitColumn.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView portrait=new ImageView(context);portrait.setTag("ataturk_portrait");portrait.setContentDescription("Mustafa Kemal Atatürk’ün 1932 tarihli portresi");portrait.setImageResource(R.drawable.ataturk_portrait);portrait.setScaleType(ImageView.ScaleType.CENTER_CROP);portrait.setBackground(rounded(0xFF101D30,14,0xFFAC9456));portrait.setClipToOutline(true);
        portraitColumn.addView(portrait,new LayoutParams(dp(88),dp(118)));
        TextView caption=label("Mustafa Kemal\nAtatürk",11,WHITE,true);caption.setGravity(Gravity.CENTER);LayoutParams captionParams=new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);captionParams.topMargin=dp(5);portraitColumn.addView(caption,captionParams);
        LayoutParams portraitParams=new LayoutParams(dp(96),ViewGroup.LayoutParams.WRAP_CONTENT);portraitParams.leftMargin=dp(10);tribute.addView(portraitColumn,portraitParams);
        LayoutParams tributeParams=new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);tributeParams.topMargin=dp(12);addView(tribute,tributeParams);

        View rule=new View(context);rule.setBackgroundColor(0xFF47516B);LayoutParams ruleParams=new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(1));ruleParams.topMargin=dp(12);ruleParams.bottomMargin=dp(8);addView(rule,ruleParams);
        TextView dedication=label("1881 — 1938  ·  Saygı ve minnetle",11,MUTED,false);addView(dedication);
    }
    private TextView label(String text,int size,int color,boolean bold){TextView v=new TextView(getContext());v.setText(text);v.setTextSize(size);v.setTextColor(color);v.setSingleLine(false);v.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));v.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_SIMPLE);v.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE);return v;}
    private GradientDrawable rounded(int color,int radius,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));d.setStroke(dp(1),stroke);return d;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
