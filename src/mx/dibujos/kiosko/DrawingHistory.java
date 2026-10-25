package mx.dibujos.kiosko;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.concurrent.Executor;

/** Historial por hoja, compartido por el motor Bigme y Mandala. PNG evita retener 40 bitmaps grandes. */
final class DrawingHistory {
    private final ArrayDeque<Entry> entries=new ArrayDeque<>();
    private final Executor worker;
    DrawingHistory(Executor executor){worker=executor;}
    void push(Bitmap picture){
        if(picture==null)return;
        Entry entry=new Entry(picture);entries.addLast(entry);
        while(entries.size()>40)entries.removeFirst().close();
        worker.execute(entry::compress);
    }
    boolean canUndo(){return !entries.isEmpty();}
    Bitmap pop(){
        if(entries.isEmpty())return null;
        Entry entry=entries.removeLast();Bitmap result=entry.copy();entry.close();return result;
    }
    void clear(){while(!entries.isEmpty())entries.removeFirst().close();}
    private static final class Entry {
        private Bitmap bitmap;
        private byte[] png;
        private boolean closed;
        Entry(Bitmap picture){bitmap=picture;}
        synchronized void compress(){
            if(closed||bitmap==null)return;
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            if(bitmap.compress(Bitmap.CompressFormat.PNG,100,bytes)){
                png=bytes.toByteArray();bitmap.recycle();bitmap=null;
            }
        }
        synchronized Bitmap copy(){
            if(bitmap!=null)return bitmap.copy(Bitmap.Config.ARGB_8888,false);
            return png==null?null:BitmapFactory.decodeByteArray(png,0,png.length);
        }
        synchronized void close(){closed=true;if(bitmap!=null)bitmap.recycle();bitmap=null;png=null;}
    }
}
