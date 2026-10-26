package mx.dibujos.kiosko;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.AtomicFile;
import android.util.Log;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.zip.*;

/** Documento propio: dibujo editable separado del papel, además del PNG para compartir. */
final class DrawingProjects {
    static final class Page {
        final Bitmap picture,paper;
        /** Película de la hoja (puede faltar en dibujos viejos) y la imagen desde la que empieza. */
        Recording recording;Bitmap base;
        Page(Bitmap picture,Bitmap paper){this.picture=picture;this.paper=paper;}
    }
    private final File folder;
    DrawingProjects(Context context){folder=new File(context.getFilesDir(),"drawing-projects");}
    private File file(Uri id) throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(id.toString().getBytes(StandardCharsets.UTF_8));
        StringBuilder name=new StringBuilder();for(byte b:digest)name.append(String.format(java.util.Locale.US,"%02x",b&255));
        File current=new File(folder,name+".dibujo");
        // Versiones anteriores usaban otra extensión: se migra en silencio para no perder proyectos.
        File legacy=new File(folder,name+".bernabe");
        if(!current.exists()&&legacy.exists())legacy.renameTo(current);
        return current;
    }
    boolean save(Uri id,Bitmap picture,Bitmap paper){return save(id,picture,paper,null,null);}
    boolean save(Uri id,Bitmap picture,Bitmap paper,byte[] film,Bitmap base){
        FileOutputStream stream=null;AtomicFile target=null;
        try{
            if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("Sin carpeta de proyectos");
            target=new AtomicFile(file(id));stream=target.startWrite();
            ZipOutputStream zip=new ZipOutputStream(stream);
            zip.putNextEntry(new ZipEntry("document.json"));
            zip.write("{\"version\":1,\"type\":\"editable-drawing\"}".getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            image(zip,"drawing.png",picture);
            if(paper!=null)image(zip,"paper.png",paper);
            if(film!=null){zip.putNextEntry(new ZipEntry("pelicula.bin"));zip.write(film);zip.closeEntry();}
            if(film!=null&&base!=null)image(zip,"inicio.png",base);
            zip.finish();zip.flush();target.finishWrite(stream);return true;
        }catch(Exception e){if(target!=null&&stream!=null)target.failWrite(stream);Log.w("MisDibujosProyectos","No se pudo guardar el proyecto",e);return false;}
    }
    private static void image(ZipOutputStream zip,String name,Bitmap bitmap) throws IOException{
        zip.putNextEntry(new ZipEntry(name));if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,zip))throw new IOException("PNG incompleto");zip.closeEntry();
    }
    Page load(Uri id){
        Bitmap picture=null,paper=null,base=null;Recording film=null;
        try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(new AtomicFile(file(id)).openRead()))){
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null){
                if(entry.getName().equals("drawing.png"))picture=BitmapFactory.decodeStream(zip);
                else if(entry.getName().equals("paper.png"))paper=BitmapFactory.decodeStream(zip);
                else if(entry.getName().equals("inicio.png"))base=BitmapFactory.decodeStream(zip);
                else if(entry.getName().equals("pelicula.bin")){
                    try{ByteArrayOutputStream all=new ByteArrayOutputStream();byte[] buf=new byte[16384];int n;
                        while((n=zip.read(buf))>0)all.write(buf,0,n);film=Recording.fromBytes(all.toByteArray());}
                    catch(Exception e){Log.w("MisDibujosProyectos","Película no legible: se conserva el dibujo",e);}
                }
                zip.closeEntry();
            }
            if(picture==null)return null;
            Page page=new Page(picture,paper);page.recording=film;page.base=film==null?null:base;return page;
        }catch(Exception ignored){return null;}
    }
}
