package mx.dibujos.kiosko;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * Botón con icono dibujado a mano en negro puro, para que un niño que todavía
 * no lee lo entienda y para que la tinta electrónica lo pinte nítido.
 * El botón seleccionado se invierte (fondo negro, icono blanco).
 */
public class ToolButton extends View {

    public static final int PEN_S = 0, PEN_M = 1, PEN_L = 2, ERASER = 3, UNDO = 4, NEW_PAGE = 5, GALLERY = 6, BACK = 7,
            CAMERA = 8, SHUTTER = 9, SWITCH = 10, HOME = 11, PENCIL = 12, PIANO = 13, SLEEP = 14, ROTATE = 15,
            MANDALA=16,BELL=17,ORGAN=18,FLUTE=19,STRINGS=20,FILTER_NORMAL=21,EYES=22,BALLOON=23,MIRROR=24,PIXELS=25,OUTLINE=26,LIGHTPHOTO=27,RETAKE=28,ASSISTED=29,FREE=30,LAMP=31,
            TIP_PEN=32,TIP_PENCIL=33,TIP_BRUSH=34,TIP_DASH=35,STAMP=36,STAMP_FIRST=37;
    /** Punta de cada tipo de Lienzo.BRUSH_*, en orden. */
    public static final int[] TIPS={TIP_PEN,TIP_PENCIL,TIP_BRUSH,TIP_DASH};

    private int icon;
    public void setIcon(int value){icon=value;setContentDescription(LABELS[icon]);invalidate();}
    private boolean selected;
    private boolean bare;
    public void setBare(boolean value) { bare=value; invalidate(); }
    private static final String[] LABELS={"Fino","Medio","Grueso","Goma","Deshacer","Nueva","Dibujos","Volver","Fotos","Tomar foto","Cámara","Inicio","Dibujar","Piano","Dormir","Rotar","Mandala","Campanas","Órgano","Flauta","Cuerdas","Normal","Ojotes","Globo","Espejo","Invertido","Contornos","Foto clara","Otra foto","Asistido","Libre","Linterna",
        "Lápiz","Grafito","Pincel","Puntos","Sellos",
        "Estrella","Corazón","Flor","Sol","Nube","Casita","Gato","Pez","Mariposa"}; // los sellos, en el orden de Stamps
    private final Paint p = new Paint();
    private final RectF r = new RectF();
    private final Path path = new Path();

    public ToolButton(Context c, int icon) {
        super(c);
        this.icon = icon;
        p.setAntiAlias(true);
        setContentDescription(LABELS[icon]);
        setFocusable(true);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        setClickable(true);
    }

    public void setChosen(boolean s) {
        if (selected == s) return;
        selected = s;
        setSelected(s);
        invalidate();
    }

