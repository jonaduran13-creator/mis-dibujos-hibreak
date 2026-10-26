package mx.dibujos.kiosko;

/** Gravedad estimada del acelerómetro, sin depender del sensor virtual de MediaTek.
 * El HiBreak anuncia TYPE_GRAVITY pero entrega (0,0,0). El filtro deja pasar
 * inclinaciones suaves y amortigua los golpes, con igual respuesta a 50 o 100 Hz. */
final class Tilt {
    private float ax, ay;
    private boolean ready;
    private float x,y;
    void update(float rawX,float rawY,int rotation,float seconds) {
        if (!Float.isFinite(rawX)||!Float.isFinite(rawY)||!Float.isFinite(seconds))return;
        float alpha=ready?1f-(float)Math.exp(-Math.max(.001f,Math.min(.2f,seconds))/.10f):1f;
        ready=true;ax+=(rawX-ax)*alpha;ay+=(rawY-ay)*alpha;
        switch(rotation){
            case 1:x=ay;y=ax;break;
            case 2:x=ax;y=-ay;break;
            case 3:x=-ay;y=-ax;break;
            default:x=-ax;y=ay;
        }
    }
    float x(){return x;}
    float y(){return y;}
}
