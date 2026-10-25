package mx.dibujos.kiosko;

import android.app.Instrumentation;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

/** Verificaciones fuera de pantalla, en un paquete de pruebas sin acceso a los dibujos del niño. */
public final class EditChecks extends Instrumentation {
    private int checks;
    private final StringBuilder report=new StringBuilder();
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            runOnMainSync(this::verify);
            result.putString("stream",report.toString()+"\nPASS "+checks+" comprobaciones de edición real en Android\n");finish(-1,result);
        }catch(Throwable e){result.putString("stream",report.toString()+"\nFAIL "+e);finish(0,result);}
    }
    private void check(boolean condition,String label){if(!condition)throw new AssertionError(label);checks++;report.append("PASS ").append(label).append('\n');}
    private DrawView view(){DrawView v=new DrawView(getTargetContext());v.setShapeAssist(false);v.layout(0,0,256,256);return v;}
    private Bitmap white(){Bitmap b=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888);b.eraseColor(Color.WHITE);return b;}
    private void touch(DrawView view,int action,float x,float y,long start){MotionEvent e=MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,x,y,0);view.onTouchEvent(e);e.recycle();}
    private void stroke(DrawView view,float x0,float y0,float x1,float y1){long start=SystemClock.uptimeMillis();touch(view,0,x0,y0,start);for(int i=1;i<=24;i++)touch(view,2,x0+(x1-x0)*i/24,y0+(y1-y0)*i/24,start);touch(view,1,x1,y1,start);}
    private void verify(){
        Bitmap legacy=white();Paint black=new Paint();black.setColor(Color.BLACK);black.setStrokeWidth(20);
        new Canvas(legacy).drawLine(40,128,216,128,black);
        DrawView v=view();v.load(legacy);v.setEraser(32);stroke(v,128,80,128,176);
        Bitmap erased=v.snapshot();check(erased.getPixel(128,128)==Color.WHITE,"borrador elimina tinta de un PNG antiguo");
        check(erased.getPixel(60,128)==Color.BLACK,"no borra los trazos vecinos");
        check(legacy.getPixel(128,128)==Color.BLACK,"no modifica el archivo/bitmap original al abrirlo");
        v.undo();check(v.snapshot().getPixel(128,128)==Color.BLACK,"deshacer recupera la tinta antigua borrada");
        v.setPen(10);stroke(v,90,30,90,70);check(v.snapshot().getPixel(90,50)==Color.BLACK,"permite añadir tinta al dibujo reabierto");
        v.undo();check(v.snapshot().getPixel(90,50)==Color.WHITE,"deshacer quita el trazo nuevo sin quitar el dibujo guardado");

        Bitmap paper=white();for(int y=0;y<256;y++)for(int x=0;x<256;x++){int g=30+x/2+y/5;paper.setPixel(x,y,Color.rgb(g,g,g));}
        Bitmap content=paper.copy(Bitmap.Config.ARGB_8888,true);new Canvas(content).drawRect(0,0,256,256,black);
        DrawView photo=view();photo.loadEditable(content,paper,false);photo.setMandala(true);photo.setEraser(30);stroke(photo,132,80,132,176);
        Bitmap revealed=photo.snapshot();check(revealed.getPixel(132,100)==paper.getPixel(132,100),"el borrador recupera el papel/foto original");
        check(revealed.getPixel(154,118)==paper.getPixel(154,118),"mandala recupera la foto sin girar ni reflejar el papel");

        DrawView pending=new DrawView(getTargetContext());pending.setShapeAssist(false);pending.loadEditable(legacy,null,true);pending.layout(0,0,256,256);
        check(pending.isDirty()&&pending.snapshot().getPixel(128,128)==Color.BLACK,"cargar antes del layout conserva el contenido y el guardado pendiente");
        pending.layout(0,0,384,256);pending.setEraser(30);stroke(pending,192,80,192,176);
        check(pending.snapshot().getPixel(192,128)==Color.WHITE,"el dibujo sigue editable tras cambiar el tamaño del lienzo");
        pending.clear();check(!pending.isDirty()&&pending.snapshot().getPixel(192,128)==Color.WHITE,"una hoja nueva no conserva tinta ni cambios pendientes");
        pending.setPen(10);long start=SystemClock.uptimeMillis();touch(pending,0,50,50,start);touch(pending,2,100,50,start);pending.setActive(false);
        check(!pending.isDrawing()&&pending.isDirty(),"al suspender se termina el trazo para poder guardarlo");

        DrawingProjects store=new DrawingProjects(getTargetContext());Uri id=Uri.parse("content://editing-tests/photo/1");
        check(store.save(id,content,paper),"guarda proyecto con dibujo y papel separados");
        DrawingProjects.Page loaded=store.load(id);check(loaded!=null&&loaded.paper!=null&&loaded.picture.getPixel(128,128)==Color.BLACK,"reabre las dos capas del proyecto");
        DrawView reopened=view();reopened.loadEditable(loaded.picture,loaded.paper,false);reopened.setEraser(30);stroke(reopened,128,80,128,176);
        check(reopened.snapshot().getPixel(128,128)==paper.getPixel(128,128),"el proyecto guardado vuelve a ser editable con borrador");
        Bitmap invalid=white();invalid.recycle();check(!store.save(id,invalid,paper),"un fallo de escritura se detecta");
        check(store.load(id).picture.getPixel(128,128)==Color.BLACK,"la escritura atómica conserva el proyecto anterior si falla");
        Uri plain=Uri.parse("content://editing-tests/drawing/2");check(store.save(plain,legacy,null),"guarda un dibujo sin foto de fondo");
        check(store.load(plain).paper==null,"el papel blanco no se convierte en una imagen protegida");
    }
}
