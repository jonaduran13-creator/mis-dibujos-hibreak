package mx.dibujos.kiosko;

import android.content.Context;
import android.graphics.*;
import android.hardware.Camera;
import android.os.*;
import android.util.Log;
import android.view.*;
import android.widget.*;
import java.util.List;

/** Vista previa horizontal: el controlador de cámara resuelve el giro una sola vez. */
@SuppressWarnings("deprecation")
public class CameraPanel extends FrameLayout implements TextureView.SurfaceTextureListener {
    public interface Callback { void onPhoto(Bitmap photo); void onClose(); }
    private final Callback callback;
    private final TextureView texture;
    private ImageView filtered;
    private final LinearLayout bar;
    private TextView hint;
    private HandlerThread thread;
    private Handler bg;
    private volatile Camera camera;
    private volatile boolean paused,detached;
    private boolean front=true,capturing; // abre con la cámara selfie: los niños se toman fotos a sí mismos
    private volatile boolean processing;
    private Bitmap latest;
    private int latestMode=-1,latestRotation=-1;
    private volatile float viewportRatio=2;
    private volatile int sensorDegrees=90;
    private int processedRotation=-1;
    private volatile boolean sensorFront;
    private int filterMode,epoch;
    private volatile int previewWidth=1280,previewHeight=720,previewRotation;
    private long lastFrame;
    private final FunFilters.Tracker tracker=new FunFilters.Tracker();

