package mx.dibujos.kiosko;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;

/**
 * Lienzo pensado para tinta electrónica: solo negro y blanco, sin antialias
 * (los grises intermedios fuerzan refrescos lentos) y redibujando únicamente
 * el rectángulo que cambió en cada movimiento del dedo.
 */
public class DrawView extends View implements Lienzo {

    private static final int MAX_UNDO = 40;

    private static class Stroke {
        final Path path = new Path();
        final ArrayList<android.graphics.PointF> points = new ArrayList<>();
        final float width;
        final boolean eraser;
        Stroke(float width, boolean eraser) {
            this.width = width;
            this.eraser = eraser;
        }
    }

    private Bitmap background; // solo el papel: blanco o foto de referencia
    private BitmapShader eraserShader;
    private Bitmap committed; // fondo + trazos que ya no se pueden deshacer
    private Bitmap canvasBitmap; // lo que se ve en pantalla
    private Canvas canvas;
    private final ArrayList<Stroke> strokes = new ArrayList<>();
    private Stroke current;
    private float lastX, lastY;
    private int activePointer = -1;

    private float penWidth = 6f;
    private boolean eraser = false;
    private boolean dirty = false;
    private Bitmap pendingBackground;
    private Bitmap pendingPaper;
    private boolean pendingDirty;
    private boolean active=true;
    private final android.graphics.Matrix[] symmetry=new android.graphics.Matrix[12];
    private final float[] segmentPoints=new float[4];
    private final Path transformedPath=new Path();
    private Listener listener;
    private boolean mandala;
    private boolean assisted=true;
    @Override public void setShapeAssist(boolean enabled){assisted=enabled;}
    public void setMandala(boolean enabled){mandala=enabled;invalidate();}

    private final Paint paint = new Paint();
    private final Rect dirtyRect = new Rect();

    public DrawView(Context c) {
        super(c);
        paint.setAntiAlias(false);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        setBackgroundColor(Color.WHITE);
    }

    @Override public View view() { return this; }
    @Override public boolean isDrawing() { return current != null; }
    @Override public void setActive(boolean enabled) {if(!enabled&&current!=null)finishStroke();active=enabled;}
    @Override public void onResume() {}

    public void setListener(Listener l) { listener = l; }

    public void setPen(float width) {
        penWidth = width;
        eraser = false;
    }

    public void setEraser(float width) {
        penWidth = width;
        eraser = true;
    }

