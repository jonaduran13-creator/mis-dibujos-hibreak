package mx.dibujos.kiosko;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.BatteryManager;
import android.view.View;

/**
 * Nivel de batería para la pantalla de inicio (el kiosco oculta la barra de estado).
 * Pila dibujada en negro + porcentaje; un rayo cuando está cargando. Solo se repinta
 * cuando cambia el porcentaje o la carga, para no gastar refrescos de la tinta.
 */
public class BatteryView extends View {
    private int level = -1;
    private boolean charging;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path bolt = new Path();

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) { update(i); }
    };

    public BatteryView(Context c) {
        super(c);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // El aviso de batería es "pegajoso": registrar devuelve de inmediato el estado actual.
        update(getContext().registerReceiver(receiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED)));
    }

    @Override
    protected void onDetachedFromWindow() {
        getContext().unregisterReceiver(receiver);
        super.onDetachedFromWindow();
    }

    private void update(Intent i) {
        if (i == null) return;
        int raw = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        int pct = raw < 0 ? -1 : Math.round(raw * 100f / Math.max(1, scale));
        boolean plugged = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
        if (pct == level && plugged == charging) return;
        level = pct;
        charging = plugged;
        setContentDescription(pct < 0 ? "Batería" : "Batería " + pct + "%" + (plugged ? ", cargando" : ""));
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        float d = getResources().getDisplayMetrics().density;
        setMeasuredDimension(Math.round(120 * d), Math.round(32 * d));
    }

    @Override
    protected void onDraw(Canvas c) {
        float h = getHeight(), d = getResources().getDisplayMetrics().density;
        float bw = 34 * d, bh = 17 * d, top = (h - bh) / 2, stroke = 2 * d;
        // Cuerpo y polo de la pila.
        p.setColor(Color.BLACK);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(stroke);
        r.set(stroke, top, stroke + bw, top + bh);
        c.drawRoundRect(r, 3 * d, 3 * d, p);
        p.setStyle(Paint.Style.FILL);
        c.drawRect(stroke + bw, top + bh * .3f, stroke + bw + 3 * d, top + bh * .7f, p);
        // Relleno según el nivel.
        if (level > 0) {
            float inner = 2.5f * d;
            r.set(stroke + inner, top + inner, stroke + inner + (bw - 2 * inner) * Math.min(100, level) / 100f, top + bh - inner);
            c.drawRect(r, p);
        }
        // Rayo de carga, en blanco con borde negro para que se vea sobre el relleno.
        if (charging) {
            float cx = stroke + bw / 2, cy = top + bh / 2, s = bh * .42f;
            bolt.reset();
            bolt.moveTo(cx + s * .2f, cy - s);
            bolt.lineTo(cx - s * .55f, cy + s * .15f);
            bolt.lineTo(cx - s * .05f, cy + s * .15f);
            bolt.lineTo(cx - s * .2f, cy + s);
            bolt.lineTo(cx + s * .55f, cy - s * .15f);
            bolt.lineTo(cx + s * .05f, cy - s * .15f);
            bolt.close();
            p.setColor(Color.WHITE);
            c.drawPath(bolt, p);
            p.setColor(Color.BLACK);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.2f * d);
            c.drawPath(bolt, p);
            p.setStyle(Paint.Style.FILL);
        }
        // Porcentaje.
        p.setTextSize(18 * d);
        p.setFakeBoldText(true);
        String text = level < 0 ? "--" : level + "%";
        c.drawText(text, stroke + bw + 9 * d, h / 2 - (p.ascent() + p.descent()) / 2, p);
    }
}
