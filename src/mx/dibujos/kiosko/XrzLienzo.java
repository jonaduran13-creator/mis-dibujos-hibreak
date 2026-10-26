package mx.dibujos.kiosko;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

import com.xrz.NoteView;
import com.xrz.Rubber;
import com.xrz.SimplePen;

/**
 * Lienzo sobre el motor de escritura de Bigme (com.xrz.NoteView, en
 * /system/framework/handwritten.jar). El trazo se pinta en un búfer compartido
 * con la pantalla y se envía directo al panel, sin pasar por la composición
 * normal de Android.
 *
 * El servicio de Bigme solo lee el lápiz digitalizador (el HiBreak no tiene),
 * así que le pasamos los toques del dedo con onInputTouch(acción, x, y, presión,
 * herramienta), en coordenadas del panel: está girado 270° respecto a la pantalla.
 */
public class XrzLienzo implements Lienzo {
    private static final String TAG = "MisDibujosXrz";
    private static final int DOWN = 1, MOVE = 2, UP = 3, TOOL_FINGER = 2, PRESSURE = 2048;

    private final NoteView note;
    private final int screenWidth;
    private Listener listener;
    private boolean dirty;
    private boolean active = true;
    private int generation;
    private boolean assisted=true;
    @Override public void setShapeAssist(boolean enabled){
        assisted=enabled;
        if(!enabled){generation++;note.removeCallbacks(snap);replacing=false;}
    }
    private boolean drawing;
    private Bitmap pendingBackground;