    public CameraPanel(Context context,int ignoredBarHeight,Callback cb){
        super(context);callback=cb;setBackgroundColor(Color.BLACK);setClickable(true);
        LinearLayout row=new LinearLayout(context);
        LinearLayout col=new LinearLayout(context);col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout filters=new LinearLayout(context);filters.setBackgroundColor(Color.WHITE);
        filters.setPadding(dp(4),dp(3),dp(4),dp(3));
        int[] icons={ToolButton.FILTER_NORMAL,ToolButton.EYES,ToolButton.BALLOON,ToolButton.MIRROR,ToolButton.PIXELS};
        for(int i=0;i<icons.length;i++){
            final int mode=i;ToolButton button=new ToolButton(context,icons[i]);button.setChosen(i==0);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(60),1);if(i>0)lp.leftMargin=dp(4);
            filters.addView(button,lp);
            button.setOnClickListener(v->{
                filterMode=mode;epoch++;lastFrame=0;
                
                hint.setText("Centra tu carita");hint.setVisibility(mode==1||mode==2?VISIBLE:GONE);
                for(int j=0;j<filters.getChildCount();j++)((ToolButton)filters.getChildAt(j)).setChosen(j==mode);
                Log.i("MisDibujosCamara","Filtro "+FunFilters.NAMES[mode]);
            });
        }
        col.addView(filters);
        FrameLayout preview=new FrameLayout(context);
        texture=new TextureView(context);texture.setSurfaceTextureListener(this);
        preview.addView(texture,new LayoutParams(-1,-1));
        filtered=new ImageView(context);filtered.setScaleType(ImageView.ScaleType.CENTER_CROP);
        filtered.setBackgroundColor(Color.BLACK);
        filtered.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(b>t)viewportRatio=(r-l)/(float)(b-t);});
        preview.addView(filtered,new LayoutParams(-1,-1));
        hint=new TextView(context);hint.setText("Centra tu carita");hint.setTextColor(Color.BLACK);
        hint.setTextSize(14);hint.setGravity(Gravity.CENTER);hint.setPadding(dp(10),dp(3),dp(10),dp(3));
        hint.setBackgroundColor(Color.WHITE);hint.setVisibility(GONE);
        LayoutParams hp=new LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);hp.bottomMargin=dp(8);
        preview.addView(hint,hp);
        col.addView(preview,new LinearLayout.LayoutParams(-1,0,1));
        row.addView(col,new LinearLayout.LayoutParams(0,-1,1));
        bar=new LinearLayout(context);bar.setOrientation(LinearLayout.VERTICAL);bar.setBackgroundColor(Color.WHITE);
        bar.setPadding(dp(4),dp(4),dp(4),dp(4));
        ToolButton back=new ToolButton(context,ToolButton.BACK),shutter=new ToolButton(context,ToolButton.SHUTTER),flip=new ToolButton(context,ToolButton.SWITCH);
        bar.addView(back,new LinearLayout.LayoutParams(-1,0,1));shutter.setChosen(true);bar.addView(shutter,new LinearLayout.LayoutParams(-1,0,1.5f));
        bar.addView(flip,new LinearLayout.LayoutParams(-1,0,1));
        row.addView(bar,new LinearLayout.LayoutParams(dp(76),-1));addView(row,new LayoutParams(-1,-1));
        flip.setLabel("Cambiar");flip.setContentDescription("Cambiar cámara");
        back.setOnClickListener(v->callback.onClose());shutter.setOnClickListener(v->takePhoto());
        flip.setOnClickListener(v->{front=!front;epoch++;latest=null;release();open();});
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    public void addToBar(View view){bar.addView(view,new LinearLayout.LayoutParams(-1,0,1));}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();detached=false;thread=new HandlerThread("camara");thread.start();bg=new Handler(thread.getLooper());if(texture.isAvailable())open();}
    @Override protected void onDetachedFromWindow(){detached=true;paused=true;epoch++;release();if(thread!=null)thread.quitSafely();super.onDetachedFromWindow();}
    public void pause(){paused=true;epoch++;release();}
    public void resume(){paused=false;capturing=false;open();}
    @Override public void onSurfaceTextureAvailable(SurfaceTexture s,int w,int h){open();}
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture s,int w,int h){fitPreview();}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture s){epoch++;release();return true;}
    @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) {}

    private void frame(byte[] nv21,Camera source){
        long now=SystemClock.uptimeMillis();
        if(processing||paused||detached||capturing||now-lastFrame<180){source.addCallbackBuffer(nv21);return;}
        processing=true;lastFrame=now;
        final int mode=filterMode,version=epoch;
        final int display=getDisplay()==null?90:getDisplay().getRotation()*90;
        if(processedRotation!=display){tracker.reset();processedRotation=display;}
        try{
            // La misma imagen normalizada alimenta Normal, los filtros y el guardado.
            // Ningún efecto lee una textura con otra orientación ni estira sus píxeles.
            Bitmap raw=decode(nv21,previewWidth,previewHeight);
            Matrix matrix=new Matrix();
            int angle=sensorFront?(sensorDegrees+display)%360:(sensorDegrees-display+360)%360;
            matrix.postRotate(angle);if(sensorFront)matrix.postScale(-1,1);
            Bitmap upright=Bitmap.createBitmap(raw,0,0,raw.getWidth(),raw.getHeight(),matrix,true);
            if(upright!=raw)raw.recycle();
            int width=upright.getWidth(),height=upright.getHeight();float target=viewportRatio;
            int cw=width,ch=height;
            if(width/(float)height>target)cw=Math.max(1,Math.round(height*target));else ch=Math.max(1,Math.round(width/target));
            Bitmap crop=Bitmap.createBitmap(upright,(width-cw)/2,(height-ch)/2,cw,ch);
            if(crop!=upright)upright.recycle();
            Bitmap effect=tracker.apply(crop,mode);if(effect!=crop)crop.recycle();
            post(()->{
                if(!detached&&!paused&&version==epoch&&getDisplay()!=null&&getDisplay().getRotation()*90==display){
                    if(latest==null||latestMode!=mode||latestRotation!=display)Log.i("MisDibujosCamara","Vista "+FunFilters.NAMES[mode]+" giro="+display+" imagen="+effect.getWidth()+"x"+effect.getHeight());
                    Bitmap shown=latest;
                    latest=effect;latestMode=mode;latestRotation=display;filtered.setImageBitmap(effect);
                    // Unas 5 imágenes de ~3 MB por segundo: el cuadro anterior se libera de inmediato
                    // en vez de esperar al recolector. Nunca el que se está guardando como foto.
                    if(shown!=null&&shown!=effect&&!capturing)shown.recycle();
                }else if(effect!=latest){
                    effect.recycle(); // cuadro que ya no se va a mostrar
                }
            });
        }catch(RuntimeException e){Log.w("MisDibujosCamara","Vista previa",e);}
        finally{processing=false;try{source.addCallbackBuffer(nv21);}catch(RuntimeException ignored){}}
    }

    private static Bitmap decode(byte[] bytes,int width,int height){
        int step=Math.max(1,width/640),w=width/step,h=height/step;int[] pixels=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int sx=x*step,sy=y*step,Y=(bytes[sy*width+sx]&255)-16;
            int uv=width*height+(sy/2)*width+(sx&~1),V=(bytes[uv]&255)-128,U=(bytes[uv+1]&255)-128;
            int c=Math.max(0,Y)*1192;
            int r=Math.max(0,Math.min(255,(c+1634*V)>>10));
            int g=Math.max(0,Math.min(255,(c-833*V-400*U)>>10));
            int b=Math.max(0,Math.min(255,(c+2066*U)>>10));
            pixels[y*w+x]=0xff000000|(r<<16)|(g<<8)|b;
        }
        return Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
    }

    private void open(){
        Linterna.get(getContext()).set(false); // la cámara necesita el flash
        if(bg==null||paused||detached||!texture.isAvailable())return;
        final boolean requestedFront=front;final SurfaceTexture surface=texture.getSurfaceTexture();
        final int displayDegrees=getDisplay()==null?90:getDisplay().getRotation()*90;
        bg.post(()->{
            if(camera!=null||paused||detached||surface==null)return;
            Camera opened=null;
            try{
                Camera.CameraInfo info=new Camera.CameraInfo();int id=0;
                for(int i=0;i<Camera.getNumberOfCameras();i++){
                    Camera.getCameraInfo(i,info);
                    if(info.facing==(requestedFront?Camera.CameraInfo.CAMERA_FACING_FRONT:Camera.CameraInfo.CAMERA_FACING_BACK)){id=i;break;}
                }
                Camera.getCameraInfo(id,info);opened=Camera.open(id);
                Camera.Parameters parameters=opened.getParameters();Camera.Size best=null;double bestScore=Double.MAX_VALUE;
                for(Camera.Size size:parameters.getSupportedPreviewSizes()){
                    if(size.width>1280)continue;
                    double score=Math.abs(size.width/(double)size.height-16.0/9)*10+Math.abs(size.width-1280)/1280.0;
                    if(score<bestScore){bestScore=score;best=size;}
                }
                if(best!=null)parameters.setPreviewSize(best.width,best.height);
                List<String> focus=parameters.getSupportedFocusModes();
                if(focus!=null&&focus.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                parameters.setPreviewFormat(ImageFormat.NV21);opened.setParameters(parameters);
                Camera.Size size=opened.getParameters().getPreviewSize();previewWidth=size.width;previewHeight=size.height;
                sensorDegrees=info.orientation;sensorFront=info.facing==Camera.CameraInfo.CAMERA_FACING_FRONT;
                opened.setDisplayOrientation(0);opened.setPreviewTexture(surface);
                if(paused||detached){opened.release();return;}
                camera=opened;opened.setPreviewCallbackWithBuffer(this::frame);
                int bufferSize=previewWidth*previewHeight*3/2;
                opened.addCallbackBuffer(new byte[bufferSize]);opened.addCallbackBuffer(new byte[bufferSize]);
                opened.startPreview();
                Log.i("MisDibujosCamara","Captura unificada id="+id+" sensor="+sensorDegrees+" tamaño="+previewWidth+"x"+previewHeight);
            }catch(Exception e){if(opened!=null)opened.release();camera=null;Log.w("MisDibujosCamara","No se pudo abrir",e);
                post(()->{hint.setText("No se pudo abrir la cámara. Vuelve a intentarlo.");hint.setVisibility(VISIBLE);});}
        });
    }
    private void release(){
        if(bg==null)return;
        bg.post(()->{Camera current=camera;camera=null;if(current!=null){try{current.setPreviewCallbackWithBuffer(null);current.stopPreview();}catch(RuntimeException ignored){}current.release();}});
    }
    private void fitPreview() { /* El encuadre se calcula en el mismo camino para todos los modos. */ }
    private void takePhoto(){
        int rotation=getDisplay()==null?90:getDisplay().getRotation()*90;
        if(camera==null||capturing||latest==null||latestMode!=filterMode||latestRotation!=rotation)return;
        Bitmap photo=latest.copy(Bitmap.Config.ARGB_8888,false);
        capturing=true;epoch++;Sounds.play(Sounds.SHUTTER);release();callback.onPhoto(photo);
    }
}
