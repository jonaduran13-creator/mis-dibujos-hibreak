package mx.dibujos.kiosko;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.util.Log;
import xrz.framework.manager.XrzEinkManager;

/** Entrega al firmware la imagen de reposo, antes de apagar el controlador de tinta. */
final class SleepLogo {
    static final int[] IMAGES={R.drawable.jesus_ninos,R.drawable.arca,R.drawable.buen_pastor,
        R.drawable.nacimiento,R.drawable.jesus_jardin,R.drawable.paloma,R.drawable.mar};
    private XrzEinkManager manager;
    private final Context context;
    private final SharedPreferences prefs;
    SleepLogo(Context c, SharedPreferences p){ context=c; prefs=p; }
    void register(){
        try {
            manager=new XrzEinkManager(context);
            manager.registerSuspendCallback(new XrzEinkManager.SuspendCallback(){
                @Override public Bitmap onPrepareSuspendLogo(){
                    Bitmap b=make();
                    Log.i("MisDibujosReposo","Imagen de reposo entregada: "+b.getWidth()+"x"+b.getHeight());
                    return b;
                }
            });
            Log.i("MisDibujosReposo","Callback de reposo registrado");
        } catch(Throwable e){Log.w("MisDibujosReposo","Reposo nativo no disponible",e);manager=null;}
    }
    private synchronized Bitmap make(){
        android.graphics.Point size=new android.graphics.Point();
        context.getSystemService(android.view.WindowManager.class).getDefaultDisplay().getRealSize(size);
        int w=size.x,h=size.y;
        Bitmap out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(out);
        boolean black=prefs.getBoolean("sleepBlack",false);
        canvas.drawColor(black?Color.BLACK:Color.WHITE);
        if(!black){
            int index=Math.floorMod(prefs.getInt("wallpaper",0),IMAGES.length);
            Log.i("MisDibujosReposo","Fondo "+(index+1)+" de "+IMAGES.length);
            Bitmap art=BitmapFactory.decodeResource(context.getResources(),IMAGES[index]);
            float scale=Math.max(w/(float)art.getWidth(),h/(float)art.getHeight());
            float aw=art.getWidth()*scale,ah=art.getHeight()*scale;
            canvas.drawBitmap(art,null,new RectF((w-aw)/2,(h-ah)/2,(w+aw)/2,(h+ah)/2),new Paint(Paint.FILTER_BITMAP_FLAG));
            art.recycle();prefs.edit().putInt("wallpaper",(index+1)%IMAGES.length).apply();
        }
        return out;
    }
    void close(){if(manager!=null)try{manager.unRegisterSuspendCallback();}catch(Throwable ignored){}}
}
