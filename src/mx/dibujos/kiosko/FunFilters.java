package mx.dibujos.kiosko;

import android.graphics.*;
import android.media.FaceDetector;

/** Efectos locales de forma. Solo localiza ojos, sin identificar ni enviar caras. */
final class FunFilters {
    static final String[] NAMES={"Normal","Ojotes","Globo","Espejo","Invertido"};
    static final class Tracker {
        private float[] eyes;
        private long lastDetection,lastGood;
        void reset(){eyes=null;lastDetection=0;lastGood=0;}
        Bitmap apply(Bitmap image,int mode){
            long now=System.nanoTime()/1000000;
            if((mode==1||mode==2)&&now-lastDetection>450){
                lastDetection=now;float[] found=detect(image);
                if(found!=null){
                    if(eyes==null)eyes=found;
                    else for(int i=0;i<3;i++)eyes[i]=eyes[i]*.55f+found[i]*.45f;
                    lastGood=now;
                }else if(now-lastGood>1500)eyes=null;
            }
            return warp(image,mode,eyes);
        }
    }
    static Bitmap apply(Bitmap source,int mode){
        return warp(source,mode,mode==1||mode==2?detect(source):null);
    }
    private static float[] detect(Bitmap source){
        int fw=320,fh=Math.max(2,Math.round(source.getHeight()*fw/(float)source.getWidth()));
        Bitmap small=Bitmap.createScaledBitmap(source,fw,fh,false),rgb=small.copy(Bitmap.Config.RGB_565,false);
        try{
            FaceDetector.Face[] faces=new FaceDetector.Face[1];
            if(new FaceDetector(fw,fh,1).findFaces(rgb,faces)>0&&faces[0].confidence()>.4f){
                PointF p=new PointF();faces[0].getMidPoint(p);
                return new float[]{p.x/fw,p.y/fh,faces[0].eyesDistance()/fw};
            }
        }catch(RuntimeException ignored){}finally{rgb.recycle();if(small!=source)small.recycle();}
        return null;
    }
    private static Bitmap warp(Bitmap source,int mode,float[] eyes){
        if(mode==0)return source;
        if(mode==4)return invert(source);
        int w=source.getWidth(),h=source.getHeight();
        int[] in=new int[w*h],out=new int[w*h];source.getPixels(in,0,w,0,0,w,h);
        float mx=w*.5f,my=h*.39f,d=Math.min(w,h)*.22f;
        if(eyes!=null){mx=eyes[0]*w;my=eyes[1]*h;d=Math.max(8,eyes[2]*w);}
        int step=Math.max(6,w/64);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float sx=x,sy=y;
            if(mode==1){
                float lx=mx-d*.5f,rx=mx+d*.5f;
                float center=Math.abs(x-lx)<Math.abs(x-rx)?lx:rx;
                float dx=x-center,dy=y-my,r=d*.5f;
                float q=(dx*dx+dy*dy)/(r*r);
                if(q<1){float gain=.32f+.68f*q;sx=center+dx*gain;sy=my+dy*gain;}
            }else if(mode==2){
                float cy=my+d*.55f,dx=x-mx,dy=y-cy,r=d*1.7f;
                float q=(dx*dx+dy*dy)/(r*r);
                if(q<1){float gain=.38f+.62f*q;sx=mx+dx*gain;sy=cy+dy*gain;}
            }else if(mode==3){sx=x<w/2?x:w-1-x;}
            int ix=Math.max(0,Math.min(w-1,Math.round(sx))),iy=Math.max(0,Math.min(h-1,Math.round(sy)));
            int color=in[iy*w+ix];
            out[y*w+x]=color;
        }
        return Bitmap.createBitmap(out,w,h,Bitmap.Config.ARGB_8888);
    }
    /** Colores invertidos (negativo): blanco↔negro y cada color por su opuesto. */
    private static Bitmap invert(Bitmap source){
        int w=source.getWidth(),h=source.getHeight();
        int[] pixels=new int[w*h];source.getPixels(pixels,0,w,0,0,w,h);
        for(int i=0;i<pixels.length;i++)pixels[i]=0xff000000|(~pixels[i]&0x00ffffff);
        return Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
    }
}