    private boolean wasPressed;

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate(); // en tinta no hay animación de toque: el botón se invierte mientras se presiona
        if (wasPressed && !isPressed()) {
            // Los refrescos parciales de la tinta a veces dejan botones vecinos sin pintar:
            // se vuelve a pintar toda la barra un instante después.
            postDelayed(this::repaintBar, 150);
        }
        wasPressed = isPressed();
    }

    private void repaintBar() {
        if (!(getParent() instanceof android.view.ViewGroup)) return;
        android.view.ViewGroup bar = (android.view.ViewGroup) getParent();
        for (int i = 0; i < bar.getChildCount(); i++) bar.getChildAt(i).invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        p.setAlpha(isEnabled()?255:90);
        int w = getWidth(), h = getHeight();
        float density=getResources().getDisplayMetrics().density;
        boolean label=!bare && h>=47*density;
        float area=label?h-19*density:h;
        float cx = w / 2f, cy = area / 2f, u = Math.min(w, area) / 11f;
        boolean dark = selected != isPressed();
        int fg = dark ? Color.WHITE : Color.BLACK;

        p.setStyle(Paint.Style.FILL);
        p.setColor(dark ? Color.BLACK : Color.WHITE);
        r.set(u * 0.4f, u * 0.4f, w - u * 0.4f, h - u * 0.4f);
        if (!bare) c.drawRoundRect(r, u, u, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(u * 0.35f);
        p.setColor(Color.BLACK);
        if (!bare) c.drawRoundRect(r, u, u, p);

        if(label){p.setStyle(Paint.Style.FILL);p.setColor(fg);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));p.setTextSize(10*density);c.drawText(LABELS[icon],cx,h-8*density,p);}
        p.setColor(fg);
        if(icon>=STAMP_FIRST){drawStamp(c,icon-STAMP_FIRST,cx,cy,u*3.3f,u*.4f);return;}
        switch (icon) {
            case STAMP: {
                // Un sello de goma: mango, base y la estrellita que deja.
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(u*.45f);
                r.set(cx-u*.8f,cy-u*3.2f,cx+u*.8f,cy-u*1.4f);c.drawRoundRect(r,u*.6f,u*.6f,p);
                r.set(cx-u*2.4f,cy-u*1.4f,cx+u*2.4f,cy-u*.3f);c.drawRect(r,p);
                drawStamp(c,Stamps.STAR,cx,cy+u*1.6f,u*1.5f,u*.35f);break;
            }
            case TIP_PEN: case TIP_PENCIL: case TIP_BRUSH: case TIP_DASH: {
                path.reset();path.moveTo(cx-u*3.2f,cy+u);
                path.cubicTo(cx-u*1.5f,cy-u*3,cx+u*.2f,cy+u*3,cx+u*3.2f,cy-u*1.2f);
                p.setStyle(Paint.Style.STROKE);
                if(icon==TIP_PEN){p.setStrokeWidth(u*.7f);c.drawPath(path,p);}
                else if(icon==TIP_DASH){p.setStrokeWidth(u*.7f);p.setStrokeCap(Paint.Cap.BUTT);
                    p.setPathEffect(new android.graphics.DashPathEffect(new float[]{u*1.1f,u*.8f},0));c.drawPath(path,p);
                    p.setPathEffect(null);p.setStrokeCap(Paint.Cap.ROUND);}
                else if(icon==TIP_PENCIL){p.setStrokeWidth(u*.22f);
                    // grafito: rayitas finas y desparejas a lo largo del trazo
                    android.graphics.PathMeasure m=new android.graphics.PathMeasure(path,false);float[] at=new float[2];
                    for(float d=0;d<m.getLength();d+=u*.45f){m.getPosTan(d,at,null);float o=((int)(d/u*7)%3-1)*u*.25f;
                        c.drawLine(at[0]-u*.35f,at[1]+u*.35f+o,at[0]+u*.35f,at[1]-u*.35f+o,p);}}
                else {android.graphics.PathMeasure m=new android.graphics.PathMeasure(path,false);float[] at=new float[2];
                    p.setStyle(Paint.Style.FILL);float len=m.getLength();
                    for(float d=0;d<=len;d+=u*.2f){m.getPosTan(d,at,null);c.drawCircle(at[0],at[1],u*(.15f+1.1f*(float)Math.sin(Math.PI*d/len)),p);}}
                break;
            }
            case ASSISTED: {
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(u*.4f);c.drawCircle(cx-u*1.8f,cy-u*.8f,u*1.3f,p);
                path.reset();path.moveTo(cx+u*1.4f,cy-u*2.4f);path.lineTo(cx+u*3.2f,cy+u*1.5f);path.lineTo(cx-u*.2f,cy+u*1.5f);path.close();c.drawPath(path,p);break;
            }
            case FREE: {
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(u*.5f);path.reset();path.moveTo(cx-u*3,cy+u*1.5f);
                path.cubicTo(cx-u*2.8f,cy-u*3.2f,cx-u*.7f,cy-u*3,cx-u*.6f,cy);
                path.cubicTo(cx-u*.5f,cy+u*3.5f,cx+u*1.3f,cy+u*1.5f,cx+u*1.2f,cy-u*.2f);
                path.cubicTo(cx+u,cy-u*2,cx+u*3.3f,cy-u*2,cx+u*3,cy+u*1.5f);c.drawPath(path,p);break;
            }
            case LAMP: {
                // Linterna acostada: mango, cabeza más ancha y rayos de luz (rellenos si está prendida).
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(u*.45f);
                r.set(cx-u*3.2f,cy-u*.8f,cx-u*.4f,cy+u*.8f);c.drawRoundRect(r,u*.3f,u*.3f,p);
                path.reset();path.moveTo(cx-u*.4f,cy-u*.8f);path.lineTo(cx+u*.8f,cy-u*1.5f);path.lineTo(cx+u*.8f,cy+u*1.5f);
                path.lineTo(cx-u*.4f,cy+u*.8f);path.close();
                if(selected){p.setStyle(Paint.Style.FILL);c.drawPath(path,p);p.setStyle(Paint.Style.STROKE);}else c.drawPath(path,p);
                c.drawLine(cx+u*1.7f,cy,cx+u*3.2f,cy,p);
                c.drawLine(cx+u*1.5f,cy-u*1.4f,cx+u*2.8f,cy-u*2.4f,p);
                c.drawLine(cx+u*1.5f,cy+u*1.4f,cx+u*2.8f,cy+u*2.4f,p);break;
            }
            case MANDALA: {
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(u*.4f);
                for(int i=0;i<8;i++){c.save();c.rotate(i*45,cx,cy);r.set(cx-u*.75f,cy-u*3.4f,cx+u*.75f,cy-u*.4f);c.drawOval(r,p);c.restore();}
                c.drawCircle(cx,cy,u*.7f,p);break;
            }
            case BELL: {
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(u*.5f);
                path.reset();path.moveTo(cx-u*2.8f,cy+u*1.6f);path.lineTo(cx-u*2,cy+u*.6f);path.lineTo(cx-u*2,cy-u*.9f);
                path.cubicTo(cx-u*2,cy-u*3.1f,cx+u*2,cy-u*3.1f,cx+u*2,cy-u*.9f);
                path.lineTo(cx+u*2,cy+u*.6f);path.lineTo(cx+u*2.8f,cy+u*1.6f);path.close();c.drawPath(path,p);
                c.drawCircle(cx,cy+u*2.5f,u*.5f,p);c.drawLine(cx,cy-u*3,cx,cy-u*2.6f,p);break;
            }
            case ORGAN: {
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(u*.45f);
                for(int i=-1;i<=1;i++){float top=cy-(i==0?3.3f:2.2f)*u;r.set(cx+i*1.8f*u-u*.55f,top,cx+i*1.8f*u+u*.55f,cy+u);c.drawRoundRect(r,u*.25f,u*.25f,p);}
                c.drawRect(cx-u*3,cy+u,cx+u*3,cy+u*2.9f,p);
                for(int i=-1;i<=1;i++)c.drawLine(cx+i*1.5f*u,cy+u,cx+i*1.5f*u,cy+u*2.9f,p);break;
            }
            case FLUTE: {
                c.save();c.rotate(-35,cx,cy);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(u*.45f);
                r.set(cx-u*3.6f,cy-u*.7f,cx+u*3.6f,cy+u*.7f);c.drawRoundRect(r,u*.3f,u*.3f,p);
                p.setStyle(Paint.Style.FILL);for(int i=-2;i<=2;i++)c.drawCircle(cx+i*u,cy,u*.23f,p);c.restore();break;
            }
            case STRINGS: {
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(u*.4f);
                path.reset();path.moveTo(cx,cy-u*1.8f);path.cubicTo(cx-u*2.7f,cy-u*2,cx-u*2.2f,cy,cx-u,cy+u*.3f);
                path.cubicTo(cx-u*3,cy+u*2.8f,cx+u*3,cy+u*2.8f,cx+u,cy+u*.3f);
                path.cubicTo(cx+u*2.2f,cy,cx+u*2.7f,cy-u*2,cx,cy-u*1.8f);c.drawPath(path,p);
                c.drawLine(cx,cy-u*3.4f,cx,cy+u*1.8f,p);c.drawLine(cx-u*.5f,cy-u*3.4f,cx+u*.5f,cy-u*3.4f,p);
                c.drawLine(cx+u*2.6f,cy-u*3,cx+u*3.4f,cy+u*3,p);break;
            }
            case EYES: {
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(u*.45f);
                c.drawCircle(cx-u*1.7f,cy,u*1.55f,p);c.drawCircle(cx+u*1.7f,cy,u*1.55f,p);
                p.setStyle(Paint.Style.FILL);c.drawCircle(cx-u*1.5f,cy,u*.65f,p);c.drawCircle(cx+u*1.9f,cy,u*.65f,p);break;
            }
            case BALLOON: {
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(u*.45f);r.set(cx-u*2.2f,cy-u*3,cx+u*2.2f,cy+u*1.6f);c.drawOval(r,p);
                c.drawLine(cx,cy+u*1.6f,cx-u*.4f,cy+u*2.2f,p);c.drawLine(cx-u*.4f,cy+u*2.2f,cx+u*.4f,cy+u*2.2f,p);
                c.drawLine(cx,cy+u*2.2f,cx+u*.5f,cy+u*3.5f,p);break;
            }
            case MIRROR: {
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(u*.4f);c.drawLine(cx,cy-u*3.3f,cx,cy+u*3.3f,p);
                for(int sign:new int[]{-1,1}){path.reset();path.moveTo(cx+sign*u*.8f,cy-u*2.2f);path.lineTo(cx+sign*u*3,cy);path.lineTo(cx+sign*u*.8f,cy+u*2.2f);path.close();c.drawPath(path,p);}break;
            }
            case PIXELS: {
                // Filtro "Invertido": círculo mitad lleno, mitad vacío.
                r.set(cx-u*3f,cy-u*3f,cx+u*3f,cy+u*3f);
                p.setStyle(Paint.Style.FILL);c.drawArc(r,90,180,true,p);
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(u*.45f);c.drawOval(r,p);break;
            }
            case PIANO: {
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(u*.45f);
                r.set(cx-3.3f*u,cy-2.6f*u,cx+3.3f*u,cy+2.6f*u);c.drawRoundRect(r,u*.4f,u*.4f,p);
                for(int i=1;i<4;i++)c.drawLine(cx-3.3f*u+i*1.65f*u,cy-2.6f*u,cx-3.3f*u+i*1.65f*u,cy+2.6f*u,p);
                p.setStyle(Paint.Style.FILL);
                for(int i=1;i<4;i++){float x=cx-3.3f*u+i*1.65f*u;c.drawRect(x-.4f*u,cy-2.6f*u,x+.4f*u,cy+.2f*u,p);}break;
            }
            case SLEEP: {
                p.setStyle(Paint.Style.FILL); c.drawCircle(cx,cy,2.8f*u,p);
                p.setColor(dark?Color.BLACK:Color.WHITE);c.drawCircle(cx+1.4f*u,cy-1.1f*u,2.5f*u,p);break;
            }
            case PEN_S:
            case PEN_M:
            case PEN_L: {
                float rad = icon == PEN_S ? u * 0.6f : icon == PEN_M ? u * 1.3f : u * 2.3f;
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(cx, cy, rad, p);
                break;
            }
            case ERASER: {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(u * 0.5f);
                r.set(cx - u * 3, cy - u * 1.6f, cx + u * 3, cy + u * 1.6f);
                c.drawRoundRect(r, u * 0.6f, u * 0.6f, p);
                c.drawLine(cx - u * 0.6f, cy - u * 1.6f, cx - u * 0.6f, cy + u * 1.6f, p);
                break;
            }
            case UNDO:
            case BACK: {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(u * 0.6f);
                r.set(cx - u * 2.2f, cy - u * 2f, cx + u * 2.6f, cy + u * 2.6f);
                c.drawArc(r, 180, 200, false, p);
                path.reset();
                path.moveTo(cx - u * 3.4f, cy - u * 0.6f);
                path.lineTo(cx - u * 2.2f, cy + u * 1.2f);
                path.lineTo(cx - u * 0.8f, cy - u * 0.4f);
                c.drawPath(path, p);
                break;
            }
            case NEW_PAGE: {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(u * 0.5f);
                r.set(cx - u * 2.2f, cy - u * 3f, cx + u * 2.2f, cy + u * 3f);
                c.drawRect(r, p);
                p.setStrokeWidth(u * 0.6f);
                c.drawLine(cx - u * 1.2f, cy, cx + u * 1.2f, cy, p);
                c.drawLine(cx, cy - u * 1.2f, cx, cy + u * 1.2f, p);
                break;
            }
            case RETAKE:
            case FILTER_NORMAL:
            case CAMERA: {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(u * 0.5f);
                r.set(cx - u * 3.2f, cy - u * 1.8f, cx + u * 3.2f, cy + u * 2.4f);
                c.drawRoundRect(r, u * 0.6f, u * 0.6f, p);
                c.drawCircle(cx, cy + u * 0.3f, u * 1.2f, p);
                p.setStyle(Paint.Style.FILL);
                c.drawRect(cx - u * 1.2f, cy - u * 2.6f, cx + u * 1.2f, cy - u * 1.8f, p);
                break;
            }
            case SHUTTER: {
                float rad = Math.min(w, h) * 0.32f;
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(u * 0.6f);
                c.drawCircle(cx, cy, rad, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(cx, cy, rad * 0.72f, p);
                break;
            }
            case SWITCH: {
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(u*.4f);
                r.set(cx-u*3,cy-u*1.6f,cx+u*3,cy+u*2.1f);c.drawRoundRect(r,u*.4f,u*.4f,p);c.drawCircle(cx,cy+u*.2f,u,p);
                c.drawLine(cx-u*2,cy-u*3,cx+u*2,cy-u*3,p);c.drawLine(cx+u*2,cy-u*3,cx+u,cy-u*3.7f,p);
                c.drawLine(cx+u*2,cy+u*3.3f,cx-u*2,cy+u*3.3f,p);c.drawLine(cx-u*2,cy+u*3.3f,cx-u,cy+u*4,p);break;
            }
            case OUTLINE: {
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(u*.35f);c.drawRect(cx-u*2.5f,cy-u*3.2f,cx+u*2.5f,cy+u*3.2f,p);
                c.drawCircle(cx,cy-u*.3f,u*1.4f,p);c.drawArc(cx-u*1.8f,cy+u*.7f,cx+u*1.8f,cy+u*3.5f,180,180,false,p);break;
            }
            case LIGHTPHOTO: {
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(u*.4f);c.drawRect(cx-u*3.2f,cy-u*2.5f,cx+u*3.2f,cy+u*2.5f,p);
                c.drawCircle(cx+u*1.6f,cy-u*1.2f,u*.6f,p);path.reset();path.moveTo(cx-u*3,cy+u*2.2f);path.lineTo(cx-u,cy-u*.8f);path.lineTo(cx+u,cy+u*2.2f);path.lineTo(cx+u*2,cy+u*.8f);path.lineTo(cx+u*3,cy+u*2.2f);c.drawPath(path,p);break;
            }
            case ROTATE: {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(u * 0.55f);
                r.set(cx - u * 2.4f, cy - u * 2.4f, cx + u * 2.4f, cy + u * 2.4f);
                c.drawArc(r, 200, 140, false, p);
                c.drawArc(r, 20, 140, false, p);
                float ax = cx + u * 2.25f, ay = cy - u * 0.8f;
                c.drawLine(ax, ay, ax + u * 0.9f, ay - u * 1.0f, p);
                c.drawLine(ax, ay, ax - u * 1.1f, ay - u * 0.6f, p);
                float bx = cx - u * 2.25f, by = cy + u * 0.8f;
                c.drawLine(bx, by, bx - u * 0.9f, by + u * 1.0f, p);
                c.drawLine(bx, by, bx + u * 1.1f, by + u * 0.6f, p);
                break;
            }
            case HOME: {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(u * 0.5f);
                path.reset();
                path.moveTo(cx - u * 3, cy - u * 0.2f);
                path.lineTo(cx, cy - u * 3);
                path.lineTo(cx + u * 3, cy - u * 0.2f);
                c.drawPath(path, p);
                r.set(cx - u * 2.2f, cy - u * 0.6f, cx + u * 2.2f, cy + u * 2.8f);
                c.drawRect(r, p);
                p.setStyle(Paint.Style.FILL);
                c.drawRect(cx - u * 0.7f, cy + u * 1.0f, cx + u * 0.7f, cy + u * 2.8f, p);
                break;
            }
            case PENCIL: {
                // Lápiz en diagonal: cuerpo, punta y goma.
                c.save();
                c.rotate(-45, cx, cy);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(u * 0.5f);
                r.set(cx - u * 2.6f, cy - u * 0.9f, cx + u * 1.6f, cy + u * 0.9f);
                c.drawRect(r, p);
                c.drawLine(cx - u * 1.8f, cy - u * 0.9f, cx - u * 1.8f, cy + u * 0.9f, p);
                path.reset();
                path.moveTo(cx + u * 1.6f, cy - u * 0.9f);
                path.lineTo(cx + u * 3.4f, cy);
                path.lineTo(cx + u * 1.6f, cy + u * 0.9f);
                c.drawPath(path, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(cx + u * 3.1f, cy, u * 0.35f, p);
                c.restore();
                break;
            }
            case GALLERY: {
                p.setStyle(Paint.Style.FILL);
                float s = u * 1.6f, g = u * 0.4f;
                for (int i = 0; i < 2; i++)
                    for (int j = 0; j < 2; j++) {
                        float x = cx - s - g / 2 + i * (s + g);
                        float y = cy - s - g / 2 + j * (s + g);
                        c.drawRect(x, y, x + s, y + s, p);
                    }
                break;
            }
        }
    }

    /** Dibuja un sello en miniatura (botones de sellos). */
    private void drawStamp(Canvas c,int kind,float cx,float cy,float rad,float stroke){
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(stroke);
        for(java.util.List<android.graphics.PointF> s:Stamps.unit(kind)){
            path.reset();
            for(int i=0;i<s.size();i++){float x=cx+s.get(i).x*rad,y=cy+s.get(i).y*rad;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}
            c.drawPath(path,p);
        }
    }
}
