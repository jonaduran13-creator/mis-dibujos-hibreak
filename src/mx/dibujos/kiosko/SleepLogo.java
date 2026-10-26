package mx.dibujos.kiosko;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.util.Log;
import xrz.framework.manager.XrzEinkManager;

/**
 * Entrega al firmware la imagen de reposo, antes de apagar el controlador de tinta.
 *
 * Importante (visto al decompilar DisplayPowerController de Bigme): si esta llamada falla o
 * devuelve null, el sistema no pone imagen pero queda marcado "con imagen", y al despertar
 * intenta quitar una vista vieja: se cae la interfaz del sistema. Por eso la imagen se deja
 * PREPARADA de antemano (se entrega al instante) y la llamada nunca falla ni devuelve null.
 */
final class SleepLogo {
    static final int[] IMAGES={R.drawable.jesus_ninos,R.drawable.arca,R.drawable.buen_pastor,
        R.drawable.nacimiento,R.drawable.jesus_jardin,R.drawable.paloma,R.drawable.mar};
    private static final String TAG="MisDibujosReposo";
    private XrzEinkManager manager;
    private final Context context;
    private final SharedPreferences prefs;
    /** Próxima imagen, ya dibujada, y con qué ajuste se dibujó (negro o el fondo n). */
    private volatile Bitmap ready;
    private volatile String readyKey;

    SleepLogo(Context c, SharedPreferences p){ context=c; prefs=p; }

    void register(){
        try {
            manager=new XrzEinkManager(context);
            manager.registerSuspendCallback(new XrzEinkManager.SuspendCallback(){
                @Override public Bitmap onPrepareSuspendLogo(){ return deliver(); }
            });
            Log.i(TAG,"Callback de reposo registrado");
        } catch(Throwable e){Log.w(TAG,"Reposo nativo no disponible",e);manager=null;}
        prepareNext();
    }

    private Bitmap deliver(){
        Bitmap b;
        try {
            b=ready;
            if(b==null||b.isRecycled()||!key().equals(readyKey))b=make(key());
            ready=null;
            if(!prefs.getBoolean("sleepBlack",false))
                prefs.edit().putInt("wallpaper",(index()+1)%IMAGES.length).apply();
        } catch(Throwable e){
            // Nunca null: una imagen lisa evita el error del sistema al despertar.
            Log.w(TAG,"No se pudo preparar la imagen de reposo",e);
            b=solid(prefs.getBoolean("sleepBlack",false)?Color.BLACK:Color.WHITE);
        }
        Log.i(TAG,"Imagen de reposo entregada: "+b.getWidth()+"x"+b.getHeight());
        prepareNext();
        return b;
    }

    /** Dibuja en segundo plano la imagen del próximo descanso. */
    private void prepareNext(){
        new Thread(()->{
            try { String k=key(); Bitmap b=make(k); ready=b; readyKey=k; }
            catch(Throwable e){ Log.w(TAG,"Sin imagen preparada",e); }
        },"reposo").start();
    }

    private int index(){ return Math.floorMod(prefs.getInt("wallpaper",0),IMAGES.length); }
    private String key(){ return prefs.getBoolean("sleepBlack",false)?"negro":"fondo"+index(); }

    private Point size(){
        Point size=new Point();
        context.getSystemService(android.view.WindowManager.class).getDefaultDisplay().getRealSize(size);
        return size;
    }

    private Bitmap solid(int color){
        Point s=size();
        Bitmap out=Bitmap.createBitmap(Math.max(1,s.x),Math.max(1,s.y),Bitmap.Config.ARGB_8888);
        out.eraseColor(color);
        return out;
    }

    private synchronized Bitmap make(String k){
        if(k.equals("negro"))return solid(Color.BLACK);
        Bitmap out=solid(Color.WHITE);
        int w=out.getWidth(),h=out.getHeight();
        int index=Integer.parseInt(k.substring("fondo".length()));
        Bitmap art=BitmapFactory.decodeResource(context.getResources(),IMAGES[index]);
        float scale=Math.max(w/(float)art.getWidth(),h/(float)art.getHeight());
        float aw=art.getWidth()*scale,ah=art.getHeight()*scale;
        new Canvas(out).drawBitmap(art,null,new RectF((w-aw)/2,(h-ah)/2,(w+aw)/2,(h+ah)/2),new Paint(Paint.FILTER_BITMAP_FLAG));
        art.recycle();
        Log.i(TAG,"Fondo "+(index+1)+" de "+IMAGES.length+" preparado");
        return out;
    }

    void close(){if(manager!=null)try{manager.unRegisterSuspendCallback();}catch(Throwable ignored){}}
}