    /** Lanza una excepción si el sistema no trae el motor de Bigme. */
    public XrzLienzo(Context c) {
        note = new NoteView(c);
        note.setFocusCheckEnabled(false);
        note.setInputEnabled(true);
        note.setOnTouchListener(this::onFinger);
        note.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) note.post(this::flushPending);
        });
        screenWidth = c.getResources().getDisplayMetrics().widthPixels;
        setPen(5);
        Log.i(TAG, "motor de Bigme " + note.getVersion());
    }

    @Override public View view() { return note; }
    @Override public void setListener(Listener l) { listener = l; }
    @Override public boolean isDirty() { return dirty; }
    @Override public boolean isDrawing() { return drawing; }
    @Override public void markSaved() { dirty = false; }
    @Override public void markDirty(){dirty=true;}

    @Override
    public void setPen(float width) {
        int w = Math.max(1, Math.round(width));
        SimplePen pen = new SimplePen();
        pen.setAutoStrokeWidthEnabled(false);
        pen.setStrokeWidthRange(w - 1, w); // el motor exige máximo > mínimo
        pen.setStrokeWidth(w);
        note.setPen(pen);
        erasing = false;
    }

    @Override
    public void setEraser(float width) {
        int w = Math.max(1, Math.round(width));
        Rubber r = new Rubber();
        r.setStrokeWidthRange(w - 1, w);
        r.setStrokeWidth(w);
        note.setPen(r);
        erasing = true;
    }

    @Override
    public void undo() {
        if (!note.canRevertPrev()) return;
        note.revertPrev();
        dirty = true;
        if (listener != null) {
            listener.onStrokeFinished();
            listener.onRedrawn();
        }
    }

    @Override
    public void clear() {
        generation++;
        note.clear(true);
        note.setBackground(blank(), true);
        dirty = false;
        if (listener != null) listener.onRedrawn();
    }

    @Override
    public void load(Bitmap b) {
        generation++;
        note.clear(true);
        setBackground(b);
        dirty = false;
    }

    @Override
    public void loadPhoto(Bitmap b) {
        load(b);
        dirty = true;
    }

    /**
     * Dibujo editable sin salir del motor rápido: la tinta guardada va a la capa de primer
     * plano del motor (la que borra la goma) y el papel/foto al fondo, que la goma no toca.
     * Así reabrir, deshacer o borrar ya no obliga a pasar al lienzo lento de Android.
     */
    @Override
    public void loadEditable(Bitmap content, Bitmap paper, boolean modified) {
        generation++;
        note.clear(true);
        Bitmap ink = inkOnly(content, paper);
        if (note.getWidth() == 0) {
            pendingInk = ink;
            pendingBackground = paper;
            pendingLayers = true;
        } else {
            applyLayers(ink, paper);
        }
        dirty = modified;
        if (listener != null) listener.onRedrawn();
    }

    private Bitmap pendingInk;
    private boolean pendingLayers;

    private void applyLayers(Bitmap ink, Bitmap paper) {
        note.setForegroundAndBackgroundTogether(fit(ink, Color.TRANSPARENT),
                paper == null ? blank() : fit(paper, Color.WHITE), true);
    }

    /** Deja solo lo que el niño dibujó: transparente donde la imagen coincide con el papel (o es blanco). */
    private static Bitmap inkOnly(Bitmap content, Bitmap paper) {
        int w = content.getWidth(), h = content.getHeight();
        int[] px = new int[w * h];
        content.getPixels(px, 0, w, 0, 0, w, h);
        int[] under = null;
        if (paper != null) {
            Bitmap p = paper.getWidth() == w && paper.getHeight() == h ? paper : Bitmap.createScaledBitmap(paper, w, h, true);
            under = new int[w * h];
            p.getPixels(under, 0, w, 0, 0, w, h);
            if (p != paper) p.recycle();
        }
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            boolean blank;
            if (under != null) {
                int u = under[i];
                int d = Math.abs(((c >> 16) & 255) - ((u >> 16) & 255)) + Math.abs(((c >> 8) & 255) - ((u >> 8) & 255))
                        + Math.abs((c & 255) - (u & 255));
                blank = d < 48;
            } else {
                blank = ((c >> 16) & 255) > 235 && ((c >> 8) & 255) > 235 && (c & 255) > 235;
            }
            if (blank) px[i] = Color.TRANSPARENT;
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
    }

    private void setBackground(Bitmap b) {
        if (note.getWidth() == 0) {
            pendingBackground = b;
            return;
        }
        note.setBackground(fit(b), true);
        Log.i(TAG, "fondo " + b.getWidth() + "x" + b.getHeight());
    }

    /**
     * El motor quiere el fondo del tamaño del lienzo y en la orientación física
     * del panel (acostada): el punto (x, y) de la pantalla va a (y, ancho - x).
     */
    private Bitmap fit(Bitmap b) {
        return fit(b, Color.WHITE);
    }

    private Bitmap fit(Bitmap b, int fill) {
        int w = note.getWidth(), h = note.getHeight();
        Bitmap view = b;
        if (b.getWidth() != w || b.getHeight() != h) {
            view = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(view);
            c.drawColor(fill, android.graphics.PorterDuff.Mode.SRC);
            float scale = Math.min(1f, Math.min((float) w / b.getWidth(), (float) h / b.getHeight()));
            c.drawBitmap(b, null, new RectF(0, 0, b.getWidth() * scale, b.getHeight() * scale), null);
        }
        int angle=panelAngle();
        if(angle==0) return view;
        Matrix m=new Matrix();m.setRotate(angle);
        Bitmap panel=Bitmap.createBitmap(view,0,0,w,h,m,true);
        return panel;
    }

    private Bitmap blank() {
        int w = Math.max(1, note.getWidth()), h = Math.max(1, note.getHeight());
        boolean swap=panelAngle()%180!=0;
        Bitmap out = Bitmap.createBitmap(swap?h:w, swap?w:h, Bitmap.Config.ARGB_8888); // orientación del panel
        out.eraseColor(Color.WHITE);
        return out;
    }

    @Override
    public Bitmap snapshot() {
        // getBlendBitmap() viene en la orientación física del panel (acostada).
        // Antes de que exista la superficie (al arrancar, reabriendo el último dibujo) o
        // tras destruirla, el motor de Bigme lanza NullPointerException en Painter.blend:
        // se responde "sin imagen" para no tumbar la app (que es el inicio del kiosco).
        Bitmap b;
        try {
            b = note.getBlendBitmapAfterRotation();
        } catch (RuntimeException e) {
            Log.w(TAG, "snapshot: el motor de Bigme aún no está listo", e);
            return null;
        }
        if (b == null) return null;
        Log.i(TAG, "snapshot " + b.getWidth() + "x" + b.getHeight() + " vista " + note.getWidth() + "x" + note.getHeight());
        return b.copy(Bitmap.Config.ARGB_8888, false);
    }

    @Override
    public void setActive(boolean on) {
        if (active == on) return;
        if(!on)generation++;
        active = on;
        if (drawing) feedUp();
        note.setInputEnabled(on);
    }

    @Override
    public void onResume() {
        note.onRestart();
        note.post(this::flushPending);
    }

    /** Aplica lo que se cargó antes de que el lienzo tuviera tamaño (al arrancar). */
    private void flushPending() {
        if (note.getWidth() == 0) return;
        try {
            if (pendingLayers) {
                applyLayers(pendingInk, pendingBackground);
            } else if (pendingBackground != null) {
                note.setBackground(fit(pendingBackground), true);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "el motor de Bigme aún no está listo", e);
            return;
        }
        pendingLayers = false;
        pendingInk = null;
        pendingBackground = null;
    }

    // ---------- Entrada del dedo ----------

    /** Dejar el dedo quieto este tiempo al final del trazo lo convierte en figura perfecta. */
    private static final long HOLD_TO_SNAP = 350;
    private static final float HOLD_SLOP = 12f;

    private float lastRawX, lastRawY;
    private float holdX, holdY;
    private boolean erasing;
    private boolean snapped; // ya se convirtió: se ignora el resto del toque
    private boolean replacing; // mientras se cambia por la figura perfecta, se ignoran toques nuevos
    private final List<PointF> points = new ArrayList<>();
    private final Runnable snap = this::trySnap;

    private int panelAngle() {
        int rotation=note.getDisplay()==null?1:note.getDisplay().getRotation();
        return (rotation*90+270)%360;
    }

    private void feed(int action, float rawX, float rawY) {
        int angle=panelAngle();float x=rawX,y=rawY;
        int w=note.getResources().getDisplayMetrics().widthPixels;
        int h=note.getResources().getDisplayMetrics().heightPixels;
        if(angle==270){x=rawY;y=w-rawX;}
        else if(angle==180){x=w-rawX;y=h-rawY;}
        else if(angle==90){x=h-rawY;y=rawX;}
        note.onInputTouch(action,Math.round(x),Math.round(y),PRESSURE,TOOL_FINGER);
    }

    private void down(float x, float y) {
        if(listener!=null)listener.onStrokeStarted();
        drawing = true;
        snapped = false;
        points.clear();
        lastRawX = holdX = x;
        lastRawY = holdY = y;
        points.add(new PointF(x, y));
        feed(DOWN, x, y);
        armSnap();
    }

    private void move(float x, float y) {
        feed(MOVE, x, y);
        points.add(new PointF(x, y));
        lastRawX = x;
        lastRawY = y;
        // Temblores mínimos con el dedo quieto no cuentan como movimiento.
        if (Math.hypot(x - holdX, y - holdY) > HOLD_SLOP) {
            holdX = x;
            holdY = y;
            armSnap();
        }
    }

    private void armSnap() {
        note.removeCallbacks(snap);
        if (assisted && !erasing) note.postDelayed(snap, HOLD_TO_SNAP);
    }

    private void feedUp() {
        note.removeCallbacks(snap);
        feed(UP, lastRawX, lastRawY);
        drawing = false;
        dirty = true;
        if (listener != null) listener.onStrokeFinished();
    }

    /** Si el trazo parece una línea o figura, se cambia por la versión perfecta. */
    private void trySnap() {
        if (!assisted || !drawing || erasing) return;
        long started = android.os.SystemClock.uptimeMillis();
        ShapeSnap.Result found = ShapeSnap.classify(points);
        Log.i(TAG, "figura: " + (found == null ? "ninguna" : found.kind) + " (" + points.size() + " puntos, "
                + (android.os.SystemClock.uptimeMillis() - started) + " ms)");
        if (found == null) return;
        final List<PointF> shape = found.points;
        snapped = true;
        replacing = true;
        feedUp();
        // El motor registra el trazo en su propio hilo: se espera a que lo termine
        // antes de quitarlo, o se borraría el trazo anterior.
        final int version=generation;
        note.postDelayed(() -> {if(version==generation)replaceWith(shape);else replacing=false;}, SNAP_SETTLE);
    }

    private static final long SNAP_SETTLE = 180;

    private void replaceWith(List<PointF> shape) {
        replacing = false;
        if(!active)return; // conservar el trazo original si ya hay otra pantalla encima
        note.revertPrev(); // quita el trazo a mano…
        PointF first = shape.get(0);
        lastRawX = first.x;
        lastRawY = first.y;
        feed(DOWN, first.x, first.y); // …y lo dibuja perfecto, como un trazo más (se puede deshacer)
        for (int i = 1; i < shape.size(); i++) {
            PointF p = shape.get(i);
            feed(MOVE, p.x, p.y);
            lastRawX = p.x;
            lastRawY = p.y;
        }
        feed(UP, lastRawX, lastRawY);
        Sounds.play(Sounds.SNAP);
        if (listener != null) {
            listener.onStrokeFinished();
            listener.onRedrawn();
        }
    }

    private boolean onFinger(View v, MotionEvent e) {
        if (!active) return false;
        // Las posiciones históricas solo vienen relativas a la vista: se pasan a pantalla.
        float dx = e.getRawX() - e.getX(), dy = e.getRawY() - e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (replacing) return true;
                v.requestUnbufferedDispatch(e); // cada movimiento llega al instante, sin esperar al cuadro
                down(e.getRawX(), e.getRawY());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!drawing) return true;
                for (int i = 0; i < e.getHistorySize(); i++) {
                    move(e.getHistoricalX(0, i) + dx, e.getHistoricalY(0, i) + dy);
                }
                move(e.getRawX(), e.getRawY());
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                if (e.getActionIndex() == 0 && drawing) feedUp();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (drawing) {
                    lastRawX = e.getRawX();
                    lastRawY = e.getRawY();
                    if(e.getActionMasked()==MotionEvent.ACTION_UP)trySnap();
                    if(drawing)feedUp();
                }
                return true;
        }
        return true;
    }

    /**
     * Trazo simulado en coordenadas de pantalla (x0,y0,x1,y1,…), para probar desde la PC.
     * Con hold, se queda "quieto" al final para probar la figura perfecta.
     */
    public void testPath(float[] xy, boolean hold) {
        down(xy[0], xy[1]);
        for (int i = 2; i + 1 < xy.length; i += 2) {
            float x0 = xy[i - 2], y0 = xy[i - 1], x1 = xy[i], y1 = xy[i + 1];
            for (int k = 1; k <= 10; k++) move(x0 + (x1 - x0) * k / 10, y0 + (y1 - y0) * k / 10);
        }
        if (hold) {
            // Igual que un dedo quieto: se espera el tiempo de la figura perfecta y luego se suelta.
            note.postDelayed(() -> { if (drawing) feedUp(); }, HOLD_TO_SNAP + 100);
        } else {
            feedUp();
        }
    }
}
