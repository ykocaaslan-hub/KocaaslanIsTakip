package com.kocaaslan.istakip;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import java.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.Locale;

public class ChartView extends View {
    private double[][] data = new double[6][2];
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String[] months = new String[6];

    public ChartView(Context c) { super(c); setMinimumHeight(dp(230)); rebuildMonths(); }
    public void setData(double[][] d) { data = d == null ? new double[6][2] : d; rebuildMonths(); invalidate(); }
    private void rebuildMonths() {
        String[] shortM = new DateFormatSymbols(new Locale("tr","TR")).getShortMonths();
        Calendar cal = Calendar.getInstance(); cal.set(Calendar.DAY_OF_MONTH,1); cal.add(Calendar.MONTH,-5);
        for(int i=0;i<6;i++){ months[i]=shortM[cal.get(Calendar.MONTH)].replace(".",""); cal.add(Calendar.MONTH,1); }
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c); float w=getWidth(), h=getHeight();
        p.setColor(0xFF5D6F6B); p.setTextSize(dp(12)); p.setTextAlign(Paint.Align.CENTER);
        double max=1; for(double[] v:data) max=Math.max(max,Math.max(v[0],v[1]));
        float left=dp(15), right=w-dp(15), top=dp(20), bottom=h-dp(35), group=(right-left)/6f;
        p.setStrokeWidth(dp(1)); p.setColor(0x223B4D49);
        for(int g=0;g<4;g++){ float y=top+(bottom-top)*g/3f; c.drawLine(left,y,right,y,p); }
        for(int i=0;i<6;i++){
            float cx=left+group*i+group/2; float bw=Math.min(dp(18),group/4);
            float ih=(float)((data[i][0]/max)*(bottom-top)); float eh=(float)((data[i][1]/max)*(bottom-top));
            p.setColor(0xFF1F9D72); c.drawRoundRect(new RectF(cx-bw-dp(2),bottom-ih,cx-dp(2),bottom),dp(4),dp(4),p);
            p.setColor(0xFFE45858); c.drawRoundRect(new RectF(cx+dp(2),bottom-eh,cx+bw+dp(2),bottom),dp(4),dp(4),p);
            p.setColor(0xFF5D6F6B); p.setTextSize(dp(11)); c.drawText(months[i],cx,h-dp(12),p);
        }
    }
    private int dp(int x){ return (int)(x*getResources().getDisplayMetrics().density+.5f); }
}