    public boolean isDirty() { return dirty; }
    public void markSaved() { dirty = false; }
    @Override public void markDirty(){dirty=true;}
    public boolean canUndo() { return !strokes.isEmpty(); }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        if (w == 0 || h == 0) return;
        for(int i=0;i<12;i++){
            symmetry[i]=new android.graphics.Matrix();
            if(i%2==1)symmetry[i].setScale(-1,1,w/2f,h/2f);
            symmetry[i].postRotate((i/2)*60,w/2f,h/2f);
        }
        if (pendingBackground != null) {
            Bitmap picture=pendingBackground,paper=pendingPaper;boolean modified=pendingDirty;
            pendingBackground=null;pendingPaper=null;
            installDocument(w,h,picture,paper,modified);
        } else {
            installDocument(w,h,canvasBitmap,background,dirty);
        }
    }

    private void installDocument(int w,int h,Bitmap picture,Bitmap paper,boolean modified){
        setBackgroundBitmap(w,h,paper);
        if(picture!=null)drawCentered(new Canvas(committed),picture);
        canvasBitmap=committed.copy(Bitmap.Config.ARGB_8888,true);canvas=new Canvas(canvasBitmap);
        strokes.clear();current=null;dirty=modified;invalidate();
    }

    /**
     * El fondo (hoja blanca, foto o dibujo abierto) se guarda aparte: el borrador
     * lo vuelve a pintar en lugar de poner blanco, así no borra la foto.
     */
    private void setBackgroundBitmap(int w, int h, Bitmap src) {
        Bitmap bgr = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bgr.eraseColor(Color.WHITE);
        if (src != null) drawCentered(new Canvas(bgr), src);
        background = bgr;
        eraserShader = new BitmapShader(background, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        committed = background.copy(Bitmap.Config.ARGB_8888, true);
    }

    private void drawCentered(Canvas c, Bitmap b) {
        float scale = Math.min((float) c.getWidth() / b.getWidth(), (float) c.getHeight() / b.getHeight());
        scale = Math.min(scale, 1f);
        float dw = b.getWidth() * scale, dh = b.getHeight() * scale;
        RectF dst = new RectF((c.getWidth()-dw)/2,(c.getHeight()-dh)/2,(c.getWidth()+dw)/2,(c.getHeight()+dh)/2);
        c.drawBitmap(b, null, dst, null);
    }

    /** Hoja en blanco. */
    public void clear() {
        strokes.clear();
        current = null;
        pendingBackground=null;pendingPaper=null;
        dirty=false;
        if (committed == null) return;
        setBackgroundBitmap(getWidth(), getHeight(), null);
        rebuild();
        dirty = false;
    }

    /** Abre un dibujo guardado o una foto para pintar encima. */
    public void load(Bitmap b) {
        loadEditable(b,null,false);
    }

    @Override public void loadEditable(Bitmap content,Bitmap paper,boolean modified){
        if(getWidth()==0||getHeight()==0){
            pendingBackground=content;pendingPaper=paper;pendingDirty=modified;dirty=modified;return;
        }
        pendingBackground=null;pendingPaper=null;
        installDocument(getWidth(),getHeight(),content,paper,modified);
    }

    /** Una foto nueva cuenta como cambio sin guardar. */
    public void loadPhoto(Bitmap b) {
        loadEditable(b,b,true);
    }

    public void undo() {
        if (strokes.isEmpty()) return;
        strokes.remove(strokes.size() - 1);
        rebuild();
        dirty = true;
        if (listener != null) listener.onStrokeFinished();
    }

    private void rebuild() {
        canvas.drawBitmap(committed, 0, 0, null);
        for (Stroke s : strokes) drawStroke(canvas, s);
        invalidate();
    }

    public Bitmap snapshot() {
        return canvasBitmap == null ? null : canvasBitmap.copy(Bitmap.Config.ARGB_8888, false);
    }

    private void applyPaint(Stroke s) {
        paint.setStrokeWidth(s.width);
        paint.setColor(Color.BLACK);
        paint.setShader(s.eraser ? eraserShader : null);
    }

    private void drawStroke(Canvas c, Stroke s) {
        applyPaint(s);
        int copies=mandala?12:1;
        for(int i=0;i<copies;i++){
            if(mandala){transformedPath.set(s.path);transformedPath.transform(symmetry[i]);c.drawPath(transformedPath,paint);}
            else c.drawPath(s.path,paint);
        }
    }

    private void segment(float x0,float y0,float x1,float y1){
        int copies=mandala?12:1;
        for(int i=0;i<copies;i++){
            segmentPoints[0]=x0;segmentPoints[1]=y0;segmentPoints[2]=x1;segmentPoints[3]=y1;
            if(mandala)symmetry[i].mapPoints(segmentPoints);
            canvas.drawLine(segmentPoints[0],segmentPoints[1],segmentPoints[2],segmentPoints[3],paint);
        }
        if(mandala)invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (canvasBitmap != null) c.drawBitmap(canvasBitmap, 0, 0, null);
        if(mandala){
            Paint guide=new Paint();guide.setColor(Color.BLACK);guide.setStyle(Paint.Style.STROKE);guide.setStrokeWidth(1);
            guide.setPathEffect(new android.graphics.DashPathEffect(new float[]{2,18},0));
            float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(cx,cy)-12;
            c.drawCircle(cx,cy,r,guide);c.drawCircle(cx,cy,r*.5f,guide);
            for(int i=0;i<6;i++){double a=i*Math.PI/3;c.drawLine(cx,cy,cx+(float)Math.cos(a)*r,cy+(float)Math.sin(a)*r,guide);}
            guide.setPathEffect(null);c.drawCircle(cx,cy,4,guide);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (canvas == null || !active) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                if(listener!=null)listener.onStrokeStarted();
                activePointer = e.getPointerId(0);
                float x = e.getX(), y = e.getY();
                current = new Stroke(penWidth, eraser);
                current.points.add(new android.graphics.PointF(x,y));
                current.path.moveTo(x, y);
                current.path.lineTo(x + 0.1f, y + 0.1f);
                lastX = x;
                lastY = y;
                applyPaint(current);
                segment(x,y,x+.1f,y+.1f);
                invalidateAround(x, y, x, y);
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (current == null) return true;
                int idx = e.findPointerIndex(activePointer);
                if (idx < 0) return true;
                applyPaint(current);
                float minX = lastX, maxX = lastX, minY = lastY, maxY = lastY;
                int n = e.getHistorySize();
                for (int i = 0; i <= n; i++) {
                    float x = i < n ? e.getHistoricalX(idx, i) : e.getX(idx);
                    float y = i < n ? e.getHistoricalY(idx, i) : e.getY(idx);
                    segment(lastX,lastY,x,y);
                    current.path.lineTo(x, y);
                    current.points.add(new android.graphics.PointF(x,y));
                    lastX = x;
                    lastY = y;
                    minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                }
                invalidateAround(minX, minY, maxX, maxY);
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                finishStroke();
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                if (e.getPointerId(e.getActionIndex()) == activePointer) finishStroke();
                return true;
        }
        return true;
    }

    private void finishStroke() {
        if (current == null) return;
        boolean corrected=false;
        if(assisted&&!current.eraser){
            java.util.List<android.graphics.PointF> shape=ShapeSnap.recognize(current.points);
            if(shape!=null){
                current.path.reset();current.path.moveTo(shape.get(0).x,shape.get(0).y);
                for(int i=1;i<shape.size();i++)current.path.lineTo(shape.get(i).x,shape.get(i).y);
                corrected=true;Sounds.play(Sounds.SNAP);
            }
        }
        strokes.add(current);
        current = null;
        activePointer = -1;
        dirty = true;
        if(corrected)rebuild();
        if (strokes.size() > MAX_UNDO) {
            drawStroke(new Canvas(committed), strokes.remove(0));
        }
        if (listener != null) listener.onStrokeFinished();
    }

    private void invalidateAround(float x0, float y0, float x1, float y1) {
        int pad = (int) Math.ceil(penWidth / 2f) + 2;
        dirtyRect.set((int) x0 - pad, (int) y0 - pad, (int) x1 + pad, (int) y1 + pad);
        invalidate(dirtyRect);
    }
}
